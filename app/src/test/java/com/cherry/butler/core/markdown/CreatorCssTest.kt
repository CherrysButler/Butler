package com.cherry.butler.core.markdown

import com.cherry.butler.core.markdown.CreatorCss.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CreatorCssTest {

    // ShyLoL's bio, shortened: a <style> block aimed at Janitor's page container, then the text.
    private val shy = """
        <p></p><style>
        .css-1uodvt1 {
        padding-top: 0px;
        flex-direction: column;
        text-align: center;
        }
        </style><p><span style="color: rgb(168, 161, 234);">You can call me Shy</span></p>
        <p style="text-align: left;">Rules</p>
    """.trimIndent()

    @Test
    fun `a style block is never shown as text`() {
        val text = RichHtml.parse(shy).joinToString("\n") { (it as? RichHtml.Block.Paragraph)?.text.orEmpty() }
        assertFalse(text.contains("css-1uodvt1"))
        assertFalse(text.contains("padding-top"))
        assertFalse(RichHtml.toPlainText(shy).contains("flex-direction"))
    }

    @Test
    fun `a rule aimed at the page centres the text, and a paragraph's own alignment still wins`() {
        val paragraphs = RichHtml.parse(shy).filterIsInstance<RichHtml.Block.Paragraph>()
        assertEquals(RichHtml.Align.Center, paragraphs.first { it.text.startsWith("You can") }.align)
        assertEquals(RichHtml.Align.Start, paragraphs.first { it.text == "Rules" }.align)
    }

    @Test
    fun `a rule aimed at something inside the bio is not taken as the page's`() {
        val html = """<style>.note { text-align: center; color: #ff0000 }</style><p class="note">a</p><p>b</p>"""
        assertNull(RichHtml.pageLook(html).align)
    }

    @Test
    fun `profile css lands on the parts Butler draws`() {
        val css = """
            /* cards */
            .pp-cc-wrapper { background: linear-gradient(#1a1a2e, #000); border: 1px solid #ff00ff !important; border-radius: 16px; }
            .pp-cc-name, .profile-title-heading { color: rgb(255, 0, 0); }
            .pp-cc-wrapper:hover { background-color: #ffffff; }
            .pp-page-background { background-color: #010112; }
            .pp-fl-modal { color: #00ff00; }
        """.trimIndent()
        val looks = CreatorCss.parse(css)
        val card = looks.getValue(Part.Card)
        assertEquals(0xFF1A1A2EL, card.background)
        assertEquals(0xFFFF00FFL, card.border)
        assertEquals(16f, card.radius)
        assertEquals(0xFFFF0000L, looks.getValue(Part.CardName).color)
        assertEquals(0xFFFF0000L, looks.getValue(Part.Name).color)
        assertEquals(0xFF010112L, looks.getValue(Part.Page).background)
        // A hover state and a part Butler doesn't have change nothing.
        assertEquals(setOf(Part.Card, Part.CardName, Part.Name, Part.Page), looks.keys)
    }
}
