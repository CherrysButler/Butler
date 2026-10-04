package com.cherry.butler.core.data.remote

import com.cherry.butler.core.data.remote.dto.ApiSettingsDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiSettingsDtoTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // The shape GET /mb/api-settings returned on 2026-10-03, keys replaced by fakes.
    private val body = """
        {"legacy_config":{"proxyConfigurations":[{"apiKey":"sk-legacy-SECRET"}]},
         "materialized":true,
         "prompts":[{"id":"p1","name":"Lorebarry","kind":"system","content":"<A>","created_at":"x"}],
         "proxy_configs":[
           {"id":"b","name":"Chutes","api_url":"https://llm.chutes.ai/v1/chat/completions","api_key":"","model":"Qwen","position":1,"prompt":null},
           {"id":"a","name":"Gemma","api_url":"https://openrouter.ai/api/v1/chat/completions","api_key":"sk-or-SECRET","model":"kimi","position":0,
            "prompt":{"id":"p1","name":"Lorebarry","kind":"system","content":"<A>"}}],
         "settings":{"source":"proxy","router_enabled":false,"selected_proxy_config_id":"a",
                     "generation_settings":{"top_k":3,"temperature":0.6}}}
    """.trimIndent()

    @Test
    fun `keys are reduced to presence and never kept`() {
        val dto = json.decodeFromString(ApiSettingsDto.serializer(), body)
        val gemma = dto.proxyConfigs.first { it.id == "a" }
        val chutes = dto.proxyConfigs.first { it.id == "b" }
        assertTrue(gemma.hasKey)
        assertFalse(chutes.hasKey)
        assertFalse("SECRET" in dto.toString(), "a decoded settings object must not hold a key")
        assertEquals("Lorebarry", gemma.prompt?.name)
        assertEquals("a", dto.settings.selectedProxyConfigId)
        assertEquals("3", dto.settings.generationSettings["top_k"].toString())
    }

    @Test
    fun `a patch response without prompts is not an empty library`() {
        val patched = body.replace(Regex(""""prompts":\[[^\]]*\],"""), "")
        val dto = json.decodeFromString(ApiSettingsDto.serializer(), patched)
        assertEquals(null, dto.prompts)
    }
}
