package io.ymusic.lyrics

enum class LyricsFormat { TTML, LRC, PLAIN }

/** One entry point: detects the format of a lyrics payload and parses it. Throws [LyricsParseException] (E93001). */
object LyricsParsers {
    fun detect(text: String): LyricsFormat {
        val t = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return when {
            t.startsWith("<") && t.contains("<tt") -> LyricsFormat.TTML
            LrcParser.looksLikeLrc(t) -> LyricsFormat.LRC
            else -> LyricsFormat.PLAIN
        }
    }

    fun parse(
        text: String,
        source: String = "",
        format: LyricsFormat = detect(text),
    ): LyricsDoc =
        when (format) {
            LyricsFormat.TTML -> TtmlParser.parse(text, source)
            LyricsFormat.LRC -> LrcParser.parse(text, source)
            LyricsFormat.PLAIN -> PlainParser.parse(text, source)
        }
}
