package com.cherry.butler.core.generation

import com.cherry.butler.core.generation.SuggestionService.Companion.endOnSentence
import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestionServiceTest {

    @Test
    fun `a finished line is left alone`() {
        assertEquals("*I kneel.* \"I will.\"", endOnSentence("*I kneel.* \"I will.\""))
        assertEquals("*I kneel beside him.*", endOnSentence("*I kneel beside him.*"))
    }

    @Test
    fun `a line cut mid-sentence goes back to its last whole sentence`() {
        assertEquals("*I kneel beside him.*", endOnSentence("*I kneel beside him. I press my pa"))
        assertEquals("\"Stay back.\"", endOnSentence("\"Stay back.\" *I reach for the"))
    }

    @Test
    fun `an open quote or asterisk is closed`() {
        assertEquals("\"Stay back, please.\"", endOnSentence("\"Stay back, please. If you"))
    }

    @Test
    fun `text with no sentence end at all comes back as it was`() {
        assertEquals("*hugs her", endOnSentence("*hugs her"))
    }
}
