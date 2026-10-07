package com.cherry.butler.core.generation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgentLoopTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an exact edit is placed where the text is`() {
        val (out, applied, missed) = AgentLoop.apply("She smiled. *He nods.* \"Fine,\" she says.", listOf("*He nods.*" to "*He shrugs.*"))
        assertEquals("She smiled. *He shrugs.* \"Fine,\" she says.", out)
        assertEquals(1, applied)
        assertEquals(0, missed)
    }

    @Test
    fun `an edit whose whitespace differs still lands`() {
        val text = "She smiled.\n\n*He nods slowly,* then speaks."
        val (out, applied, missed) = AgentLoop.apply(text, listOf("*He nods   slowly,* then" to "*He nods,* then"))
        assertEquals("She smiled.\n\n*He nods,* then speaks.", out)
        assertEquals(1, applied)
        assertEquals(0, missed)
    }

    @Test
    fun `an edit that isn't in the text is counted, not forced`() {
        val (out, applied, missed) = AgentLoop.apply("Plain text.", listOf("not here" to "x", "Plain" to "Simple"))
        assertEquals("Simple text.", out)
        assertEquals(1, applied)
        assertEquals(1, missed)
    }

    @Test
    fun `an empty replacement deletes`() {
        val (out, _, _) = AgentLoop.apply("One. Two. Three.", listOf(" Two." to ""))
        assertEquals("One. Three.", out)
    }

    @Test
    fun `the verdict is found inside reasoning and fences`() {
        val answer = "<think>let me see</think>Sure, here is the check:\n```json\n{\"verdict\":\"fix\",\"problems\":[\"spoke for the user\"],\"edits\":[],\"rewrite\":null}\n```"
        val obj = AgentLoop.jsonIn(answer, json)
        assertNotNull(obj)
        assertEquals("fix", obj!!["verdict"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `no json means no verdict`() {
        assertNull(AgentLoop.jsonIn("Looks fine to me.", json))
    }
}
