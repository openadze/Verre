package io.ymusic.lyrics

/** LRC and enhanced LRC (`<mm:ss.xx>` before each word). Blank timed lines end the previous line. */
object LrcParser {
    private const val LAST_LINE_MS = 5_000L
    private const val LAST_WORD_MAX_MS = 3_000L

    private val lineTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val metaTag = Regex("""^\[([A-Za-z#]+):(.*)\]$""")

    fun looksLikeLrc(text: String): Boolean = lineTag.containsMatchIn(text)

    private class Raw(
        val start: Long,
        val body: String,
    )

    fun parse(
        src: String,
        source: String = "",
    ): LyricsDoc {
        var offset = 0L
        val raws = ArrayList<Raw>()
        for (rawLine in src.lineSequence()) {
            val line = rawLine.trim('\uFEFF', ' ', '\t', '\r')
            if (line.isEmpty()) continue
            val stamps = ArrayList<Long>()
            var pos = 0
            while (true) {
                val m = lineTag.find(line, pos)
                if (m == null || m.range.first != pos) break
                stamps.add(toMs(m))
                pos = m.range.last + 1
            }
            if (stamps.isEmpty()) {
                val meta = metaTag.matchEntire(line)
                if (meta != null && meta.groupValues[1].lowercase() == "offset") {
                    offset = meta.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                continue
            }
            val body = line.substring(pos)
            for (s in stamps) raws.add(Raw(s, body))
        }
        if (raws.isEmpty()) throw LyricsParseException("LRC: no timed lines")

        // A positive [offset:] makes lyrics appear sooner (LRC convention).
        fun at(ms: Long): Long = (ms - offset).coerceAtLeast(0L)
        val sorted = raws.sortedBy { it.start }
        val lines = ArrayList<LyricLine>()
        var rich = false
        for ((i, r) in sorted.withIndex()) {
            var j = i + 1
            while (j < sorted.size && sorted[j].start <= r.start) j++
            val start = at(r.start)
            val end = if (j < sorted.size) at(sorted[j].start) else start + LAST_LINE_MS
            val parts = parseWords(r.body, start, end, ::at)
            val text =
                if (parts.isEmpty()) {
                    r.body.trim()
                } else {
                    parts.joinToString("") { it.text }.trim()
                }
            if (text.isEmpty()) continue
            if (parts.isNotEmpty()) rich = true
            lines.add(LyricLine(start, end, text, parts))
        }
        if (lines.isEmpty()) throw LyricsParseException("LRC: every timed line is empty")
        return LyricsDoc(lines, if (rich) SyncType.RICH else SyncType.LINE, source)
    }

    private fun parseWords(
        body: String,
        lineStart: Long,
        lineEnd: Long,
        at: (Long) -> Long,
    ): List<LyricPart> {
        val tags = wordTag.findAll(body).toList()
        if (tags.isEmpty()) return emptyList()
        val segs = ArrayList<Pair<Long, String>>() // start, raw text
        val leading = body.substring(0, tags.first().range.first)
        if (leading.isNotBlank()) segs.add(lineStart to leading)
        for ((k, t) in tags.withIndex()) {
            val from = t.range.last + 1
            val to = if (k + 1 < tags.size) tags[k + 1].range.first else body.length
            segs.add(at(toMs(t)) to body.substring(from, to))
        }
        val parts = ArrayList<LyricPart>()
        for ((k, seg) in segs.withIndex()) {
            val raw = seg.second
            if (raw.isBlank()) continue
            val next = segs.getOrNull(k + 1)
            val end = next?.first ?: minOf(lineEnd, seg.first + LAST_WORD_MAX_MS)
            val spaceAfter = raw.last().isWhitespace() || (next != null && next.second.firstOrNull()?.isWhitespace() == true)
            parts.add(LyricPart(seg.first, (end - seg.first).coerceAtLeast(0L), raw.trim() + if (spaceAfter) " " else ""))
        }
        if (parts.isNotEmpty()) {
            val last = parts.last()
            parts[parts.size - 1] = last.copy(text = last.text.trimEnd())
        }
        return parts
    }

    private fun toMs(m: MatchResult): Long {
        val minutes = m.groupValues[1].toLong()
        val seconds = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val ms =
            when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.substring(0, 3).toLong()
            }
        return minutes * 60_000 + seconds * 1_000 + ms
    }
}
