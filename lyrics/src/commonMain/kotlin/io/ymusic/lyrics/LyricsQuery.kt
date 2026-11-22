package io.ymusic.lyrics

/**
 * What a provider is asked for: the track as the player knows it. Build one with [forTrack], which cleans the
 * YouTube-isms ("(Official Video)", "- Topic", "Artist - Title") so providers see a plain title and artist.
 */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val videoId: String? = null,
) {
    /** First credited artist, for fuzzy searches ("A, B & C" -> "A"). */
    val primaryArtist: String get() = LyricsQueryCleaner.primaryArtist(artist)

    /** Stable cache key: the video id when known, else the normalized title / artist / whole seconds. */
    val cacheKey: String
        get() =
            videoId?.takeIf { it.isNotBlank() }?.let { "yt:$it" }
                ?: "q:${LyricsQueryCleaner.normalize(title)}|${LyricsQueryCleaner.normalize(artist)}|${durationMs?.div(1000) ?: 0}"

    companion object {
        fun forTrack(
            title: String?,
            artist: String?,
            album: String? = null,
            durationMs: Long? = null,
            videoId: String? = null,
        ): LyricsQuery {
            val cleanArtist = LyricsQueryCleaner.artist(artist.orEmpty())
            return LyricsQuery(
                title = LyricsQueryCleaner.title(title.orEmpty(), cleanArtist),
                artist = cleanArtist,
                album = album?.trim()?.takeIf { it.isNotEmpty() },
                durationMs = durationMs?.takeIf { it > 0 },
                videoId = videoId,
            )
        }
    }
}

object LyricsQueryCleaner {
    private val noiseBrackets =
        Regex(
            """\s*[(\[][^)\]]*\b(official|lyrics?|lyric video|audio|video|visuali[sz]er|hd|hq|4k|mv|clip)\b[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE,
        )
    private val noiseTail =
        Regex("""\s*[-|–—]\s*(official|lyrics?|audio|video|visuali[sz]er)\b.*$""", RegexOption.IGNORE_CASE)
    private val artistSuffix =
        Regex("""\s*(-\s*topic|vevo|\(official\)|official)\s*$""", RegexOption.IGNORE_CASE)
    private val artistSplit = Regex("""\s*(,|&|;|\bfeat\.?|\bft\.?|\bx\b|\band\b)\s*""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")

    fun title(
        raw: String,
        artist: String = "",
    ): String {
        var t = raw.trim()
        if (artist.isNotEmpty()) {
            for (sep in listOf(" - ", " – ", " — ")) {
                if (t.startsWith(artist + sep, ignoreCase = true)) {
                    t = t.substring(artist.length + sep.length)
                    break
                }
            }
        }
        t = noiseBrackets.replace(t, "")
        t = noiseTail.replace(t, "")
        return spaces.replace(t, " ").trim().ifEmpty { raw.trim() }
    }

    fun artist(raw: String): String = spaces.replace(artistSuffix.replace(raw.trim(), ""), " ").trim()

    fun primaryArtist(artist: String): String = artistSplit.split(artist).firstOrNull()?.trim()?.ifEmpty { null } ?: artist

    fun normalize(s: String): String = spaces.replace(s.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }, " ").trim()
}
