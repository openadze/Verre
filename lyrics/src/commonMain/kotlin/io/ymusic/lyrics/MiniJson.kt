package io.ymusic.lyrics

/**
 * A tiny JSON reader (no dependency, like [MiniXml]) for provider responses. Objects become `Map<String, Any?>`,
 * arrays `List<Any?>`, numbers `Double`, plus `String`, `Boolean` and `null`. Malformed input throws
 * [LyricsProviderException] (E93003).
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        val p = Reader(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd) p.fail("unexpected trailing characters")
        return v
    }

    private class Reader(
        private val s: String,
    ) {
        private var i = 0
        val atEnd: Boolean get() = i >= s.length

        fun fail(what: String): Nothing = throw LyricsProviderException("E93003", "bad JSON at $i: $what")

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            if (atEnd) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("unexpected '$c'")
            }
        }

        private fun literal(
            word: String,
            v: Any?,
        ): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length
            return v
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("bad number")
        }

        private fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd) fail("unterminated escape")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail("short \\u escape")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad \\u escape"))
                                i += 4
                            }
                            '"', '\\', '/' -> sb.append(e)
                            else -> fail("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') {
                i++
                return out
            }
            while (true) {
                skipWs()
                out.add(value())
                skipWs()
                if (atEnd) fail("unterminated array")
                when (s[i++]) {
                    ',' -> Unit
                    ']' -> return out
                    else -> fail("expected , or ]")
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') {
                i++
                return out
            }
            while (true) {
                skipWs()
                if (atEnd || s[i] != '"') fail("expected key")
                val k = str()
                skipWs()
                if (atEnd || s[i++] != ':') fail("expected :")
                skipWs()
                out[k] = value()
                skipWs()
                if (atEnd) fail("unterminated object")
                when (s[i++]) {
                    ',' -> Unit
                    '}' -> return out
                    else -> fail("expected , or }")
                }
            }
        }
    }
}
