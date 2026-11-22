package io.ymusic.lyrics

import kotlinx.coroutines.withTimeoutOrNull

/** What the user chose in Settings (see [LyricsSettings]). [providerIds] is the order providers are tried in. */
data class LyricsOptions(
    val enabled: Boolean = true,
    val preferWordSync: Boolean = true,
    val providerIds: List<String> = listOf(LyricsPlusProvider.ID, LrclibProvider.ID, NeteaseProvider.ID),
)

/**
 * Asks the providers in order, picks the best result (word > line > plain; a tie goes to the closest length) and
 * caches it. Stops early on a word-synced hit (or, with [LyricsOptions.preferWordSync] off, on any synced hit).
 *
 * Failures: a provider that throws [LyricsProviderException] / [LyricsParseException], or times out, is logged and
 * skipped. Nothing found AFTER a failure is not cached as a miss (it may be transient); nothing found with every
 * provider answering is cached as a miss for [negativeTtlMs].
 */
class LyricsRepository(
    providers: List<LyricsProvider>,
    private val cache: LyricsCache,
    private val log: LyricsLogger? = null,
    private val clock: () -> Long,
    private val timeoutMs: Long = 8_000,
    private val richTtlMs: Long = 30 * DAY_MS,
    private val otherTtlMs: Long = 3 * DAY_MS,
    private val negativeTtlMs: Long = 6 * HOUR_MS,
) {
    private val byId = providers.associateBy { it.id }

    /** The best lyrics for [query], or null (disabled, none found, or all providers failed). */
    suspend fun load(
        query: LyricsQuery,
        options: LyricsOptions = LyricsOptions(),
    ): LyricsDoc? {
        if (!options.enabled) return null
        val key = query.cacheKey
        val now = clock()

        cache.get(key)?.takeIf { it.expiresAtMs > now }?.let { hit ->
            if (!hit.found) return null
            try {
                return LyricsParsers.parse(hit.raw, hit.source, requireNotNull(hit.format))
            } catch (e: LyricsParseException) {
                log?.warn("lyrics cache: stored payload for $key no longer parses; refetching", e)
            }
        }

        val found = ArrayList<LyricsResult.Found>()
        var failed = false
        for (id in options.providerIds) {
            val provider = byId[id] ?: continue
            val result =
                try {
                    withTimeoutOrNull(timeoutMs) { provider.fetch(query) }
                        ?: run {
                            failed = true
                            log?.warn("lyrics provider '$id' timed out after $timeoutMs ms", null)
                            null
                        }
                } catch (e: LyricsProviderException) {
                    failed = true
                    log?.warn("lyrics provider '$id' failed", e)
                    null
                } catch (e: LyricsParseException) {
                    failed = true
                    log?.warn("lyrics provider '$id' returned lyrics that do not parse", e)
                    null
                }
            if (result is LyricsResult.Found && !result.doc.isEmpty) {
                found.add(result)
                val type = result.doc.syncType
                if (type == SyncType.RICH || (!options.preferWordSync && type == SyncType.LINE)) break
            }
        }

        val bestDoc = LyricsSelector.select(found.map { it.doc }, query.durationMs)
        val best = found.firstOrNull { it.doc === bestDoc }
        if (best == null) {
            if (!failed) cache.put(key, LyricsCacheEntry(false, now + negativeTtlMs, "", null, ""))
            return null
        }
        val ttl = if (best.doc.syncType == SyncType.RICH) richTtlMs else otherTtlMs
        cache.put(key, LyricsCacheEntry(true, now + ttl, best.doc.source, best.format, best.raw))
        return best.doc
    }

    companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
    }
}
