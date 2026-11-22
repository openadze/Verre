package io.ymusic.lyrics

/**
 * LRCLIB's `lyricsfile` (the YAML field `/api/get` and `/api/search` return next to `syncedLyrics`). It is the only
 * LRCLIB representation that can carry word timing:
 *
 * ```
 * metadata: { offset_ms, language, instrumental, ... }
 * lines:
 *   - text: "Hello world"
 *     start_ms: 12040
 *     end_ms: 15200
 *     words:                    # optional
 *       - { text: Hello, start_ms: 12040, end_ms: 12550 }   # or the block form, one key per line
 * plain: |
 *   ...
 * ```
 *
 * Not a general YAML parser: it reads exactly this shape (block lists of `key: value` maps, one nesting level for
 * `words`, inline `{ a: b }` maps, quoted or bare scalars). A missing `end_ms` is interpolated; empty or `null`
 * `words` leave the line as a plain timed line. Throws [LyricsParseException] (E93001) when no line is timed.
 */
object LyricsfileParser {
    private const val LAST_LINE_MS = 5_000L
    private const val LAST_WORD_MAX_MS = 3_000L

    fun looksLikeLyricsfile(text: String): Boolean {
        val t = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return t.startsWith("version:") || t.startsWith("metadata:") || t.startsWith("lines:") || t.contains("\nlines:")
    }

    private class RawWord(val text: String, val start: Long?, val end: Long?)

    private class RawLine(val fields: MutableMap<String, String> = HashMap(), val words: MutableList<RawWord> = ArrayList())

    fun parse(
        src: String,
        source: String = "",
    ): LyricsDoc {
        var section = ""
        val meta = HashMap<String, String>()
        val rawLines = ArrayList<RawLine>()
        var lineIndent = -1
        var current: RawLine? = null
        var wordFields: MutableMap<String, String>? = null
        var inWords = false

        fun flushWord() {
            val f = wordFields ?: return
            current?.words?.add(RawWord(f["text"] ?: "", f["start_ms"]?.toLongOrNull(), f["end_ms"]?.toLongOrNull()))
            wordFields = null
        }

        for (rawLine in src.lineSequence()) {
            val trimmedEnd = rawLine.trimEnd('\r', ' ', '\t')
            val body = trimmedEnd.trimStart(' ', '\t')
            if (body.isEmpty() || body.startsWith("#")) continue
            val indent = trimmedEnd.length - body.length
            if (indent == 0 && !body.startsWith("-")) {
                flushWord()
                inWords = false
                val (k, v) = splitKeyValue(body) ?: continue
                section = k
                if (k != "lines" && v.isNotEmpty() && v != "|" && v != ">") meta[k] = unquote(v)
                continue
            }
            when (section) {
                "metadata" -> splitKeyValue(body)?.let { (k, v) -> meta[k] = unquote(v) }
                "lines" -> {
                    val isItem = body.startsWith("- ") || body == "-"
                    val content = if (isItem) body.removePrefix("-").trimStart() else body
                    if (isItem && (lineIndent < 0 || indent <= lineIndent)) {
                        flushWord()
                        inWords = false
                        if (lineIndent < 0) lineIndent = indent
                        val created = RawLine().also(rawLines::add)
                        current = created
                        inlineOrPair(content)?.let { created.fields.putAll(it) }
                        continue
                    }
                    val line = current ?: continue
                    if (isItem && inWords) {
                        flushWord()
                        val fields = HashMap<String, String>()
                        inlineOrPair(content)?.let(fields::putAll)
                        wordFields = fields
                    } else if (!isItem) {
                        val kv = splitKeyValue(content) ?: continue
                        if (kv.first == "words") {
                            flushWord()
                            inWords = true
                            if (kv.second.startsWith("[") && kv.second.length > 2) {
                                for (w in inlineList(kv.second)) {
                                    line.words.add(RawWord(w["text"] ?: "", w["start_ms"]?.toLongOrNull(), w["end_ms"]?.toLongOrNull()))
                                }
                            }
                        } else if (inWords && wordFields != null) {
                            wordFields!![kv.first] = unquote(kv.second)
                        } else {
                            line.fields[kv.first] = unquote(kv.second)
                        }
                    }
                }
            }
        }
        flushWord()

        if (meta["instrumental"] == "true") throw LyricsParseException("lyricsfile: instrumental")
        val offset = meta["offset_ms"]?.toLongOrNull() ?: 0L

        fun at(ms: Long): Long = (ms - offset).coerceAtLeast(0L)
        val timed = rawLines.filter { it.fields["start_ms"]?.toLongOrNull() != null || it.words.any { w -> w.start != null } }
        if (timed.isEmpty()) throw LyricsParseException("lyricsfile: no timed lines")

        val lines = ArrayList<LyricLine>()
        var rich = false
        for ((i, r) in timed.withIndex()) {
            val firstWord = r.words.firstOrNull { it.start != null }?.start
            val start = at(r.fields["start_ms"]?.toLongOrNull() ?: firstWord ?: 0L)
            val explicitEnd = r.fields["end_ms"]?.toLongOrNull()
            val nextStart = timed.getOrNull(i + 1)?.let { n -> n.fields["start_ms"]?.toLongOrNull() ?: n.words.firstNotNullOfOrNull { it.start } }
            val lastWordEnd = r.words.lastOrNull { it.end != null }?.end
            val end = at(explicitEnd ?: nextStart ?: lastWordEnd ?: (start + LAST_LINE_MS)).coerceAtLeast(start)
            val parts = buildParts(r, start, end, ::at)
            val text = r.fields["text"]?.trim().orEmpty().ifEmpty { parts.joinToString("") { it.text }.trim() }
            if (text.isEmpty()) continue
            if (parts.isNotEmpty()) rich = true
            lines.add(LyricLine(start, end, text, parts))
        }
        if (lines.isEmpty()) throw LyricsParseException("lyricsfile: every timed line is empty")
        return LyricsDoc(lines, if (rich) SyncType.RICH else SyncType.LINE, source, meta["language"])
    }

