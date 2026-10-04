package com.cherry.butler.core.markdown

import com.cherry.butler.core.markdown.RpMarkdown.Block
import com.cherry.butler.core.markdown.RpMarkdown.InlineKind
import com.cherry.butler.core.markdown.RpMarkdown.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules from ARCHITECTURE.md §5, pinned: `**` before `*`, unbalanced markers stay
 * literal, and roleplay's speech/action mix resolves without one construct eating the rest.
 */
class RpMarkdownTest {

    private fun inline(s: String) = RpMarkdown.parseInline(s)
    private fun kinds(spans: List<Span>) = spans.map { it.kind }

    @Test
    fun `action and speech side by side`() {
        val (text, spans) = inline("""*She smiles* "Hello," she says.""")
        assertEquals("""She smiles "Hello," she says.""".replace("\"Hello,\"", "Hello,"), text)
        assertEquals(listOf(InlineKind.Action, InlineKind.Speech), kinds(spans))
        assertEquals("She smiles", text.substring(spans[0].start, spans[0].end))
        assertEquals("Hello,", text.substring(spans[1].start, spans[1].end))
    }

    @Test
    fun `double star resolves before single star`() {
        val (text, spans) = inline("**bold** next to *action*")
        assertEquals("bold next to action", text)
        assertEquals(listOf(InlineKind.Strong, InlineKind.Action), kinds(spans))
    }

    @Test
    fun `triple star is strong plus action`() {
        val (text, spans) = inline("***both***")
        assertEquals("both", text)
        assertTrue(InlineKind.Strong in kinds(spans))
        assertTrue(InlineKind.Action in kinds(spans))
    }

    @Test
    fun `arithmetic stars are not markup`() {
        val (text, spans) = inline("5 * 3 * 2 = 30")
        assertEquals("5 * 3 * 2 = 30", text)
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `an unclosed star is narration to the end of its paragraph, not beyond`() {
        val blocks = RpMarkdown.parse("*she trails off and the rest\n\nNext paragraph stays plain")
        val first = blocks[0] as Block.Paragraph
        assertEquals("she trails off and the rest", first.text)
        assertEquals(listOf(InlineKind.Action), kinds(first.spans))
        assertTrue((blocks[1] as Block.Paragraph).spans.isEmpty())
    }

    @Test
    fun `an unclosed quote renders literally`() {
        val (text, spans) = inline("""He said "wait""")
        assertEquals("""He said "wait""", text)
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `thought in backticks`() {
        val (text, spans) = inline("`I should leave.` She stays.")
        assertEquals("I should leave. She stays.", text)
        assertEquals(listOf(InlineKind.Thought), kinds(spans))
    }

    @Test
    fun `curly quotes are speech`() {
        val (text, spans) = inline("“Come in,” he said.")
        assertEquals("Come in, he said.", text)
        assertEquals(listOf(InlineKind.Speech), kinds(spans))
    }

    @Test
    fun `speech nested inside action keeps both spans`() {
        val (text, spans) = inline("""*"Fine," she snaps*""")
        assertEquals("Fine, she snaps", text)
        assertTrue(InlineKind.Action in kinds(spans))
        assertTrue(InlineKind.Speech in kinds(spans))
        val action = spans.first { it.kind == InlineKind.Action }
        assertEquals(0, action.start)
        assertEquals(text.length, action.end)
    }

    @Test
    fun `strong inside speech`() {
        val (text, spans) = inline("\"I **really** mean it\"")
        assertEquals("I really mean it", text)
        val strong = spans.first { it.kind == InlineKind.Strong }
        assertEquals("really", text.substring(strong.start, strong.end))
    }

    @Test
    fun `blocks - paragraphs rules and lists`() {
        val blocks = RpMarkdown.parse(
            """
            First paragraph
            continues here.

            ---
            - one
            - two
            1. first
            2. second
            """.trimIndent(),
        )
        assertTrue(blocks[0] is Block.Paragraph)
        assertEquals("First paragraph\ncontinues here.", (blocks[0] as Block.Paragraph).text)
        assertTrue(blocks[1] is Block.Rule)
        assertEquals(Block.ListItem("one", emptyList(), ordered = false, index = 0), blocks[2])
        assertEquals(Block.ListItem("two", emptyList(), ordered = false, index = 0), blocks[3])
        assertEquals(Block.ListItem("first", emptyList(), ordered = true, index = 1), blocks[4])
        assertEquals(Block.ListItem("second", emptyList(), ordered = true, index = 2), blocks[5])
    }

    @Test
    fun `real captured reply shape`() {
        val (text, spans) = inline(
            """*Mia smiles warmly at you, her violet eyes softening.* "Welcome home," *she says, taking your coat.*""",
        )
        assertEquals(
            "Mia smiles warmly at you, her violet eyes softening. Welcome home, she says, taking your coat.",
            text,
        )
        assertEquals(listOf(InlineKind.Action, InlineKind.Speech, InlineKind.Action), kinds(spans))
    }

    @Test
    fun `underscores emphasise but never inside a word`() {
        val (text, spans) = inline("_quiet_ and __loud__ in snake_case_name")
        assertEquals("quiet and loud in snake_case_name", text)
        assertEquals(listOf(InlineKind.Action, InlineKind.Strong), kinds(spans))
        assertEquals("quiet", text.substring(spans[0].start, spans[0].end))
        assertEquals("loud", text.substring(spans[1].start, spans[1].end))
    }

    @Test
    fun `backslash escapes are literal punctuation`() {
        val (text, spans) = inline("""\~~~FIXING~~~ and \*not action\*""")
        assertEquals("~~~FIXING~~~ and *not action*", text)
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `a stray star reads as narration up to the next speech`() {
        val (text, spans) = RpMarkdown.parseInline("“If you want this” *she spread her arms—“then ask me.”")
        assertEquals("If you want this she spread her arms—then ask me.", text)
        val action = spans.single { it.kind == InlineKind.Action }
        assertEquals("she spread her arms—", text.substring(action.start, action.end))
        assertEquals(2, spans.count { it.kind == InlineKind.Speech })
    }

    @Test
    fun `quote marks can be kept around speech`() {
        val (text, spans) = RpMarkdown.parseInline("He said \"hi\" quietly", keepQuotes = true)
        assertEquals("He said \"hi\" quietly", text)
        val speech = spans.single { it.kind == InlineKind.Speech }
        assertEquals("\"hi\"", text.substring(speech.start, speech.end))
    }
}
