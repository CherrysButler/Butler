package com.cherry.butler.core.data

import com.cherry.butler.core.data.local.MessageEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class VariantsTest {

    private var clock = 0L

    private fun row(id: Long, isBot: Boolean, isMain: Boolean = true, text: String = "x") = MessageEntity(
        localId = id, serverId = id * 10, chatId = 1, isBot = isBot, isMain = isMain, text = text,
        createdAt = clock++, rating = null, personaId = null, generationRequestIds = emptyList(),
        thinking = null, streamState = null, cachedAt = 0,
    )

    @Test
    fun `the newest main variant is the chosen one`() {
        // As the server leaves it after two selections: both variants main.
        assertEquals(3L, Variants.pick(listOf(row(2, true), row(3, true))).localId)
        assertEquals(2L, Variants.pick(listOf(row(2, true), row(3, true, isMain = false))).localId)
    }

    @Test
    fun `history carries one reply per turn`() {
        val rows = listOf(row(1, false), row(2, true), row(3, true), row(4, false), row(5, true, isMain = false))
        assertEquals(listOf(1L, 3L, 4L, 5L), Variants.collapse(rows).map { it.localId })
        assertEquals(listOf(1L, 2L, 4L, 5L), Variants.collapse(rows, prefer = 2).map { it.localId })
    }
}
