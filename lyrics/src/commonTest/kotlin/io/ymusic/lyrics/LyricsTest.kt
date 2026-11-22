package io.ymusic.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsTest {
    private val ttmlWord =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xml:lang="en">
          <head><metadata>
            <iTunesMetadata><translations><translation xml:lang="pt">
              <text for="L2">Dance comigo</text></translation></translations></iTunesMetadata>
          </metadata></head>
          <body dur="1:00"><div>
            <p begin="0:01.000" end="0:03.000" ttm:agent="v1" itunes:key="L1">
              <span begin="0:01.000" end="0:01.400">Hel</span><span begin="0:01.400" end="0:01.800">lo</span>
              <span begin="0:01.900" end="0:03.000">world &amp; sky</span>
              <span ttm:role="x-bg"><span begin="0:02.000" end="0:02.500">(oh)</span></span>
            </p>
            <p begin="0:04.000" end="0:06.000" itunes:key="L2">
              <span begin="0:04.000" end="0:05.000">Dance </span><span begin="0:05.000" end="0:06.000">with me</span>
            </p>
          </div></body>
        </tt>
        """.trimIndent()

    @Test
    fun lrcLineSyncWithOffsetAndMultiStamp() {
        val doc =
            LrcParser.parse(
                """
                [ar:Someone]
                [offset:500]
                [00:10.00]First
                [00:20.50][00:40.5]Chorus
                [00:30.00]
                """.trimIndent(),
            )
        assertEquals(SyncType.LINE, doc.syncType)
        assertEquals(listOf("First", "Chorus", "Chorus"), doc.lines.map { it.text })
        assertEquals(9_500L, doc.lines[0].startMs)
        assertEquals(20_000L, doc.lines[1].startMs)
        assertEquals(20_000L, doc.lines[0].endMs)
        assertEquals(29_500L, doc.lines[1].endMs) // the blank [00:30.00] ends it
    }

    @Test
    fun enhancedLrcWords() {
        val doc = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>big <00:02.00>world<00:02.40>\n[00:05.00]<00:05.00>Bye")
        assertEquals(SyncType.RICH, doc.syncType)
        val line = doc.lines[0]
        assertEquals("Hello big world", line.text)
        assertEquals(3, line.parts.size)
        assertEquals("Hello ", line.parts[0].text)
        assertEquals(500L, line.parts[0].durationMs)
        assertEquals(400L, line.parts[2].durationMs) // closed by the empty end tag
        assertEquals("world", line.parts[2].text)
    }

    @Test
    fun lrcWithoutTimestampsFails() {
        val e = assertFailsWith<LyricsParseException> { LrcParser.parse("[ar:x]\nnothing here") }
        assertTrue(e.message!!.startsWith("E93001"))
    }

    @Test
    fun ttmlWordLevel() {
        val doc = TtmlParser.parse(ttmlWord)
        assertEquals(SyncType.RICH, doc.syncType)
        assertEquals("en", doc.language)
        val l1 = doc.lines[0]
        assertEquals("v1", l1.agent)
        assertEquals("Hello world & sky", l1.text)
        // Hel+lo merged into one word; "world & sky" is one span with inner spaces, kept as a single part.
        assertEquals(listOf("Hello ", "world & sky ", "(oh)"), l1.parts.map { it.text })
        assertEquals(1_000L, l1.parts[0].startMs)
        assertEquals(800L, l1.parts[0].durationMs)
        assertTrue(l1.parts[2].isBackground)
        assertEquals(false, l1.isBackground)
    }

    @Test
    fun ttmlTranslationFromHeadTable() {
        val doc = TtmlParser.parse(ttmlWord)
        assertNull(doc.lines[0].translation)
        assertEquals("Dance comigo", doc.lines[1].translation)
        assertEquals("Dance with me", doc.lines[1].text)
    }

    @Test
    fun ttmlLineLevelAndUntimed() {
        val line =
            TtmlParser.parse(
                """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
                <p begin="1.5s" end="3s">One</p><p begin="00:00:04.000" end="00:00:05.000">Two</p></div></body></tt>""",
            )
        assertEquals(SyncType.LINE, line.syncType)
        assertEquals(1_500L, line.lines[0].startMs)
        assertEquals(4_000L, line.lines[1].startMs)
        val none = TtmlParser.parse("""<tt><body><div><p>Just text</p></div></body></tt>""")
        assertEquals(SyncType.NONE, none.syncType)
    }

    @Test
    fun ttmlRelativeSpanTimes() {
        val doc =
            TtmlParser.parse(
                """<tt><body><p begin="10s" end="12s"><span begin="0s" end="1s">A</span> <span begin="1s" end="2s">b</span></p></body></tt>""",
            )
        assertEquals(10_000L, doc.lines[0].parts[0].startMs)
        assertEquals(11_000L, doc.lines[0].parts[1].startMs)
    }

    @Test
    fun ttmlCjkSyllablesStaySeparate() {
        val doc =
            TtmlParser.parse(
                """<tt><body><p begin="0s" end="2s"><span begin="0s" end="1s">你</span><span begin="1s" end="2s">好</span></p></body></tt>""",
            )
        assertEquals(listOf("你", "好"), doc.lines[0].parts.map { it.text })
    }

    @Test
    fun malformedXmlFailsWithCode() {
        val e = assertFailsWith<LyricsParseException> { TtmlParser.parse("<tt><body><p>oops</body></tt>") }
        assertTrue(e.message!!.startsWith("E93001"))
        assertFailsWith<LyricsParseException> { TtmlParser.parse("<html></html>") }
    }

    @Test
    fun plainAndDetect() {
        val doc = LyricsParsers.parse("Line one\n\nLine two\n")
        assertEquals(SyncType.NONE, doc.syncType)
        assertEquals(2, doc.lines.size)
        assertEquals(LyricsFormat.LRC, LyricsParsers.detect("[00:01.00]x"))
        assertEquals(LyricsFormat.TTML, LyricsParsers.detect("\uFEFF<?xml version=\"1.0\"?><tt></tt>"))
        assertEquals(LyricsFormat.PLAIN, LyricsParsers.detect("hello"))
        assertFailsWith<LyricsParseException> { PlainParser.parse("  \n ") }
    }

    @Test
    fun selectorPrefersWordThenLineThenPlain() {
        val rich = TtmlParser.parse(ttmlWord, "ttml")
        val line = LrcParser.parse("[00:01.00]a\n[00:02.00]b", "lrc")
        val plain = PlainParser.parse("a\nb", "plain")
        assertEquals("ttml", LyricsSelector.select(listOf(plain, line, rich))?.source)
        assertEquals("lrc", LyricsSelector.select(listOf(plain, line))?.source)
        assertEquals("plain", LyricsSelector.select(listOf(plain))?.source)
        assertNull(LyricsSelector.select(emptyList()))
    }

    @Test
    fun selectorTieGoesToDurationMatch() {
        val short = LrcParser.parse("[00:01.00]a\n[01:00.00]", "short")
        val long = LrcParser.parse("[00:01.00]a\n[03:30.00]", "long")
        assertEquals("long", LyricsSelector.select(listOf(short, long), 210_000L)?.source)
        assertEquals("short", LyricsSelector.select(listOf(short, long), 62_000L)?.source)
    }

    @Test
    fun timelineActiveLineGapAndProgress() {
        val doc = LrcParser.parse("[00:10.00]one\n[00:14.00]\n[00:30.00]two\n[00:33.00]\n")
        val tl = LyricsTimeline(doc)
        assertEquals(-1, tl.activeIndex(5_000L))
        assertTrue(tl.isInstrumentalGap(5_000L)) // 10 s before the first line
        assertEquals(0, tl.activeIndex(11_000L))
        assertEquals(false, tl.isInstrumentalGap(11_000L))
        assertTrue(tl.isInstrumentalGap(20_000L)) // 14 s .. 30 s
        assertEquals(0.5f, tl.gapProgress(22_000L)!!, 0.001f)
        assertEquals(1, tl.activeIndex(31_000L))
        assertEquals(0.5f, tl.lineProgress(doc.lines[0], 12_000L), 0.001f)
        val part = LyricPart(1_000L, 500L, "x")
        assertEquals(0f, tl.partProgress(part, 900L))
        assertEquals(0.5f, tl.partProgress(part, 1_250L), 0.001f)
        assertEquals(1f, tl.partProgress(part, 2_000L))
        assertEquals(-1, LyricsTimeline(PlainParser.parse("a")).activeIndex(10_000L))
    }
}
