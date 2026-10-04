package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GuidedRetryTest {
    private fun line(text: String, bot: Boolean) = MessageEntity(
        serverId = 1, chatId = 1, isBot = bot, isMain = true, text = text, createdAt = 0,
        rating = null, personaId = null, generationRequestIds = emptyList(), thinking = null,
        streamState = null, cachedAt = 0,
    )

    @Test
    fun `guidance rides on the last line only`() {
        val history = listOf(line("Hello", bot = true), line("Hi there", bot = false))
        val out = GuidedRetry.apply(history, " Be more enthusiastic! ")
        assertEquals("Hello", out[0].text)
        assertTrue(out[1].text.startsWith("Hi there\n\n(OOC: For your next reply only: Be more enthusiastic!"))
        assertEquals("Hi there", history[1].text)
    }

    @Test
    fun `no guidance leaves the history untouched`() {
        val history = listOf(line("Hi", bot = false))
        assertSame(history, GuidedRetry.apply(history, "   "))
        assertSame(history, GuidedRetry.apply(history, null))
    }
}
