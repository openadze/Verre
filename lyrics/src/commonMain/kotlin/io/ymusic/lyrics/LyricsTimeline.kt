package io.ymusic.lyrics

/** Position queries over a [LyricsDoc]: active line, instrumental gaps, word / line progress. All times in ms. */
class LyricsTimeline(
    val doc: LyricsDoc,
) {
    private val lines = doc.lines
    private val timed = doc.syncType != SyncType.NONE

    /** Index of the last line that has started, or -1 (before the first line, or an unsynced doc). */
    fun activeIndex(positionMs: Long): Int {
        if (!timed || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    /** True inside a long enough silence: before the first line, or between a line's end and the next start. */
    fun isInstrumentalGap(
        positionMs: Long,
        minGapMs: Long = DEFAULT_MIN_GAP_MS,
    ): Boolean = gapProgress(positionMs, minGapMs) != null

    /** 0..1 progress through the current instrumental gap (for the three dots), or null outside a gap. */
    fun gapProgress(
        positionMs: Long,
        minGapMs: Long = DEFAULT_MIN_GAP_MS,
    ): Float? {
        if (!timed || lines.isEmpty()) return null
        val idx = activeIndex(positionMs)
        val from: Long
        val to: Long
        if (idx < 0) {
            from = 0L
            to = lines.first().startMs
        } else {
            if (idx + 1 >= lines.size) return null
            from = lines[idx].endMs
            to = lines[idx + 1].startMs
            if (positionMs < from) return null
        }
        if (to - from < minGapMs || positionMs >= to) return null
        return ((positionMs - from).toFloat() / (to - from).toFloat()).coerceIn(0f, 1f)
    }

    fun partProgress(
        part: LyricPart,
        positionMs: Long,
    ): Float =
        when {
            positionMs <= part.startMs -> 0f
            part.durationMs <= 0L || positionMs >= part.endMs -> 1f
            else -> (positionMs - part.startMs).toFloat() / part.durationMs.toFloat()
        }

    fun lineProgress(
        line: LyricLine,
        positionMs: Long,
    ): Float =
        when {
            positionMs <= line.startMs -> 0f
            line.endMs <= line.startMs || positionMs >= line.endMs -> 1f
            else -> (positionMs - line.startMs).toFloat() / (line.endMs - line.startMs).toFloat()
        }

    companion object {
        const val DEFAULT_MIN_GAP_MS = 5_000L
    }
}
