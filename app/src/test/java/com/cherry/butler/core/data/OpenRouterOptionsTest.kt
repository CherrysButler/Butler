package com.cherry.butler.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterOptionsTest {

    private val payload = buildJsonObject {
        put("model", JsonPrimitive("moonshotai/kimi-k2.6"))
        put("stream", JsonPrimitive(true))
    }

    @Test
    fun emptyOptionsLeaveThePayloadAlone() {
        assertSame(payload, OpenRouterOptions().applyTo(payload))
    }

    @Test
    fun presetReplacesTheModel() {
        val out = OpenRouterOptions(preset = " @preset/roleplay ").applyTo(payload)
        assertEquals(JsonPrimitive("@preset/roleplay"), out["model"])
        assertFalse("provider" in out)
        assertEquals(JsonPrimitive(true), out["stream"])
    }

    @Test
    fun routingBecomesAProviderObject() {
        val out = OpenRouterOptions(
            providers = listOf("deepinfra", "together"),
            allowFallbacks = false,
            prefer = OpenRouterOptions.Prefer.Throughput,
        ).applyTo(payload)
        val provider = out["provider"]!!.jsonObject
        assertEquals(JsonArray(listOf(JsonPrimitive("deepinfra"), JsonPrimitive("together"))), provider["order"])
        assertEquals(JsonPrimitive(false), provider["allow_fallbacks"])
        assertEquals(JsonPrimitive("throughput"), provider["sort"])
        assertEquals(JsonPrimitive("moonshotai/kimi-k2.6"), out["model"])
    }

    @Test
    fun onlyOpenRouterHostsCount() {
        assertTrue(OpenRouterOptions.isOpenRouter("https://openrouter.ai/api/v1/chat/completions"))
        assertFalse(OpenRouterOptions.isOpenRouter("https://llm.chutes.ai/v1/chat/completions"))
        assertFalse(OpenRouterOptions.isOpenRouter("https://evil.example/openrouter.ai"))
    }

    @Test
    fun providerListIsTidied() {
        assertEquals(listOf("deepinfra", "together"), OpenRouterOptions.parseProviders(" DeepInfra, together ,,deepinfra"))
    }
}
