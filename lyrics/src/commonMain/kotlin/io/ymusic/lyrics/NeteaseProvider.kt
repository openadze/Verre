package io.ymusic.lyrics

import kotlin.math.abs

/**
 * NetEase Cloud Music (`music.163.com`): returns word-timed YRC when available, falling back to LRC.
 */
class NeteaseProvider(
    private val http: LyricsHttp,
    private val log: LyricsLogger? = null,
) : LyricsProvider {
    override val id: String = ID

    override suspend fun fetch(query: LyricsQuery): LyricsResult {
        if (query.title.isBlank() || query.artist.isBlank()) return LyricsResult.NotFound
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer" to "https://music.163.com/",
            "Accept" to "application/json",
        )
        val q = "${query.title} ${query.primaryArtist}".trim()
        val searchUrl = "https://music.163.com/api/search/get?type=1&limit=5&s=${UrlEncode.component(q)}"
        val searchResp = http.get(searchUrl, headers)
        if (!searchResp.isSuccessful) {
            throw LyricsProviderException("NetEase /search answered HTTP ${searchResp.code}")
        }
        val root = MiniJson.parse(searchResp.body).asMap()
            ?: throw LyricsProviderException("E93003", "NetEase /search is not an object")
        val songs = (root["result"].asMap()?.get("songs") as? List<*>) ?: return LyricsResult.NotFound
        if (songs.isEmpty()) return LyricsResult.NotFound

        val wantedMs = query.durationMs
        val bestSongId = pickSongId(songs, wantedMs) ?: return LyricsResult.NotFound

        val lyricUrl = "https://music.163.com/api/song/lyric?id=$bestSongId&lv=1&yv=1"
        val lyricResp = http.get(lyricUrl, headers)
        if (!lyricResp.isSuccessful) {
            throw LyricsProviderException("NetEase /lyric answered HTTP ${lyricResp.code}")
        }
        val lyricRoot = MiniJson.parse(lyricResp.body).asMap()
            ?: throw LyricsProviderException("E93003", "NetEase /lyric is not an object")

        val tlyricText = (lyricRoot["tlyric"].asMap()?.get("lyric") as? String)?.takeIf { it.isNotBlank() }
        val romalrcText = (lyricRoot["romalrc"].asMap()?.get("lyric") as? String)?.takeIf { it.isNotBlank() }

        val yrcText = (lyricRoot["yrc"].asMap()?.get("lyric") as? String)?.takeIf { it.isNotBlank() }
        if (yrcText != null && YrcParser.looksLikeYrc(yrcText)) {
            try {
                val doc = attachSubtitles(LyricsParsers.parse(yrcText, ID, LyricsFormat.YRC), tlyricText, romalrcText)
                return LyricsResult.Found(doc, yrcText, LyricsFormat.YRC)
            } catch (e: LyricsParseException) {
                log?.warn("NetEase: YRC does not parse, falling back to LRC", e)
            }
        }

        val lrcText = (lyricRoot["lrc"].asMap()?.get("lyric") as? String)?.takeIf { it.isNotBlank() }
            ?: return LyricsResult.NotFound
        val format = if (LrcParser.looksLikeLrc(lrcText)) LyricsFormat.LRC else LyricsFormat.PLAIN
        val doc = attachSubtitles(LyricsParsers.parse(lrcText, ID, format), tlyricText, romalrcText)
        return LyricsResult.Found(doc, lrcText, format)
    }

    private fun attachSubtitles(doc: LyricsDoc, tlyric: String?, romalrc: String?): LyricsDoc {
        if (doc.syncType == SyncType.NONE || (tlyric == null && romalrc == null)) return doc
        val translations = parseOptionalLrc(tlyric, "tlyric")
        val romanizations = parseOptionalLrc(romalrc, "romalrc")
        if (translations.isEmpty() && romanizations.isEmpty()) return doc
        val updated = doc.lines.map { line ->
            val trans = findClosestLine(translations, line.startMs)
            val roma = findClosestLine(romanizations, line.startMs)
            if (trans == null && roma == null) line else line.copy(translation = trans, romanization = roma)
        }
        return doc.copy(lines = updated)
    }

    private fun parseOptionalLrc(raw: String?, tag: String): List<LyricLine> {
        if (raw == null || !LrcParser.looksLikeLrc(raw)) return emptyList()
        return try {
            LrcParser.parse(raw, ID).lines.filter { it.text.isNotBlank() && it.text.trim() != "//" }
        } catch (e: LyricsParseException) {
            log?.warn("NetEase: $tag does not parse", e)
            emptyList()
        }
    }

    private fun findClosestLine(lines: List<LyricLine>, targetMs: Long): String? {
        if (lines.isEmpty()) return null
        var best: LyricLine? = null
        var bestDiff = 500L
        for (candidate in lines) {
            val diff = abs(candidate.startMs - targetMs)
            if (diff <= bestDiff) {
                bestDiff = diff
                best = candidate
            }
        }
        return best?.text?.takeIf { it.isNotBlank() }
    }

    private fun pickSongId(songs: List<*>, wantedMs: Long?): Long? {
        var fallbackId: Long? = null
        var bestId: Long? = null
        var bestDiff = Long.MAX_VALUE
        for (item in songs) {
            val map = item.asMap() ?: continue
            val songId = (map["id"] as? Double)?.toLong() ?: continue
            if (fallbackId == null) fallbackId = songId
            val durMs = (map["duration"] as? Double)?.toLong() ?: continue
            if (wantedMs != null) {
                val diff = abs(durMs - wantedMs)
                if (diff <= DURATION_TOLERANCE_MS && diff < bestDiff) {
                    bestDiff = diff
                    bestId = songId
                }
            } else {
                return songId
            }
        }
        return bestId ?: if (wantedMs == null) fallbackId else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

    companion object {
        const val ID = "netease"
        private const val DURATION_TOLERANCE_MS = 6_000L
    }
}
