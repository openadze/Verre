package io.ymusic.lyrics

/**
 * Parses NetEase YRC word-timed lyrics:
 * `[lineStartMs,lineDurMs](wordStartMs,wordDurMs,0)word...`
 * Lines starting with `{` (NetEase JSON metadata lines) are skipped.
 */
object YrcParser {
    private val lineHeaderRegex = Regex("""^\[(\d+),(\d+)\]""")
    private val wordRegex = Regex("""\((\d+),(\d+),\d+\)([^()\[\]]*)""")

    fun looksLikeYrc(text: String): Boolean =
        text.lineSequence().any { line ->
            val t = line.trim()
            lineHeaderRegex.containsMatchIn(t) && wordRegex.containsMatchIn(t)
        }

    fun parse(text: String, source: String = ""): LyricsDoc {
        val lines = ArrayList<LyricLine>()
        for (rawLine in text.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("{")) continue
            val header = lineHeaderRegex.find(trimmed) ?: continue
            val lineStart = header.groupValues[1].toLongOrNull() ?: continue
            val lineDur = header.groupValues[2].toLongOrNull() ?: 0L
            val body = trimmed.substring(header.range.last + 1)

            val parts = ArrayList<LyricPart>()
            val lineText = StringBuilder()
            for (m in wordRegex.findAll(body)) {
                val wStart = m.groupValues[1].toLongOrNull() ?: continue
                val wDur = m.groupValues[2].toLongOrNull() ?: 0L
                val wText = m.groupValues[3]
                if (wText.isEmpty()) continue
                lineText.append(wText)
                parts.add(LyricPart(startMs = wStart, durationMs = wDur.coerceAtLeast(0L), text = wText))
            }
            val fullText = lineText.toString().trim()
            if (fullText.isEmpty()) continue
            val lineEnd = (lineStart + lineDur).coerceAtLeast(parts.lastOrNull()?.endMs ?: lineStart)
            lines.add(LyricLine(startMs = lineStart, endMs = lineEnd, text = fullText, parts = parts))
        }
        if (lines.isEmpty()) throw LyricsParseException("YRC contained no lyric lines")
        val syncType = if (lines.any { it.parts.isNotEmpty() }) SyncType.RICH else SyncType.LINE
        return LyricsDoc(lines = lines, syncType = syncType, source = source)
    }
}
