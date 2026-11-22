package io.ymusic.lyrics

import kotlin.math.abs

/**
 * LRCLIB (lrclib.net): free, open lyrics: `lyricsfile` (word timing), line-synced and plain. `/api/get` first (exact
 * match with duration), then `/api/search` when it is a 404. [userAgent] must identify the app (LRCLIB asks for it).
 */
class LrclibProvider(
    private val http: LyricsHttp,
    private val userAgent: String,
    private val log: LyricsLogger? = null,
    private val baseUrl: String = "https://lrclib.net/api",
) : LyricsProvider {
    override val id: String = ID

    override suspend fun fetch(query: LyricsQuery): LyricsResult {
        if (query.title.isBlank() || query.artist.isBlank()) return LyricsResult.NotFound
        val headers = mapOf("User-Agent" to userAgent, "Accept" to "application/json")

        val get = http.get(getUrl(query), headers)
        when {
            get.isSuccessful -> return fromItem(MiniJson.parse(get.body)) ?: LyricsResult.NotFound
            get.code != 404 -> throw LyricsProviderException("LRCLIB /get answered HTTP ${get.code}")
        }

        val search = http.get(searchUrl(query), headers)
        if (!search.isSuccessful) throw LyricsProviderException("LRCLIB /search answered HTTP ${search.code}")
        val items = MiniJson.parse(search.body) as? List<*> ?: throw LyricsProviderException("E93003", "LRCLIB /search is not an array")

        val wanted = query.durationMs
        val found = ArrayList<LyricsResult.Found>()
        for (item in items) {
            val seconds = (item.asMap()?.get("duration") as? Double)
            if (wanted != null && seconds != null && abs(seconds * 1000 - wanted) > DURATION_TOLERANCE_MS) continue
            try {
                (fromItem(item) as? LyricsResult.Found)?.let(found::add)
            } catch (e: LyricsParseException) {
                // One broken candidate must not lose the others — but it is logged, never swallowed.
                log?.warn("LRCLIB: skipping a candidate that does not parse", e)
            }
        }
        val best = LyricsSelector.select(found.map { it.doc }, wanted) ?: return LyricsResult.NotFound
        return found.first { it.doc === best }
    }

    private fun getUrl(q: LyricsQuery): String {
        val sb = StringBuilder("$baseUrl/get?track_name=${UrlEncode.component(q.title)}&artist_name=${UrlEncode.component(q.artist)}")
        q.album?.let { sb.append("&album_name=").append(UrlEncode.component(it)) }
        q.durationMs?.let { sb.append("&duration=").append(it / 1000) }
        return sb.toString()
    }

    private fun searchUrl(q: LyricsQuery): String =
        "$baseUrl/search?track_name=${UrlEncode.component(q.title)}&artist_name=${UrlEncode.component(q.primaryArtist)}"

    /**
     * Preference: `lyricsfile` (YAML, can hold word timing) > `syncedLyrics` (line LRC) > `plainLyrics`. A lyricsfile
     * that does not parse is logged and the legacy fields are used instead, so one bad upload never hides the lyrics.
     * An instrumental track or an empty item is "not found".
     */
    private fun fromItem(item: Any?): LyricsResult? {
        val m = item.asMap() ?: throw LyricsProviderException("E93003", "LRCLIB item is not an object")
        if (m["instrumental"] == true) return LyricsResult.NotFound
        val file = (m["lyricsfile"] as? String)?.takeIf { it.isNotBlank() }
        if (file != null) {
            try {
                return LyricsResult.Found(LyricsParsers.parse(file, ID, LyricsFormat.LYRICSFILE), file, LyricsFormat.LYRICSFILE)
            } catch (e: LyricsParseException) {
                log?.warn("LRCLIB: lyricsfile does not parse, using the legacy fields", e)
            }
        }
        val synced = (m["syncedLyrics"] as? String)?.takeIf { it.isNotBlank() }
        if (synced != null) {
            return LyricsResult.Found(LyricsParsers.parse(synced, ID, LyricsFormat.LRC), synced, LyricsFormat.LRC)
        }
        val plain = (m["plainLyrics"] as? String)?.takeIf { it.isNotBlank() } ?: return LyricsResult.NotFound
        return LyricsResult.Found(LyricsParsers.parse(plain, ID, LyricsFormat.PLAIN), plain, LyricsFormat.PLAIN)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

    companion object {
        const val ID = "lrclib"
        private const val DURATION_TOLERANCE_MS = 5_000L
    }
}
