package io.ymusic.lyrics

import kotlin.math.roundToLong

/**
 * TTML lyrics (Apple Music style): `<p>` lines with optional `<span begin end>` syllables, `ttm:role="x-bg"`
 * background vocals, `ttm:agent`, and translation / romanization (inline `x-translation` / `x-roman` spans or the
 * head's `<translation>` / `<transliteration>` tables keyed by `itunes:key`). Syllables are grouped into words.
 */
object TtmlParser {
    fun parse(
        src: String,
        source: String = "",
    ): LyricsDoc {
        val root = MiniXml.parse(src)
        if (root.localName != "tt") throw LyricsParseException("TTML: root is <${root.name}>, expected <tt>")
        val body = root.descendants().firstOrNull { it.localName == "body" } ?: throw LyricsParseException("TTML: no <body>")
        val translations = sideTable(root, "translation")
        val romans = sideTable(root, "transliteration")

        val built = ArrayList<LyricLine>()
        val untimed = ArrayList<String>()
        for (p in body.descendants().filter { it.localName == "p" }) {
            val line = line(p, translations, romans)
            if (line != null) built.add(line) else p.text().collapse().takeIf { it.isNotEmpty() }?.let(untimed::add)
        }
        val language = root.attrLocal("lang")
        if (built.isEmpty()) {
            if (untimed.isEmpty()) throw LyricsParseException("TTML: no lyric lines")
            return LyricsDoc(untimed.map { LyricLine(0L, 0L, it) }, SyncType.NONE, source, language)
        }
        val sorted = built.sortedBy { it.startMs }
        val rich = sorted.any { it.parts.isNotEmpty() }
        return LyricsDoc(sorted, if (rich) SyncType.RICH else SyncType.LINE, source, language)
    }

    private fun sideTable(
        root: XmlElement,
        tableName: String,
    ): Map<String, String> {
        val out = HashMap<String, String>()
        for (table in root.descendants().filter { it.localName == tableName }) {
            for (t in table.children.filterIsInstance<XmlElement>().filter { it.localName == "text" }) {
                val key = t.attrLocal("for") ?: continue
                val value = t.text().collapse()
                if (value.isNotEmpty()) out.putIfAbsent(key, value)
            }
        }
        return out
    }

    private class Syl(
        val text: String,
        val start: Long,
        val end: Long,
        val bg: Boolean,
    )

    private sealed interface Item {
        class S(
            val syl: Syl,
        ) : Item

        data object Sep : Item
    }

    private class Ctx {
        val items = ArrayList<Item>()
        var translation: String? = null
        var roman: String? = null
    }

    private fun walk(
        el: XmlElement,
        bg: Boolean,
        ctx: Ctx,
    ) {
        for (c in el.children) {
            when (c) {
                is XmlText -> {
                    if (c.text.isNotEmpty() && c.text.isBlank()) ctx.items.add(Item.Sep)
                }

                is XmlElement -> {
                    val role = c.attrLocal("role")
                    val begin = c.attrLocal("begin")?.let(::parseTtmlTime)
                    val end = c.attrLocal("end")?.let(::parseTtmlTime)
                    when {
                        role == "x-translation" -> ctx.translation = c.text().collapse()
                        role == "x-roman" -> ctx.roman = c.text().collapse()
                        role == "x-bg" -> walk(c, true, ctx)
                        c.localName == "br" -> ctx.items.add(Item.Sep)
                        begin != null && end != null -> ctx.items.add(Item.S(Syl(c.text(), begin, end, bg)))
                        else -> walk(c, bg, ctx)
                    }
                }
            }
        }
    }

    private fun line(
        p: XmlElement,
        translations: Map<String, String>,
        romans: Map<String, String>,
    ): LyricLine? {
        val pBegin = p.attrLocal("begin")?.let(::parseTtmlTime)
        val pEnd = p.attrLocal("end")?.let(::parseTtmlTime)
        val ctx = Ctx()
        walk(p, false, ctx)
        var sylls = ctx.items.filterIsInstance<Item.S>().map { it.syl }
        val key = p.attrLocal("key")
        val translation = ctx.translation ?: key?.let { translations[it] }
        val roman = ctx.roman ?: key?.let { romans[it] }
        val agent = p.attrLocal("agent")

        if (sylls.isEmpty()) {
            if (pBegin == null) return null
            val text = plainText(p)
            if (text.isEmpty()) return null
            val start = pBegin
            return LyricLine(start, (pEnd ?: start).coerceAtLeast(start), text, emptyList(), agent, false, translation, roman)
        }
        // Strict TTML makes span times relative to the line; Apple-style files are absolute. Tell them apart.
        if (pBegin != null && sylls.minOf { it.start } < pBegin) {
            sylls = sylls.map { Syl(it.text, it.start + pBegin, it.end + pBegin, it.bg) }
            val shifted = ArrayList<Item>()
            var k = 0
            for (it in ctx.items) shifted.add(if (it is Item.S) Item.S(sylls[k++]) else it)
            ctx.items.clear()
            ctx.items.addAll(shifted)
        }
        val parts = group(ctx.items)
        if (parts.isEmpty()) return null
        val main = parts.filter { !it.isBackground }
        val onlyBg = main.isEmpty()
        val text = (if (onlyBg) parts else main).joinToString("") { it.text }.trim()
        val start = pBegin ?: parts.minOf { it.startMs }
        val end = pEnd ?: parts.maxOf { it.endMs }
        return LyricLine(start, end.coerceAtLeast(start), text, parts, agent, onlyBg, translation, roman)
    }

