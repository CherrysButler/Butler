package com.cherry.butler.core.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioNotesTest {

    private val card = NoteCard(name = "Mara", avatar = "a.webp", isNsfw = true, totalTokens = 412, creatorName = "Cherrycigs")

    @Test
    fun `a note retires once Janitor's card shows what was saved`() {
        val janitor = card.toCharacter("c1")
        assertTrue(card.matches(janitor))
        // The index still has the old version: the note keeps showing the new one.
        assertFalse(card.matches(janitor.copy(name = "Mara (old)")))
        assertFalse(card.matches(janitor.copy(totalTokens = 300)))
        assertFalse(card.matches(janitor.copy(avatar = "old.webp")))
    }

    @Test
    fun `a note lives ten minutes at most`() {
        val now = 1_000_000_000L
        assertTrue(StudioNote("c1", at = now - 60_000).live(now))
        assertFalse(StudioNote("c1", at = now - StudioNote.TTL_MS - 1).live(now))
    }
}
