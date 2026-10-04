package com.cherry.butler.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholdersTest {

    @Test
    fun `fills both names in any casing`() {
        assertEquals(
            "Rain looked at Eva. Eva smiled at Rain.",
            "{{user}} looked at {{char}}. {{Char}} smiled at {{ USER }}.".fillNames("Rain", "Eva"),
        )
    }

    @Test
    fun `missing persona reads as You`() {
        assertEquals("You wake up.", "{{user}} wake up.".fillNames(null, "Eva"))
    }

    @Test
    fun `missing character leaves the brace alone`() {
        assertEquals("{{char}} waits.", "{{char}} waits.".fillNames("Rain", null))
    }

    @Test
    fun `plain text is untouched`() {
        val text = "No braces here."
        assertEquals(text, text.fillNames("Rain", "Eva"))
    }

    @Test
    fun `marked names become a persona span in roleplay text, winning inside speech`() {
        val filled = "\"Hi {{user}},\" *{{char}} waves at {{user}}*".fillNames("Rain", "Eva", markUser = true)
        val (text, spans) = RpMarkdown.parseInline(filled)
        assertEquals("Hi Rain, Eva waves at Rain", text)
        val persona = spans.filter { it.kind == RpMarkdown.InlineKind.Persona }
        assertEquals(listOf("Rain", "Rain"), persona.map { text.substring(it.start, it.end) })
        // Persona spans come last so their colour overrides the speech tint they sit in.
        assertEquals(RpMarkdown.InlineKind.Persona, spans.last().kind)
    }

    @Test
    fun `marked names become a persona run in editor html`() {
        val html = "<p><strong>ABOUT {{USER}}:</strong> {{user}} is new.</p>".fillNames("Rain", "Eva", markUser = true)
        val p = RichHtml.parse(html).single() as RichHtml.Block.Paragraph
        assertEquals("ABOUT Rain: Rain is new.", p.text)
        val persona = p.runs.filter { it.style.persona }
        assertEquals(listOf("Rain", "Rain"), persona.map { p.text.substring(it.start, it.end) })
        assertEquals(true, persona.first().style.bold)
    }

    @Test
    fun `marks never leak into plain text`() {
        val filled = "{{user}} smiles".fillNames("Rain", null, markUser = true)
        assertEquals("Rain smiles", filled.stripPersonaMarks())
        assertEquals("Rain smiles", filled.plainPreview())
    }
}
