package com.cherry.butler.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CharacterReportTest {
    @Test
    fun `the reasons are the four Janitor knows, to the letter`() {
        assertEquals(listOf("spam", "stolen", "illegal", "other"), ReportReason.entries.map { it.wire })
    }

    @Test
    fun `a report is what the website sends`() {
        val body = CharacterReport.body("98d6771e-a31b-4000-bba4-76d69c66e070", "Character Friends & Family", ReportReason.Spam, "  not allowed  ")
        assertEquals(
            """{"character_id":"98d6771e-a31b-4000-bba4-76d69c66e070","other":"not allowed","reason":"spam","url":"https://janitorai.com/characters/98d6771e-a31b-4000-bba4-76d69c66e070_character-character-friends-family"}""",
            body.toString(),
        )
    }

    @Test
    fun `a name with nothing to make a slug of leaves the bare address`() {
        assertEquals("https://janitorai.com/characters/abc", CharacterReport.pageUrl("abc", "☆☆"))
    }
}