    private fun buildParts(
        r: RawLine,
        lineStart: Long,
        lineEnd: Long,
        at: (Long) -> Long,
    ): List<LyricPart> {
        val words = r.words.filter { it.text.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lineText = r.fields["text"].orEmpty()
        var cursor = 0
        val parts = ArrayList<LyricPart>()
        for ((k, w) in words.withIndex()) {
            val token = w.text.trim()
            val start = at(w.start ?: parts.lastOrNull()?.endMs ?: lineStart)
            val next = words.getOrNull(k + 1)?.start?.let(at)
            val end = at(w.end ?: 0L).takeIf { w.end != null } ?: next ?: minOf(lineEnd, start + LAST_WORD_MAX_MS)
            // The space after a word comes from the line text when the word can be found in it, else it is assumed.
            val found = if (lineText.isEmpty()) -1 else lineText.indexOf(token, cursor)
            val spaceAfter =
                if (found >= 0) {
                    cursor = found + token.length
                    cursor < lineText.length && lineText[cursor].isWhitespace()
                } else {
                    w.text.last().isWhitespace() || !token.isCjk()
                }
            parts.add(LyricPart(start, (end - start).coerceAtLeast(0L), token + if (spaceAfter) " " else ""))
        }
        val last = parts.last()
        parts[parts.size - 1] = last.copy(text = last.text.trimEnd())
        return parts
    }

    private fun String.isCjk(): Boolean = any { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF || it.code in 0xAC00..0xD7AF }

    /** `key: value` at the start of [s]; the value is returned raw (trimmed, not unquoted). */
    private fun splitKeyValue(s: String): Pair<String, String>? {
        val i = s.indexOf(':')
        if (i <= 0) return null
        return s.substring(0, i).trim().trim('"', '\'') to s.substring(i + 1).trim()
    }

    /** The content of a list item: `{ a: 1, b: x }` or one `key: value` pair. */
    private fun inlineOrPair(content: String): Map<String, String>? {
        if (content.isEmpty()) return null
        if (content.startsWith("{")) return inlineMap(content)
        val kv = splitKeyValue(content) ?: return null
        return mapOf(kv.first to unquote(kv.second))
    }

    private fun inlineList(s: String): List<Map<String, String>> {
        val inner = s.trim().removePrefix("[").removeSuffix("]")
        val out = ArrayList<Map<String, String>>()
        var depth = 0
        val sb = StringBuilder()
        var quote = '\u0000'
        for (c in inner) {
            if (quote != '\u0000') {
                sb.append(c)
                if (c == quote) quote = '\u0000'
                continue
            }
            when (c) {
                '"', '\'' -> {
                    quote = c
                    sb.append(c)
                }
                '{' -> {
                    depth++
                    sb.append(c)
                }
                '}' -> {
                    depth--
                    sb.append(c)
                    if (depth == 0) {
                        out.add(inlineMap(sb.toString().trim()))
                        sb.clear()
                    }
                }
                else -> if (depth > 0) sb.append(c)
            }
        }
        return out
    }

    private fun inlineMap(s: String): Map<String, String> {
        val inner = s.trim().removePrefix("{").removeSuffix("}")
        val out = HashMap<String, String>()
        val sb = StringBuilder()
        var quote = '\u0000'

        fun flush() {
            splitKeyValue(sb.toString().trim())?.let { (k, v) -> out[k] = unquote(v) }
            sb.clear()
        }
        for (c in inner) {
            if (quote != '\u0000') {
                sb.append(c)
                if (c == quote) quote = '\u0000'
            } else if (c == '"' || c == '\'') {
                quote = c
                sb.append(c)
            } else if (c == ',') {
                flush()
            } else {
                sb.append(c)
            }
        }
        flush()
        return out
    }

    private fun unquote(v: String): String {
        val s = v.trim()
        if (s == "null" || s == "~") return ""
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            val sb = StringBuilder()
            var i = 1
            while (i < s.length - 1) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length - 1) {
                    i++
                    when (val n = s[i]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        else -> sb.append(n)
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            return sb.toString()
        }
        if (s.length >= 2 && s.first() == '\'' && s.last() == '\'') return s.substring(1, s.length - 1).replace("''", "'")
        return s
    }
}
