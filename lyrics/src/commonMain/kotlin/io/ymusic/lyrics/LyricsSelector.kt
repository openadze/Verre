package io.ymusic.lyrics

import kotlin.math.abs

/** Picks the best candidate: word-synced > line-synced > plain; a tie goes to the closest match with the track length. */
object LyricsSelector {
    private fun rank(t: SyncType): Int =
        when (t) {
            SyncType.RICH -> 2
            SyncType.LINE -> 1
            SyncType.NONE -> 0
        }

    fun select(
        candidates: List<LyricsDoc>,
        trackDurationMs: Long? = null,
    ): LyricsDoc? {
        val usable = candidates.filter { !it.isEmpty }
        if (usable.isEmpty()) return null
        return usable.maxWithOrNull(
            compareBy<LyricsDoc> { rank(it.syncType) }
                .thenBy { -durationGap(it, trackDurationMs) },
        )
    }

    /** How far the last line's end is from the track length; 0 when unknown (no preference). */
    private fun durationGap(
        doc: LyricsDoc,
        trackDurationMs: Long?,
    ): Long {
        if (trackDurationMs == null || doc.syncType == SyncType.NONE) return 0L
        return abs(doc.lines.maxOf { it.endMs } - trackDurationMs)
    }
}
