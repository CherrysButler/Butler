package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompactionTest {
    private fun line(id: Long?, bot: Boolean = id?.rem(2) == 0L) = MessageEntity(
        serverId = id, chatId = 1, isBot = bot, isMain = true, text = "line $id", createdAt = id ?: 0,
        rating = null, personaId = null, generationRequestIds = emptyList(), thinking = null,
        streamState = null, cachedAt = 0,
    )

    private fun chat(n: Int) = (1L..n).map { line(it * 10) }

    @Test
    fun `the first compact folds everything but the kept window`() {
        val plan = assertNotNull(Compaction.plan(chat(20), keep = 12, coveredUpTo = null))
        assertEquals((1L..8).map { it * 10 }, plan.fold.map { it.serverId })
        assertEquals(80L, plan.upTo)
    }

    @Test
    fun `a later compact folds only what moved past the previous one`() {
        // The last compact covered up to message 80; six more have moved out of the window since.
        val plan = assertNotNull(Compaction.plan(chat(26), keep = 12, coveredUpTo = 80))
        assertEquals(listOf(90L, 100L, 110L, 120L, 130L, 140L), plan.fold.map { it.serverId })
        assertEquals(140L, plan.upTo)
    }

    @Test
    fun `too little to fold is no compact`() {
        assertNull(Compaction.plan(chat(12), keep = 12, coveredUpTo = null))
        assertNull(Compaction.plan(chat(14), keep = 12, coveredUpTo = null))
        // Two past the previous compact: below the minimum.
        assertNull(Compaction.plan(chat(22), keep = 12, coveredUpTo = 80))
        assertNotNull(Compaction.plan(chat(22), keep = 12, coveredUpTo = 80, minFold = 2))
    }

    @Test
    fun `a summary that already covers the whole chat leaves nothing to fold`() {
        assertNull(Compaction.plan(chat(30), keep = 12, coveredUpTo = 300))
    }

    @Test
    fun `lines not on the server are neither folded nor counted`() {
        val history = chat(16) + line(null, bot = true)
        val plan = assertNotNull(Compaction.plan(history, keep = 12, coveredUpTo = null))
        assertEquals(listOf(10L, 20L, 30L, 40L), plan.fold.map { it.serverId })
    }

    @Test
    fun `at least one message is always kept`() {
        val plan = assertNotNull(Compaction.plan(chat(6), keep = 0, coveredUpTo = null))
        assertEquals(50L, plan.upTo)
    }

    @Test
    fun `the prompt builds on the previous memory when there is one`() {
        val prompt = Compaction.prompt("CHARACTERS\n  Mia: a knight.")
        assertTrue(prompt.startsWith("[OOC:"))
        assertTrue(prompt.endsWith("]"))
        assertTrue("EARLIER MEMORY" in prompt)
        assertTrue("<memory>\nCHARACTERS\n  Mia: a knight.\n</memory>" in prompt)
        for (kept in listOf("name", "relationships", "threads", "current scene")) assertTrue(kept in prompt.lowercase(), kept)
    }

    @Test
    fun `the first prompt starts from the story itself`() {
        for (none in listOf(null, "", "   ")) {
            val prompt = Compaction.prompt(none)
            assertFalse("EARLIER MEMORY" in prompt)
            assertTrue("from the start of the story" in prompt)
        }
    }

    @Test
    fun `an echoed memory wrapper is taken off`() {
        assertEquals("Notes", Compaction.clean("  <memory>\nNotes\n</memory> "))
        assertEquals("Plain notes", Compaction.clean("\nPlain notes\n"))
    }
}
