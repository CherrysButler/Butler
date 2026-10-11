package com.cherry.butler.core.generation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ProxyPayloadTest {
    private fun payload(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun lines(p: JsonObject) = p["messages"]!!.jsonArray.map {
        val m = it.jsonObject
        m["role"]!!.jsonPrimitive.content to m["content"]!!.jsonPrimitive.content
    }

    @Test
    fun `an alternating payload goes out as it came`() {
        val p = payload("""{"messages":[{"role":"system","content":"S"},{"role":"assistant","content":"Hi"},{"role":"user","content":"Yo"}],"transforms":["middle-out"]}""")
        assertSame(p, ProxyPayload.normalized(p))
    }

    @Test
    fun `two user lines in a row become one, with a blank line between`() {
        // Write for me after an unanswered user line.
        val p = payload("""{"messages":[{"role":"system","content":"S"},{"role":"assistant","content":"Hi"},{"role":"user","content":"I wave.  "},{"role":"user","content":"(Write my next line for me.)"}]}""")
        assertEquals(
            listOf("system" to "S", "assistant" to "Hi", "user" to "I wave.\n\n(Write my next line for me.)"),
            lines(ProxyPayload.normalized(p)),
        )
    }

    @Test
    fun `runs of assistant lines merge too, and longer runs merge whole`() {
        val p = payload("""{"messages":[{"role":"system","content":"S"},{"role":"assistant","content":"A"},{"role":"assistant","content":"B"},{"role":"user","content":"1"},{"role":"user","content":"2"},{"role":"user","content":"3"}]}""")
        assertEquals(listOf("system" to "S", "assistant" to "A\n\nB", "user" to "1\n\n2\n\n3"), lines(ProxyPayload.normalized(p)))
    }

    @Test
    fun `system lines are never merged or moved`() {
        val p = payload("""{"messages":[{"role":"system","content":"S1"},{"role":"system","content":"S2"},{"role":"user","content":"U"},{"role":"system","content":"S3"},{"role":"user","content":"V"}]}""")
        assertSame(p, ProxyPayload.normalized(p))
    }

    @Test
    fun `an empty line adds no blank line`() {
        val p = payload("""{"messages":[{"role":"user","content":""},{"role":"user","content":"Hello"}]}""")
        assertEquals(listOf("user" to "Hello"), lines(ProxyPayload.normalized(p)))
    }

    @Test
    fun `lines with parts or extra keys are left apart`() {
        val p = payload("""{"messages":[{"role":"user","content":[{"type":"text","text":"a"}]},{"role":"user","content":"b"},{"role":"user","content":"c","name":"Mia"}]}""")
        assertSame(p, ProxyPayload.normalized(p))
    }

    @Test
    fun `everything but the messages is kept`() {
        val p = payload("""{"model":"x","max_tokens":5000,"transforms":["middle-out"],"messages":[{"role":"user","content":"a"},{"role":"user","content":"b"}]}""")
        val out = ProxyPayload.normalized(p)
        assertEquals(p - "messages", out - "messages")
    }
}
