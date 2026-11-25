package com.cherry.butler

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Android compiles patterns with ICU, which refuses things a PC's Java accepts. The one that
 * bit: a brace that is not part of a counted repeat must be escaped (`a}` and `a{` both throw
 * on a phone). Tests run on the PC, where such a pattern passes, so this reads every pattern
 * written out in the sources and checks its braces by ICU's rule.
 */
class AndroidRegexTest {
    @Test
    fun `a bare brace is caught and the legal ones are not`() {
        assertTrue(bareBrace("""\{\s*OOC(.+?)}""") != null)
        assertTrue(bareBrace("a{") != null)
        assertEquals(null, bareBrace("""\{\s*OOC(.+?)\}"""))
        assertEquals(null, bareBrace("""a{2}b{1,}c{0,3}"""))
        assertEquals(null, bareBrace("""[^\p{L}\p{N} ]+\x{1F600}"""))
        assertEquals(null, bareBrace("""[{}]"""))
        assertEquals(null, bareBrace("""\Q{}\E"""))
    }

    @Test
    fun `no pattern in the sources has a bare brace`() {
        val root = File("src/main/java")
        assertTrue(root.isDirectory, "run from the app module")
        val raw = Regex("(?s)Regex\\(\\s*\"\"\"(.*?)\"\"\"")
        val plain = Regex("Regex\\(\\s*\"((?:[^\"\\\\\n]|\\\\.)*)\"")
        val wrong = ArrayList<String>()
        var seen = 0
        root.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            val patterns = raw.findAll(text).map { it.range.first to it.groupValues[1] } +
                plain.findAll(text).filter { !text.startsWith("\"\"\"", it.range.first + it.value.indexOf('"')) }
                    .map { it.range.first to it.groupValues[1].replace("\\\\", "\\") }
            for ((at, pattern) in patterns) {
                // Built from a variable: not a pattern until it runs.
                if ("\$" in pattern) continue
                seen++
                bareBrace(pattern)?.let { wrong += "${file.path}:${text.take(at).count { c -> c == '\n' } + 1}: $it in $pattern" }
            }
        }
        assertTrue(seen > 50, "found only $seen patterns: the scan is broken")
        assertEquals(emptyList(), wrong)
    }

    /** Where [pattern] has a brace ICU would refuse, or null. */
    private fun bareBrace(pattern: String): String? {
        val repeat = Regex("""\{\d+(,\d*)?\}""")
        var i = 0
        var inSet = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> {
                    val next = pattern.getOrNull(i + 1)
                    if (next == 'Q') {
                        val end = pattern.indexOf("\\E", i + 2)
                        i = if (end < 0) pattern.length else end + 2
                        continue
                    }
                    // \p{L}, \x{1F600}, \N{name}: the braces are the escape's own.
                    if (next != null && next in "pPxN" && pattern.getOrNull(i + 2) == '{') {
                        val end = pattern.indexOf('}', i + 2)
                        i = if (end < 0) pattern.length else end + 1
                        continue
                    }
                    i += 2
                    continue
                }
                inSet > 0 -> if (c == '[') inSet++ else if (c == ']') inSet--
                c == '[' -> inSet = 1
                c == '{' -> {
                    val counted = repeat.matchAt(pattern, i) ?: return "a bare { at $i"
                    i = counted.range.last + 1
                    continue
                }
                c == '}' -> return "a bare } at $i"
            }
            i++
        }
        return null
    }
}
