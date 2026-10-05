package com.cherry.butler.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterThinkingTest {

    private val payload = buildJsonObject { put("model", "moonshotai/kimi-k2.6") }

    @Test
    fun `the model's default sends no reasoning`() {
        val out = OpenRouterOptions().applyTo(payload)
        assertNull(out["reasoning"])
        assertTrue(OpenRouterOptions().isEmpty)
    }

    @Test
    fun `a level becomes reasoning effort`() {
        val out = OpenRouterOptions(thinking = OpenRouterOptions.Thinking.High).applyTo(payload)
        assertEquals("high", out["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertFalse(OpenRouterOptions(thinking = OpenRouterOptions.Thinking.Low).isEmpty)
    }

    @Test
    fun `Janitor's own reasoning keeps its other fields but loses the token budget`() {
        val withReasoning = JsonObject(
            payload + ("reasoning" to buildJsonObject { put("max_tokens", 4000); put("exclude", false); put("enabled", true) }),
        )
        val r = OpenRouterOptions(thinking = OpenRouterOptions.Thinking.Off).applyTo(withReasoning)["reasoning"]!!.jsonObject
        assertEquals(JsonPrimitive("none"), r["effort"])
        assertEquals(JsonPrimitive(false), r["exclude"])
        assertNull(r["max_tokens"])
        assertNull(r["enabled"])
    }
}
