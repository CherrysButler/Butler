package com.cherry.butler.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JanitorLinkTest {
    private val id = "3f0c1b2a-9d8e-4f7a-b6c5-1a2b3c4d5e6f"

    @Test
    fun `a character link with its slug opens the character`() {
        assertEquals(JanitorLink.Character(id), JanitorLink.parse("https://janitorai.com/characters/${id}_character-ash-the-night-clerk"))
    }

    @Test
    fun `bare, www and uppercase character links open the character`() {
        assertEquals(JanitorLink.Character(id), JanitorLink.parse("https://janitorai.com/characters/$id"))
        assertEquals(JanitorLink.Character(id), JanitorLink.parse("https://www.janitorai.com/characters/$id?ref=bio#top"))
        assertEquals(JanitorLink.Character(id), JanitorLink.parse("janitorai.com/characters/${id.uppercase()}"))
        assertEquals(JanitorLink.Character(id), JanitorLink.parse("  http://janitorai.com/characters/$id/  "))
    }

    @Test
    fun `a profile link opens the creator`() {
        assertEquals(JanitorLink.Creator(id), JanitorLink.parse("https://janitorai.com/profiles/${id}_profile-of-cherry"))
    }

    @Test
    fun `other links go to the browser`() {
        assertNull(JanitorLink.parse("https://janitorai.com/search?tags=fantasy"))
        assertNull(JanitorLink.parse("https://janitorai.com/characters/not-an-id"))
        assertNull(JanitorLink.parse("https://notjanitorai.com/characters/$id"))
        assertNull(JanitorLink.parse("https://janitorai.com.evil.example/characters/$id"))
        assertNull(JanitorLink.parse("https://discord.gg/abc"))
    }
}
