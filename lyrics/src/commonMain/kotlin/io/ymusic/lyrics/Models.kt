package io.ymusic.lyrics

/** How finely a [LyricsDoc] is timed. Preference order: [RICH] > [LINE] > [NONE]. */
enum class SyncType {
    /** Word by word (TTML spans, enhanced LRC `<mm:ss.xx>`). */
    RICH,

    /** One timestamp per line. */
    LINE,

    /** Plain text, no timing. */
    NONE,
}

/** One word (or CJK syllable). [text] carries a trailing space when a space follows the word in the line. */
data class LyricPart(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
    val isBackground: Boolean = false,
) {
    val endMs: Long get() = startMs + durationMs
}

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** Empty unless the line is word-timed. Background words are flagged on the part. */
    val parts: List<LyricPart> = emptyList(),
    val agent: String? = null,
    /** True when the whole line is a background vocal. */
    val isBackground: Boolean = false,
    val translation: String? = null,
    val romanization: String? = null,
)

data class LyricsDoc(
    val lines: List<LyricLine>,
    val syncType: SyncType,
    val source: String = "",
    val language: String? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** E93001: lyrics text could not be parsed. The caller of a provider must log it (never swallow it). */
class LyricsParseException(
    message: String,
    cause: Throwable? = null,
) : Exception("E93001 $message", cause)