    private fun plainText(p: XmlElement): String {
        val sb = StringBuilder()
        fun go(e: XmlElement) {
            for (c in e.children) {
                when (c) {
                    is XmlText -> sb.append(c.text)
                    is XmlElement ->
                        when {
                            c.attrLocal("role") in setOf("x-translation", "x-roman") -> Unit
                            c.localName == "br" -> sb.append(' ')
                            else -> go(c)
                        }
                }
            }
        }
        go(p)
        return sb.toString().collapse()
    }

    private class Word(
        val text: String,
        val start: Long,
        val end: Long,
        val bg: Boolean,
        var spaceAfter: Boolean,
    )

    /** Syllables -> words: a word ends at whitespace (in or between spans), a background switch, or after a CJK syllable. */
    private fun group(items: List<Item>): List<LyricPart> {
        val words = ArrayList<Word>()
        val cur = StringBuilder()
        var curStart = 0L
        var curEnd = 0L
        var curBg = false

        fun flush(spaceAfter: Boolean) {
            if (cur.isEmpty()) {
                if (spaceAfter) words.lastOrNull()?.spaceAfter = true
                return
            }
            words.add(Word(cur.toString(), curStart, curEnd, curBg, spaceAfter))
            cur.clear()
        }
        for (item in items) {
            when (item) {
                Item.Sep -> flush(true)
                is Item.S -> {
                    val syl = item.syl
                    val raw = syl.text
                    if (raw.isBlank()) {
                        flush(true)
                        continue
                    }
                    if (cur.isNotEmpty() && (syl.bg != curBg || raw.first().isWhitespace())) flush(true)
                    val core = raw.trim()
                    if (cur.isEmpty()) {
                        curStart = syl.start
                        curBg = syl.bg
                    }
                    cur.append(core)
                    curEnd = syl.end
                    when {
                        raw.last().isWhitespace() -> flush(true)
                        core.any(::isCjk) -> flush(false)
                    }
                }
            }
        }
        flush(false)
        return words.mapIndexed { i, w ->
            val space = w.spaceAfter && i < words.size - 1
            LyricPart(w.start, (w.end - w.start).coerceAtLeast(0L), w.text + if (space) " " else "", w.bg)
        }
    }

    private fun isCjk(c: Char): Boolean = c.code in 0x3040..0x30FF || c.code in 0x3400..0x9FFF || c.code in 0xF900..0xFAFF

    private fun String.collapse(): String = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
}

/** TTML clock value to milliseconds: `12.5s`, `1500ms`, `1:02.345`, `00:01:02.345`, `00:00:01:15` (30 fps frames). */
internal fun parseTtmlTime(raw: String): Long? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    fun secs(x: String): Long? = x.toDoubleOrNull()?.let { (it * 1000).roundToLong() }
    return when {
        t.endsWith("ms") -> t.dropLast(2).toDoubleOrNull()?.roundToLong()
        t.endsWith("s") -> secs(t.dropLast(1))
        t.endsWith("m") -> t.dropLast(1).toDoubleOrNull()?.let { (it * 60_000).roundToLong() }
        t.endsWith("h") -> t.dropLast(1).toDoubleOrNull()?.let { (it * 3_600_000).roundToLong() }
        ':' in t -> {
            val p = t.split(':')
            when (p.size) {
                2 -> {
                    val m = p[0].toLongOrNull()
                    val s = secs(p[1])
                    if (m == null || s == null) null else m * 60_000 + s
                }

                3 -> {
                    val h = p[0].toLongOrNull()
                    val m = p[1].toLongOrNull()
                    val s = secs(p[2])
                    if (h == null || m == null || s == null) null else h * 3_600_000 + m * 60_000 + s
                }

                4 -> {
                    val h = p[0].toLongOrNull()
                    val m = p[1].toLongOrNull()
                    val s = p[2].toLongOrNull()
                    val f = p[3].toLongOrNull()
                    if (h == null || m == null || s == null || f == null) null else h * 3_600_000 + m * 60_000 + s * 1_000 + f * 1_000 / 30
                }

                else -> null
            }
        }

        else -> secs(t)
    }
}
