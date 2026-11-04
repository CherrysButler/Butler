package com.cherry.butler.core.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRefineTest {
    @Test
    fun `phrases the draft shares with recent replies are found, longest first and as written`() {
        val earlier = listOf(
            "*The air thick with tension, she leans closer.* \"You came back,\" she says, a mix of relief and anger in her voice.",
            "He laughs. \"Of course I did.\"",
        )
        val draft = "*She steps back, the air thick with tension again.* A mix of relief and anger flickers across her face. \"Of course.\""
        val found = Repeats.find(draft, earlier)
        assertEquals(listOf("the air thick with tension", "A mix of relief and anger"), found)
    }

    @Test
    fun `common words alone are not a repeat`() {
        assertTrue(Repeats.find("She smiles at you and then turns.", listOf("He smiles at you and then leaves.")).isEmpty())
    }

    @Test
    fun `edits land across curly quotes, dashes and ellipses`() {
        val draft = "“Wait—please,” she says… and stops."
        val r = AgentLoop.apply(draft, listOf("\"Wait-please,\" she says..." to "\"Wait,\" she whispers."))
        assertEquals(1, r.applied)
        assertEquals("\"Wait,\" she whispers. and stops.", r.text)
    }

    @Test
    fun `a check answer with trailing commas and words around it is read`() {
        val answer = "Here is my verdict:\n```json\n{\"verdict\":\"fix\",\"problems\":[\"repeats a phrase\",],\"edits\":[{\"find\":\"a\",\"replace\":\"b\"},],\"rewrite\":null}\n```\nDone {ok}."
        val obj = AgentLoop.jsonIn(answer, kotlinx.serialization.json.Json)
        assertEquals("\"fix\"", obj?.get("verdict").toString())
    }

    @Test
    fun `the hidden text is the latest whole reply, and steps don't show it`() {
        val thought = AgentSteps.open(AgentSteps.Kind.Draft) + "first" + AgentSteps.close(AgentSteps.Kind.Draft) +
            AgentSteps.text("first") + AgentSteps.open(AgentSteps.Kind.Fix) + "1 edit" + AgentSteps.close(AgentSteps.Kind.Fix) + AgentSteps.text("second")
        assertEquals("second", AgentSteps.latestText(thought))
        assertEquals(listOf("first", "1 edit"), AgentSteps.parse(thought)!!.map { it.detail })
    }
}
