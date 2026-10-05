package com.cherry.butler.core.generation

import com.cherry.butler.core.markdown.SceneTags
import kotlin.test.Test
import kotlin.test.assertEquals

class MoodTaggerTest {

    private val reply = "*The rain keeps falling.* \"Stay with me,\" she whispers. *A blade glints in the dark.* He laughs it off."

    @Test
    fun `picks are read from mood lines, unknown or unasked moods dropped`() {
        val answer = "romantic: \"Stay with me,\" she whispers.\ndangerous: *A blade glints in the dark.*\nangry: He laughs it off.\nfunny: He laughs it off."
        val picks = MoodTagger.parse(answer, setOf(Mood.Romantic, Mood.Dangerous))
        assertEquals(listOf(Mood.Romantic, Mood.Dangerous), picks.map { it.first })
    }

    @Test
    fun `tags wrap the sentences where they stand, and the words never change`() {
        val picks = listOf(
            Mood.Romantic to "\"Stay with me,\" she whispers.",
            Mood.Dangerous to "A blade glints in the dark.",
        )
        val tagged = MoodTagger.apply(reply, picks)
        assertEquals(reply, SceneTags.strip(tagged))
        assertEquals(
            "*The rain keeps falling.* <romantic>\"Stay with me,\" she whispers.</romantic> *<dangerous>A blade glints in the dark.</dangerous>* He laughs it off.",
            tagged,
        )
    }

    @Test
    fun `a sentence that isn't in the reply is skipped`() {
        val tagged = MoodTagger.apply(reply, listOf(Mood.Sad to "She cries alone."))
        assertEquals(reply, tagged)
    }

    @Test
    fun `NONE adds nothing`() {
        assertEquals(emptyList(), MoodTagger.parse("NONE", Mood.entries.toSet()))
    }
}
