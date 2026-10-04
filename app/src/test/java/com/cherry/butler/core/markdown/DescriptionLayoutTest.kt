package com.cherry.butler.core.markdown

import com.cherry.butler.core.markdown.DescriptionLayout.Doc
import com.cherry.butler.core.markdown.RpMarkdown.InlineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DescriptionLayoutTest {

    private val sample = """
        _"Toughen up, baby bro."_

        Or, you're the youngest brother in a house. Protect your sister.



        About the Characters:



        Your parents are never around, so it's your **four siblings** the ones you see the most.
        **Sidney** is the **eldest**, the one that acts as a _father_ as much as a brother.
        They all love you.

        _You should take care of your sister._

        About User:

        You're the **youngest brother**, but despite being the baby of the family.
    """.trimIndent()

    @Test
    fun `prose before the first head, labels and panels after`() {
        val doc = DescriptionLayout.layout(RpMarkdown.parse(sample))
        assertIs<Doc.Prose>(doc[0])
        assertIs<Doc.Prose>(doc[1])
        assertEquals(Doc.Label("About the Characters"), doc[2])
        val panels = doc.drop(3).takeWhile { it is Doc.Panel }.map { it as Doc.Panel }
        assertEquals(4, panels.size)
        assertEquals("Your parents are never around, so it's your four siblings the ones you see the most.", panels[0].text)
        assertTrue(panels[0].spans.any { it.kind == InlineKind.Strong && panels[0].text.substring(it.start, it.end) == "four siblings" })
        assertTrue(panels[1].spans.any { it.kind == InlineKind.Action && panels[1].text.substring(it.start, it.end) == "father" })
        assertFalse(panels[0].note)
        assertTrue(panels[3].note)
        assertEquals("You should take care of your sister.", panels[3].text)
        assertEquals(Doc.Label("About User"), doc[7])
        assertIs<Doc.Panel>(doc[8])
    }

    @Test
    fun `a sentence ending in a colon is not a head`() {
        val doc = DescriptionLayout.layout(RpMarkdown.parse("He said this, and it was long. Then he added:"))
        assertIs<Doc.Prose>(doc.single())
    }
}
