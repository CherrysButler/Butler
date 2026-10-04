package com.cherry.butler.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewTest {

    @Test
    fun `strips headings, emphasis and escapes`() {
        assertEquals(
            "You live with your alcoholic parents. Luckily, your uncle helps.",
            "## **_You live with your alcoholic parents._** Luckily, your \\*uncle\\* helps.".plainPreview(),
        )
    }

    @Test
    fun `collapses whitespace and quotes`() {
        assertEquals("Line one Line two", "> Line one\n\n>   Line   two  ".plainPreview())
    }

    @Test
    fun `cuts to the limit before cleaning`() {
        val long = "a".repeat(500)
        assertEquals(240, long.plainPreview().length)
    }

    @Test
    fun `plain text passes through`() {
        assertEquals("Just a sentence.", "Just a sentence.".plainPreview())
    }
}
