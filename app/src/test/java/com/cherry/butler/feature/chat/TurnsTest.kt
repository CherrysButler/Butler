package com.cherry.butler.feature.chat

import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.local.MessageStreamState
import com.cherry.butler.core.data.local.SendJobEntity
import com.cherry.butler.core.data.local.SendJobState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TurnsTest {

    private var clock = 1_000L

    private fun row(
        localId: Long,
        isBot: Boolean,
        text: String = "x",
        isMain: Boolean = true,
        serverId: Long? = localId * 10,
        stream: String? = null,
    ) = MessageEntity(
        localId = localId, serverId = serverId, chatId = 1, isBot = isBot, isMain = isMain, text = text,
        createdAt = clock++, rating = null, personaId = null, generationRequestIds = emptyList(),
        thinking = null, streamState = stream, cachedAt = 0,
    )

    private fun job(id: Long, bot: Long, user: Long?, state: String) = SendJobEntity(
        id = id, chatId = 1, characterId = "c", userMessageLocalId = user, botMessageLocalId = bot,
        mode = "ALTERNATIVE", state = state, resumeState = null, attempt = 0, nextAttemptAt = 0,
        lastError = null, lastErrorRetryable = false, generationRequestId = null, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `consecutive bot rows are one turn showing the main variant`() {
        val turns = foldTurns(
            listOf(row(1, false), row(2, true, isMain = false), row(3, true, isMain = true), row(4, true, isMain = false)),
            jobs = emptyList(), liveIds = emptySet(),
        )
        assertEquals(2, turns.size)
        val bot = assertIs<Turn.Bot>(turns[1])
        assertEquals(listOf(2L, 3L, 4L), bot.variants.map { it.localId })
        assertEquals(3L, bot.shown.localId)
        assertEquals(1, bot.index)
    }

    @Test
    fun `a turn with no main variant shows its newest reply`() {
        // The Eva chat: the official client posted the reply but never marked it main.
        val turns = foldTurns(listOf(row(1, false), row(2, true, isMain = false)), emptyList(), emptySet())
        assertEquals(2L, (turns[1] as Turn.Bot).shown.localId)
    }

    @Test
    fun `the variant being written wins, and a failed empty attempt is not a variant`() {
        val messages = listOf(
            row(1, false),
            row(2, true, isMain = true),
            row(3, true, text = "", isMain = false, serverId = null, stream = MessageStreamState.STREAMING),
        )
        val writing = foldTurns(messages, listOf(job(9, bot = 3, user = null, state = SendJobState.GENERATING)), setOf(3L))
        assertEquals(3L, (writing[1] as Turn.Bot).shown.localId)

        val failed = foldTurns(messages, listOf(job(9, bot = 3, user = null, state = SendJobState.FAILED)), emptySet())
        val bot = failed[1] as Turn.Bot
        assertEquals(listOf(2L), bot.variants.map { it.localId })
        assertEquals(2L, bot.shown.localId)
        assertNotNull(bot.job)
    }

    @Test
    fun `a greeting with no user line before it cannot be swiped`() {
        val turns = foldTurns(listOf(row(1, true)), emptyList(), emptySet())
        assertEquals(false, (turns[0] as Turn.Bot).answersUser)
    }

    @Test
    fun `a group of only failed empty attempts is no turn at all`() {
        val turns = foldTurns(
            listOf(row(1, false), row(2, true, text = "", isMain = false, serverId = null, stream = MessageStreamState.STREAMING)),
            jobs = listOf(job(9, bot = 2, user = 1, state = SendJobState.FAILED)),
            liveIds = emptySet(),
        )
        assertEquals(1, turns.size)
        assertIs<Turn.User>(turns[0])
    }
}
