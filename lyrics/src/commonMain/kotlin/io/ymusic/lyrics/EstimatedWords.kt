package io.ymusic.lyrics

/**
 * Line-synced lyrics (most of LRCLIB) have one timestamp per line. Apple Music still lights them word by word, so
 * for a [SyncType.LINE] doc this spreads each line's time over its words, weighted by word length, and returns a doc
 * whose lines carry [LyricPart]s. The doc stays [SyncType.LINE] (it is an estimate: the selector must not rank it
 * above real word timing). Lines that already have parts, and unsynced docs, are returned untouched.
 */
fun LyricsDoc.withEstimatedWords(): LyricsDoc {
    if (syncType != SyncType.LINE) return this
    return copy(lines = lines.map { it.withEstimatedWords() })
}

private const val MAX_SUNG_MS = 6_000L
private const val LEAD_OUT_RATIO = 0.92

internal fun LyricLine.withEstimatedWords(): LyricLine {
    if (parts.isNotEmpty() || text.isBlank()) return this
    val tokens = text.split(' ').filter { it.isNotEmpty() }
    if (tokens.size < 2) return this
    // The singer rarely fills the whole gap to the next line: leave a breath, and cap very long gaps.
    val span = ((endMs - startMs) * LEAD_OUT_RATIO).toLong().coerceIn(1L, MAX_SUNG_MS)
    val weights = tokens.map { (it.length + 1).toDouble() }
    val total = weights.sum()
    var acc = 0.0
    val out = ArrayList<LyricPart>(tokens.size)
    for ((i, t) in tokens.withIndex()) {
        val from = startMs + (span * acc / total).toLong()
        acc += weights[i]
        val to = startMs + (span * acc / total).toLong()
        out.add(LyricPart(from, (to - from).coerceAtLeast(1L), if (i < tokens.size - 1) "$t " else t))
    }
    return copy(parts = out)
}
