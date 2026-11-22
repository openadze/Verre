package io.ymusic.lyrics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One cached answer. A hit keeps the provider's raw payload (parsed again on read, so a parser fix reaches old
 * entries); a miss ([found] = false, empty [raw]) is the negative cache. [expiresAtMs] is epoch millis.
 */
data class LyricsCacheEntry(
    val found: Boolean,
    val expiresAtMs: Long,
    val source: String,
    val format: LyricsFormat?,
    val raw: String,
)

interface LyricsCache {
    suspend fun get(key: String): LyricsCacheEntry?

    suspend fun put(
        key: String,
        entry: LyricsCacheEntry,
    )

    suspend fun clear()
}

/** Text codec shared by the disk caches: one header line, then the raw payload. Throws [LyricsCacheException]. */
object LyricsCacheCodec {
    private const val MAGIC = "VERRE-LYRICS-1"

    fun encode(e: LyricsCacheEntry): String =
        "$MAGIC|${if (e.found) "hit" else "miss"}|${e.expiresAtMs}|${e.format?.name ?: "-"}|${e.source.replace('|', '_').replace('\n', ' ')}\n${e.raw}"

    fun decode(text: String): LyricsCacheEntry {
        val nl = text.indexOf('\n')
        if (nl < 0) throw LyricsCacheException("no header line")
        val h = text.substring(0, nl).split('|', limit = 5)
        if (h.size != 5 || h[0] != MAGIC) throw LyricsCacheException("unknown header")
        val found =
            when (h[1]) {
                "hit" -> true
                "miss" -> false
                else -> throw LyricsCacheException("bad kind '${h[1]}'")
            }
        val expires = h[2].toLongOrNull() ?: throw LyricsCacheException("bad expiry '${h[2]}'")
        val format =
            if (h[3] == "-") {
                null
            } else {
                LyricsFormat.entries.firstOrNull { it.name == h[3] } ?: throw LyricsCacheException("bad format '${h[3]}'")
            }
        if (found && format == null) throw LyricsCacheException("hit without format")
        return LyricsCacheEntry(found, expires, h[4], format, text.substring(nl + 1))
    }
}

/** Bounded in-memory cache (LRU by insertion/read order). Lost on process death; fine in front of the disk cache. */
class InMemoryLyricsCache(
    private val maxEntries: Int = 64,
) : LyricsCache {
    private val mutex = Mutex()
    private val map = LinkedHashMap<String, LyricsCacheEntry>()

    override suspend fun get(key: String): LyricsCacheEntry? =
        mutex.withLock {
            val e = map.remove(key) ?: return@withLock null
            map[key] = e
            e
        }

    override suspend fun put(
        key: String,
        entry: LyricsCacheEntry,
    ) = mutex.withLock {
        map.remove(key)
        map[key] = entry
        while (map.size > maxEntries) map.remove(map.keys.first())
        Unit
    }

    override suspend fun clear() = mutex.withLock { map.clear() }
}
