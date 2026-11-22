package io.ymusic.lyrics

/** Unsynced lyrics: one [LyricLine] per non-blank line, all at time 0. */
object PlainParser {
    fun parse(
        src: String,
        source: String = "",
    ): LyricsDoc {
        val lines =
            src
                .lineSequence()
                .map { it.trim('\uFEFF', ' ', '\t', '\r') }
                .filter { it.isNotEmpty() }
                .map { LyricLine(0L, 0L, it) }
                .toList()
        if (lines.isEmpty()) throw LyricsParseException("Plain: no text")
        return LyricsDoc(lines, SyncType.NONE, source)
    }
}
