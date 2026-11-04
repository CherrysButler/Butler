package com.cherry.butler.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonsTest {

    @Test
    fun `the file reads back as it was written`() {
        val lessons = listOf(
            Lesson(text = "Never write my character's actions.", on = true),
            Lesson(text = "Keep replies under three paragraphs.", on = false),
            Lesson(text = "Ash never swears.", characterId = "c1", characterName = "Ash"),
        )
        val back = LessonsFile.parse(LessonsFile.write(lessons))
        assertEquals(lessons.map { Triple(it.text, it.characterId, it.on) }, back.map { Triple(it.text, it.characterId, it.on) })
        assertEquals("Ash", back.last().characterName)
    }

    @Test
    fun `ooc notes in every usual form are found, in order, and the story isn't`() {
        val line = "*I step back.* (OOC: stop describing my feelings) \"Fine.\" ((keep it shorter)) [ooc - no time skips]"
        assertEquals(listOf("stop describing my feelings", "keep it shorter", "no time skips"), OocNotes.from(line))
        assertEquals(listOf("don't speak for me"), OocNotes.from("Hello there.\nOOC: don't speak for me"))
        assertTrue(OocNotes.from("*She laughs (quietly) and nods.*").isEmpty())
    }

    @Test
    fun `a rewritten rule is a change, a new one an addition, a missing one a drop`() {
        val keep = Lesson(id = "a", text = "Never speak for me.")
        val reword = Lesson(id = "b", text = "Keep replies short, two paragraphs.")
        val drop = Lesson(id = "c", text = "Use lots of emojis.")
        val proposed = listOf(
            Lesson(text = "Never speak for me."),
            Lesson(text = "Keep replies short, under two paragraphs please."),
            Lesson(text = "No time skips."),
        )
        val changes = LessonDiff.between(listOf(keep, reword, drop), proposed)
        assertEquals(listOf(LessonChange.Kind.Changed, LessonChange.Kind.Added, LessonChange.Kind.Dropped), changes.map { it.kind })
        val applied = LessonDiff.applied(listOf(keep, reword, drop), changes)
        assertEquals(listOf("Never speak for me.", "Keep replies short, under two paragraphs please.", "No time skips."), applied.map { it.text })
        assertEquals("b", applied[1].id)
    }

    @Test
    fun `a change left unticked keeps the rule as it was`() {
        val old = Lesson(id = "a", text = "Use lots of emojis.")
        val changes = LessonDiff.between(listOf(old), emptyList())
        assertEquals(listOf(old), LessonDiff.applied(listOf(old), changes.filter { it.kind != LessonChange.Kind.Dropped }))
    }
}
