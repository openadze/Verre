package io.ymusic.lyrics

/**
 * LyricsPlus (`lyricsplus.prjktla.workers.dev`): aggregates Apple Music / Musixmatch word-timed lyrics.
 */
class LyricsPlusProvider(
    private val http: LyricsHttp,
    private val baseUrl: String = "https://lyricsplus.prjktla.workers.dev",
) : LyricsProvider {
    override val id: String = ID

    override suspend fun fetch(query: LyricsQuery): LyricsResult {
        if (query.title.isBlank() || query.artist.isBlank()) return LyricsResult.NotFound
        val sb = StringBuilder("$baseUrl/v2/lyrics/get?title=${UrlEncode.component(query.title)}&artist=${UrlEncode.component(query.primaryArtist)}")
        query.album?.takeIf { it.isNotBlank() }?.let { sb.append("&album=").append(UrlEncode.component(it)) }
        query.durationMs?.let { sb.append("&duration=").append(it / 1000) }
        val resp = http.get(sb.toString(), mapOf("Accept" to "application/json"))
        when {
            resp.code == 404 -> return LyricsResult.NotFound
            !resp.isSuccessful -> throw LyricsProviderException("LyricsPlus answered HTTP ${resp.code}")
        }
        val doc = parseJson(resp.body, ID)
        if (doc.isEmpty) return LyricsResult.NotFound
        return LyricsResult.Found(doc, resp.body, LyricsFormat.LYRICSPLUS_JSON)
    }

    companion object {
        const val ID = "lyricsplus"

        @Suppress("UNCHECKED_CAST")
        fun parseJson(raw: String, source: String = ID): LyricsDoc {
            val root = try {
                MiniJson.parse(raw) as? Map<String, Any?>
            } catch (e: LyricsProviderException) {
                throw LyricsParseException("LyricsPlus JSON invalid", e)
            } ?: throw LyricsParseException("LyricsPlus response is not an object")

            val items = root["lyrics"] as? List<*> ?: throw LyricsParseException("LyricsPlus missing 'lyrics' array")
            val lines = ArrayList<LyricLine>(items.size)
            var hasRich = false
            for (item in items) {
                val m = item as? Map<String, Any?> ?: continue
                val startMs = (m["time"] as? Double)?.toLong() ?: continue
                val durMs = (m["duration"] as? Double)?.toLong()?.coerceAtLeast(0L) ?: 0L
                val lineText = (m["text"] as? String).orEmpty()
                val syllabus = m["syllabus"] as? List<*>
                val parts = if (syllabus != null) {
                    val list = ArrayList<LyricPart>(syllabus.size)
                    for (syl in syllabus) {
                        val sm = syl as? Map<String, Any?> ?: continue
                        val sTime = (sm["time"] as? Double)?.toLong() ?: continue
                        val sDur = (sm["duration"] as? Double)?.toLong()?.coerceAtLeast(0L) ?: 0L
                        val sText = sm["text"] as? String ?: continue
                        val isBg = sm["isBackground"] == true
                        list.add(LyricPart(startMs = sTime, durationMs = sDur, text = sText, isBackground = isBg))
                    }
                    list
                } else {
                    emptyList()
                }
                if (parts.isNotEmpty()) hasRich = true
                val resolvedText = lineText.ifBlank { parts.joinToString("") { it.text }.trim() }
                if (resolvedText.isEmpty()) continue
                val isBg = m["isBackground"] == true
                lines.add(
                    LyricLine(
                        startMs = startMs,
                        endMs = (startMs + durMs).coerceAtLeast(parts.lastOrNull()?.endMs ?: startMs),
                        text = resolvedText,
                        parts = parts,
                        isBackground = isBg,
                    ),
                )
            }
            if (lines.isEmpty()) throw LyricsParseException("LyricsPlus returned no lyric lines")
            val syncType = when {
                hasRich -> SyncType.RICH
                lines.any { it.startMs > 0L } -> SyncType.LINE
                else -> SyncType.NONE
            }
            return LyricsDoc(lines = lines, syncType = syncType, source = source)
        }
    }
}
