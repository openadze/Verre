package io.ymusic.lyrics

/** Where the lyrics module logs problems it recovers from. The app adapts its own logger to this. */
fun interface LyricsLogger {
    fun warn(
        message: String,
        error: Throwable?,
    )
}

/** A minimal HTTP seam so providers stay pure and testable. [KmpLyricsHttp] is the real one. */
class LyricsHttpResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

fun interface LyricsHttp {
    /** Throws [LyricsProviderException] (E93002) when the request itself fails; HTTP error codes are returned. */
    suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): LyricsHttpResponse
}

sealed interface LyricsResult {
    /** [raw] and [format] are what the cache stores: the payload is parsed again on a hit. */
    data class Found(
        val doc: LyricsDoc,
        val raw: String,
        val format: LyricsFormat,
    ) : LyricsResult

    data object NotFound : LyricsResult
}

interface LyricsProvider {
    /** Stable id used in the `lyrics_providers` setting. */
    val id: String

    /** Throws [LyricsProviderException] or [LyricsParseException]; the caller logs them (never swallowed). */
    suspend fun fetch(query: LyricsQuery): LyricsResult
}

/** E93002: a provider request failed (network, HTTP status). E93003: a provider response could not be read. */
class LyricsProviderException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception("$code $message", cause) {
    constructor(message: String, cause: Throwable? = null) : this("E93002", message, cause)
}

/** E93004: a cache file is unreadable. The file cache logs it and drops the file. */
class LyricsCacheException(
    message: String,
    cause: Throwable? = null,
) : Exception("E93004 $message", cause)

internal object UrlEncode {
    private const val HEX = "0123456789ABCDEF"

    fun component(s: String): String {
        val sb = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
        return sb.toString()
    }
}
