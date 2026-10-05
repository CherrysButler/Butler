package com.cherry.butler.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneTagsTest {

    @Test
    fun `strip removes known tags and nothing else`() {
        assertEquals("She runs. <b>x</b>", SceneTags.strip("<butter>She runs.</butter> <b>x</b>"))
    }

    @Test
    fun `the butter version joins its parts`() {
        val reply = "Rain falls. <butter>She grabs the key.</butter> The room is cold.\n\n" +
            "He waits. <butter>\"Run,\" she says.</butter>"
        assertEquals("She grabs the key.\n\n\"Run,\" she says.", SceneTags.only(reply, SceneTag.Butter))
        assertNull(SceneTags.only("no tags here", SceneTag.Butter))
    }

    @Test
    fun `the parser hides the tags and marks the butter`() {
        val blocks = RpMarkdown.parse("Rain falls. <butter>*She grabs the key.*</butter> Cold.")
        val p = blocks.single() as RpMarkdown.Block.Paragraph
        assertEquals("Rain falls. She grabs the key. Cold.", p.text)
        val butter = p.spans.single { it.kind == RpMarkdown.InlineKind.Butter }
        assertEquals("She grabs the key.", p.text.substring(butter.start, butter.end))
        val action = p.spans.single { it.kind == RpMarkdown.InlineKind.Action }
        assertEquals("She grabs the key.", p.text.substring(action.start, action.end))
    }

    @Test
    fun `a tag left open runs into the next paragraph`() {
        val blocks = RpMarkdown.parse("<butter>One.\n\nTwo.</butter> Three.")
        val second = blocks[1] as RpMarkdown.Block.Paragraph
        val butter = second.spans.single { it.kind == RpMarkdown.InlineKind.Butter }
        assertEquals("Two.", second.text.substring(butter.start, butter.end))
    }

    @Test
    fun `a tag half-streamed at the end never shows`() {
        val blocks = RpMarkdown.parse("She turns. </butt")
        assertEquals("She turns.", (blocks.single() as RpMarkdown.Block.Paragraph).text)
        assertTrue(SceneTags.hasAny("<butter>x</butter>"))
        assertFalse(SceneTags.hasAny("plain"))
    }
}
