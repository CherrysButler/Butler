package com.cherry.butler.core.markdown

import com.cherry.butler.core.markdown.RichHtml.Align
import com.cherry.butler.core.markdown.RichHtml.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichHtmlTest {

    @Test
    fun `paragraphs with coloured spans and breaks`() {
        val blocks = RichHtml.parse(
            """<p><span style="color: rgb(255, 57, 57);">You</span> were first.</p><p style="text-align: center;"><strong>Series #20</strong><br><em>note</em></p>""",
        )
        assertEquals(2, blocks.size)
        val first = blocks[0] as Block.Paragraph
        assertEquals("You were first.", first.text)
        assertEquals(1, first.runs.size)
        assertEquals(0xFFFF3939L, first.runs[0].style.color)
        assertEquals(0..3, first.runs[0].start..first.runs[0].end)
        val second = blocks[1] as Block.Paragraph
        assertEquals("Series #20\nnote", second.text)
        assertEquals(Align.Center, second.align)
        assertTrue(second.runs.any { it.style.bold && it.start == 0 && it.end == 10 })
        assertTrue(second.runs.any { it.style.italic && it.start == 11 && it.end == 15 })
    }

    @Test
    fun `images, rules and headings become blocks`() {
        val blocks = RichHtml.parse(
            """<p style="text-align: center;"><img src="https://ella.janitorai.com/x.webp" style="width: 100%; height: auto;"></p><hr><h2>Premise</h2><p>Body</p>""",
        )
        val image = blocks[0] as Block.Image
        assertEquals("https://ella.janitorai.com/x.webp", image.url)
        assertEquals(1f, image.widthFraction)
        assertEquals(Align.Center, image.align)
        assertEquals(Block.Rule, blocks[1])
        assertEquals(2, (blocks[2] as Block.Paragraph).heading)
        assertEquals("Body", (blocks[3] as Block.Paragraph).text)
    }

    @Test
    fun `lists, links, marks and entities`() {
        val blocks = RichHtml.parse(
            """<ul><li>One &amp; two</li><li><a href="https://janitorai.com/x">link</a></li></ul><p><mark style="background-color: #ff0">hi</mark> &nbsp;there</p>""",
        )
        val a = blocks[0] as Block.ListItem
        assertEquals("One & two", a.text)
        assertFalse(a.ordered)
        assertEquals(1, a.index)
        val b = blocks[1] as Block.ListItem
        assertEquals("https://janitorai.com/x", b.runs.single().style.href)
        val p = blocks[2] as Block.Paragraph
        assertEquals("hi there", p.text)
        assertEquals(0xFFFFFF00L, p.runs.single().style.background)
    }

    @Test
    fun `nested inline styles close at a block boundary and reopen`() {
        val blocks = RichHtml.parse("""<span style="color: #abcdef"><p>a</p><p>b</p></span>""")
        assertEquals(2, blocks.size)
        for (block in blocks) {
            val p = block as Block.Paragraph
            assertEquals(0xFFABCDEFL, p.runs.single().style.color)
        }
    }

    @Test
    fun `plain text is not html and strips cleanly`() {
        assertFalse(RichHtml.looksLikeHtml("She said *hi* <3 and left."))
        assertTrue(RichHtml.looksLikeHtml("<p>Hi</p>"))
        assertEquals("Hi there now", RichHtml.toPlainText("<p>Hi<br>there</p><p><b>now</b></p>"))
    }

    @Test
    fun `janitor stylesheet classes become roles`() {
        val blocks = RichHtml.parse(
            """<p class="_dividerLine_45b6m_27" style="text-align: center;"><br></p><p class="_appModalBadge_xxqvx_134" style="text-align: center;">About:</p><p class="_characterInfoMarkdownContent_1yi4w_5" style="text-align: center;">Fact</p><p class="_confirm_10on1_74 _characterInfoShinyBox_1yi4w_265">Shiny</p><p class="is-empty"><br></p><p>Plain</p>""",
        )
        assertEquals(Block.Rule, blocks[0])
        assertEquals("appModalBadge", (blocks[1] as Block.Paragraph).role)
        assertEquals("characterInfoMarkdownContent", (blocks[2] as Block.Paragraph).role)
        assertEquals("confirm", (blocks[3] as Block.Paragraph).role)
        assertEquals(null, (blocks[4] as Block.Paragraph).role)
        assertEquals("Plain", (blocks[4] as Block.Paragraph).text)
    }

    @Test
    fun `image width from the plain attribute`() {
        val blocks = RichHtml.parse("""<p style="text-align: center;">Look <img src="https://ella.janitorai.com/x.webp?width=600" width="200"></p>""")
        val image = blocks.filterIsInstance<Block.Image>().single()
        assertEquals(0.5f, image.widthFraction)
        assertEquals(RichHtml.Align.Center, image.align)
    }
}
