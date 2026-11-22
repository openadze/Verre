package io.ymusic.lyrics

internal sealed interface XmlNode

internal class XmlText(
    val text: String,
) : XmlNode

internal class XmlElement(
    val name: String,
    val attrs: Map<String, String>,
    val children: List<XmlNode>,
) : XmlNode {
    val localName: String get() = name.substringAfter(':')

    /** Attribute by local name, whatever its prefix (`ttm:agent` -> "agent"). */
    fun attrLocal(local: String): String? = attrs.entries.firstOrNull { it.key.substringAfter(':') == local }?.value

    fun text(): String =
        buildString {
            for (c in children) {
                when (c) {
                    is XmlText -> append(c.text)
                    is XmlElement -> append(c.text())
                }
            }
        }

    fun descendants(): Sequence<XmlElement> =
        sequence {
            for (c in children) {
                if (c is XmlElement) {
                    yield(c)
                    yieldAll(c.descendants())
                }
            }
        }
}

/** A tiny non-validating XML reader: elements, attributes, text, CDATA, comments, entities. No DTD, no namespaces. */
internal object MiniXml {
    private const val MAX_DEPTH = 200

    fun parse(src: String): XmlElement = Reader(src).document()

    private class Reader(
        private val s: String,
    ) {
        private var i = 0

        private fun fail(msg: String): Nothing = throw LyricsParseException("XML: $msg at $i")

        fun document(): XmlElement {
            var root: XmlElement? = null
            while (true) {
                skipMisc()
                if (i >= s.length) break
                if (s[i] != '<') fail("text outside the root element")
                if (root != null) fail("content after the root element")
                root = element(0)
            }
            return root ?: fail("no root element")
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun skipPast(end: String) {
            val at = s.indexOf(end, i)
            if (at < 0) fail("unterminated '$end'")
            i = at + end.length
        }

        private fun skipMisc() {
            while (true) {
                skipWs()
                when {
                    s.startsWith("\uFEFF", i) -> i++
                    s.startsWith("<!--", i) -> skipPast("-->")
                    s.startsWith("<?", i) -> skipPast("?>")
                    s.startsWith("<!", i) -> skipPast(">")
                    else -> return
                }
            }
        }

        private fun name(): String {
            val start = i
            while (i < s.length && !s[i].isWhitespace() && s[i] != '/' && s[i] != '>' && s[i] != '=') i++
            if (i == start) fail("expected a name")
            return s.substring(start, i)
        }

        private fun element(depth: Int): XmlElement {
            if (depth > MAX_DEPTH) fail("nesting deeper than $MAX_DEPTH")
            i++ // '<'
            val tag = name()
            val attrs = LinkedHashMap<String, String>()
            while (true) {
                skipWs()
                if (i >= s.length) fail("unterminated <$tag>")
                when (s[i]) {
                    '/' -> {
                        if (!s.startsWith("/>", i)) fail("stray '/' in <$tag>")
                        i += 2
                        return XmlElement(tag, attrs, emptyList())
                    }

                    '>' -> {
                        i++
                        break
                    }

                    else -> {
                        val key = name()
                        skipWs()
                        if (i >= s.length || s[i] != '=') fail("attribute '$key' has no value")
                        i++
                        skipWs()
                        if (i >= s.length || (s[i] != '"' && s[i] != '\'')) fail("attribute '$key' is not quoted")
                        val quote = s[i++]
                        val end = s.indexOf(quote, i)
                        if (end < 0) fail("unterminated attribute '$key'")
                        attrs[key] = decode(s.substring(i, end))
                        i = end + 1
                    }
                }
            }
            val children = ArrayList<XmlNode>()
            while (true) {
                if (i >= s.length) fail("unterminated <$tag>")
                when {
                    s.startsWith("</", i) -> {
                        i += 2
                        val close = name()
                        skipWs()
                        if (i >= s.length || s[i] != '>') fail("malformed </$close>")
                        i++
                        if (close != tag) fail("</$close> closes <$tag>")
                        return XmlElement(tag, attrs, children)
                    }

                    s.startsWith("<!--", i) -> {
                        skipPast("-->")
                    }

                    s.startsWith("<![CDATA[", i) -> {
                        val at = s.indexOf("]]>", i)
                        if (at < 0) fail("unterminated CDATA")
                        children.add(XmlText(s.substring(i + 9, at)))
                        i = at + 3
                    }

                    s.startsWith("<?", i) -> {
                        skipPast("?>")
                    }

                    s[i] == '<' -> {
                        children.add(element(depth + 1))
                    }

                    else -> {
                        val end = s.indexOf('<', i).let { if (it < 0) s.length else it }
                        children.add(XmlText(decode(s.substring(i, end))))
                        i = end
                    }
                }
            }
        }

        private fun decode(t: String): String {
            if ('&' !in t) return t
            val sb = StringBuilder()
            var k = 0
            while (k < t.length) {
                val c = t[k]
                if (c == '&') {
                    val semi = t.indexOf(';', k)
                    if (semi > k && semi - k <= 10) {
                        val rep = entity(t.substring(k + 1, semi))
                        if (rep != null) {
                            sb.append(rep)
                            k = semi + 1
                            continue
                        }
                    }
                }
                sb.append(c)
                k++
            }
            return sb.toString()
        }

        private fun entity(e: String): String? =
            when {
                e == "amp" -> "&"
                e == "lt" -> "<"
                e == "gt" -> ">"
                e == "quot" -> "\""
                e == "apos" -> "'"
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let(::codePoint)
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let(::codePoint)
                else -> null
            }

        private fun codePoint(cp: Int): String? =
            when {
                cp < 0 || cp > 0x10FFFF -> null
                cp <= 0xFFFF -> cp.toChar().toString()
                else -> {
                    val v = cp - 0x10000
                    charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
                }
            }
    }
}
