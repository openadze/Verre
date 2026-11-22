package io.ymusic.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val WORDS =
    """
    version: "1.0"
    metadata:
      title: "Song"
      language: "en"
      offset_ms: 0
    lines:
      - text: "Hello big world"
        start_ms: 1000
        end_ms: 4000
        words:
          - text: "Hello"
            start_ms: 1000
            end_ms: 1500
          - text: "big"
            start_ms: 1600
            end_ms: 2000
          - { text: "world", start_ms: 2100, end_ms: 3500 }
      - text: "Only line timed"
        start_ms: 5000
    plain: |
      Hello big world
    """.trimIndent()

class LyricsfileTest {
    @Test
    fun readsWordTiming() {
        val doc = LyricsfileParser.parse(WORDS, "lrclib")
        assertEquals(SyncType.RICH, doc.syncType)
        assertEquals("en", doc.language)
        val first = doc.lines[0]
        assertEquals(1000, first.startMs)
        assertEquals(4000, first.endMs)
        assertEquals(listOf("Hello ", "big ", "world"), first.parts.map { it.text })
        assertEquals(2100, first.parts[2].startMs)
        assertEquals(1400, first.parts[2].durationMs)
    }

    @Test
    fun lineWithoutWordsIsInterpolated() {
        val doc = LyricsfileParser.parse(WORDS)
        val last = doc.lines[1]
        assertTrue(last.parts.isEmpty())
        assertEquals(5000, last.startMs)
        assertEquals(10_000, last.endMs)
    }

    @Test
    fun linesOnlyIsLineSync() {
        val doc = LyricsfileParser.parse("lines:\n  - text: a b\n    start_ms: 0\n  - text: c d\n    start_ms: 2000\n")
        assertEquals(SyncType.LINE, doc.syncType)
        assertEquals(2000, doc.lines[0].endMs)
    }

    @Test
    fun detectsAndRejects() {
        assertEquals(LyricsFormat.LYRICSFILE, LyricsParsers.detect(WORDS))
        assertFailsWith<LyricsParseException> { LyricsfileParser.parse("lines:\n  - text: x\n") }
    }

    @Test
    fun estimatedWordsSpreadTheLine() {
        val doc = LyricsDoc(listOf(LyricLine(0, 4000, "one two three")), SyncType.LINE).withEstimatedWords()
        val parts = doc.lines[0].parts
        assertEquals(3, parts.size)
        assertEquals("one two three", parts.joinToString("") { it.text })
        assertTrue(parts[0].startMs == 0L && parts[1].startMs >= parts[0].endMs - 1)
        assertEquals(SyncType.LINE, doc.syncType)
    }
}
