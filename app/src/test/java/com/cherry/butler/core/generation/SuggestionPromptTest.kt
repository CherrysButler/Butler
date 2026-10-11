package com.cherry.butler.core.generation

import com.cherry.butler.core.data.Perspective
import com.cherry.butler.core.data.remote.dto.PronounsDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionPromptTest {

    private val she = PronounsDto("she", "her", "her", "hers", "herself")

    @Test
    fun `first person from scratch is the line Write for me always sent`() {
        assertEquals(
            "(Write my next line for me. First person as me, in the story's style, actions in asterisks " +
                "and speech in quotes. React to what just happened and do one clear thing. Two to four " +
                "short sentences, and finish every one: end on a complete sentence. Never write the other " +
                "character's words or reactions.)",
            SuggestionPrompt.line(draft = "", perspective = Perspective.First),
        )
    }

    @Test
    fun `no voice from scratch is first person`() {
        assertEquals(SuggestionPrompt.line("", Perspective.First), SuggestionPrompt.line("", null))
    }

    @Test
    fun `third person from scratch names the persona and uses her pronouns`() {
        val line = SuggestionPrompt.line("", Perspective.Third, name = "Mia", pronouns = she)
        assertTrue(line.startsWith("(Write my next line for me. Third person as me, Mia, with she/her pronouns, in the story's style"), line)
        assertFalse("First person" in line, line)
    }

    @Test
    fun `third person without pronouns goes by name and they-them`() {
        val line = SuggestionPrompt.line("", Perspective.Third, name = "Mia", pronouns = null)
        assertTrue("Third person as me, by my name, Mia, and they/them where a pronoun is needed" in line, line)
        // Blank pronouns are none.
        assertEquals(line, SuggestionPrompt.line("", Perspective.Third, name = "Mia", pronouns = PronounsDto(subjective = " ")))
    }

    @Test
    fun `third person with no name leaves the macro to be filled`() {
        assertTrue("by my name, {{user}}," in SuggestionPrompt.line("", Perspective.Third, name = " "))
    }

    @Test
    fun `second person from scratch says you`() {
        val line = SuggestionPrompt.line("", Perspective.Second)
        assertTrue(line.startsWith("(Write my next line for me. Second person as me, \"you\" for me, in the story's style"), line)
    }

    @Test
    fun `first person rewrite is the line Enhance always sent`() {
        assertEquals(
            "*I wave.*\n\n(Rewrite it as my own line, not a scene: first person as me, same tense, keep what I do " +
                "and say and add nothing new, just make it read well. About the same length as my draft, " +
                "a sentence or two longer at most, and finish every sentence: end on a complete one. Never " +
                "write the other character's reaction.)",
            SuggestionPrompt.line(draft = " *I wave.* ", perspective = Perspective.First),
        )
    }

    @Test
    fun `a rewrite with no voice keeps the draft's own`() {
        val line = SuggestionPrompt.line("*She waves.*", null)
        assertTrue("not a scene: the same person and tense as my draft, keep what I do" in line, line)
        assertFalse("first person" in line, line)
    }

    @Test
    fun `a rewrite can be turned to third or second person`() {
        assertTrue("not a scene: third person as me, Mia, with she/her pronouns, same tense," in SuggestionPrompt.line("*I wave.*", Perspective.Third, name = "Mia", pronouns = she))
        assertTrue("not a scene: second person as me, \"you\" for me, same tense," in SuggestionPrompt.line("*I wave.*", Perspective.Second))
    }

    @Test
    fun `guidance rides last, as a note for this line only`() {
        val line = SuggestionPrompt.line("", Perspective.First, guidance = "Longer, a full paragraph.")
        assertTrue(line.endsWith("\n\n(OOC: For this line only, and over anything said above: Longer, a full paragraph. Do not mention this note.)"), line)
        assertEquals(SuggestionPrompt.line("", Perspective.First), SuggestionPrompt.line("", Perspective.First, guidance = "  "))
    }

    @Test
    fun `picks become their instructions, then the user's own words`() {
        assertEquals(
            "One or two sentences at most. Make me shyer and more hesitant. Blush",
            SuggestionPrompt.guidance(listOf("Short", "Shyer"), " Blush. ", rewrite = false),
        )
        assertEquals("Only fix spelling, grammar and punctuation, and change nothing else", SuggestionPrompt.guidance(listOf("Fix grammar"), "", rewrite = true))
        assertEquals("", SuggestionPrompt.guidance(emptyList(), " ", rewrite = false))
    }

    @Test
    fun `picks stack, and a pick lets go of its opposite`() {
        var picked = SuggestionPrompt.toggle(emptyList(), "Short", rewrite = false)
        picked = SuggestionPrompt.toggle(picked, "Bolder", rewrite = false)
        assertEquals(listOf("Short", "Bolder"), picked)
        picked = SuggestionPrompt.toggle(picked, "Longer", rewrite = false)
        assertEquals(listOf("Bolder", "Longer"), picked)
        assertEquals(listOf("Longer"), SuggestionPrompt.toggle(picked, "Bolder", rewrite = false))
    }
}
