package com.cherry.butler.core.generation

import com.cherry.butler.core.data.Lesson
import com.cherry.butler.core.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsPromptTest {
    private val corrections = listOf(
        LessonsLearner.Correction("stop speaking for me", "Rain smiled and said yes.", "Ash", "c1", 1L),
    )

    @Test
    fun `notes are read through thinking and words around the json`() {
        val answer = """<think>The player hates being spoken for.</think>
            Sure! Here you go:
            {"notes":[{"text":"Never write the player's words or actions.","character":null},{"text":"Ash never swears.","character":"Ash"}]}
            Hope that helps."""
        val rules = LessonsLearner.Prompt.rules(answer, emptyList(), corrections)
        assertEquals(listOf("Never write the player's words or actions.", "Ash never swears."), rules.map { it.text })
        assertEquals(listOf(null, "c1"), rules.map { it.characterId })
    }

    @Test
    fun `a note the player switched off stays off`() {
        val off = Lesson(text = "Keep replies short.", on = false)
        val rules = LessonsLearner.Prompt.rules("""{"notes":[{"text":"Keep replies short.","character":null}]}""", listOf(off), emptyList())
        assertFalse(rules.single().on)
    }

    @Test(expected = ApiError.Api::class)
    fun `an answer with no notes in it says so`() {
        LessonsLearner.Prompt.rules("I can't help with that.", emptyList(), corrections)
    }

    @Test
    fun `the material marks replies as context and shows the focus`() {
        val m = LessonsLearner.Prompt.material(listOf(Lesson(text = "No time skips.")), corrections, "pacing")
        assertTrue(m.contains("- No time skips."))
        assertTrue(m.contains("pacing"))
        assertTrue(m.contains("for context only, never learn from it"))
        assertTrue(m.contains("The player said: stop speaking for me"))
    }

    @Test
    fun `notes come through a code fence, trailing commas, an unclosed think and braces after`() {
        val answer = "reasoning without an opening tag</think>\n```json\n{\"notes\": [ {\"text\": \"No time skips.\", \"character\": null}, ],}\n```\nNote: {braces} here."
        assertEquals(listOf("No time skips."), LessonsLearner.Prompt.rules(answer, emptyList(), corrections).map { it.text })
    }

    @Test
    fun `a plain list is read when there's no json`() {
        val rules = LessonsLearner.Prompt.rules("Here are the notes:\n- Never speak for the player.\n- Keep replies under three paragraphs.", emptyList(), corrections)
        assertEquals(listOf("Never speak for the player.", "Keep replies under three paragraphs."), rules.map { it.text })
    }

    @Test
    fun `a small model's broken json still gives its notes`() {
        // Llama 3.2 1B's real answer, 2026-11-04: a key twice, a list and an object never closed.
        val answer = """{"notes":[{"text":"Use more descriptive language.","text":"Never use 'I'm going to do this',","character":"Player"}"""
        val rules = LessonsLearner.Prompt.rules(answer, emptyList(), corrections)
        assertEquals(listOf("Use more descriptive language.", "Never use 'I'm going to do this'"), rules.map { it.text })
        assertEquals(listOf<String?>(null, null), rules.map { it.characterId })
    }
}

