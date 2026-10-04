package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.ApiSettingsDto
import com.cherry.butler.core.data.remote.dto.PromptDeletedDto
import com.cherry.butler.core.data.remote.dto.PromptDto
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI settings on `/mb`, every route verified 2026-10-03 (docs/JANITOR_API.md §27).
 *
 * Writes go through `/api-settings`; the server mirrors them into the profile's legacy
 * `config`, which is what the generation envelope is built from. Bodies here may carry
 * the user's proxy key on the way out and always carry it on the way back, so nothing
 * in this class logs a request or a response.
 *
 * Creates are not idempotent and are sent once, never retried blindly.
 */
@Singleton
class SettingsRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = "${JanitorConfig.BACKEND_BASE}/api-settings"
    private val promptBase = "${JanitorConfig.BACKEND_BASE}/prompt-library"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun decode(body: String) = json.decodeFromString(ApiSettingsDto.serializer(), body)

    /**
     * `GET /mobile/generateAlpha/budget` → `{has_premium, bypassed, enhancement_credits, rolling,
     * weekly, soft_warning_pct}` (✅ 2026-10-04). On a free account the three allowances are null.
     * When one is an object, §7.3 documents `{remaining, total, reset_at}`; the first such is
     * returned as "remaining of total". Never verified on a paid account.
     */
    suspend fun jllmAllowance(): Pair<Int, Int>? =
        apiCall.execute(Request.Builder().url("${JanitorConfig.LLM_BASE}/generateAlpha/budget").get().build()) { raw ->
            val o = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject ?: return@execute null
            listOf("rolling", "weekly", "enhancement_credits").firstNotNullOfOrNull { key ->
                val a = o[key] as? kotlinx.serialization.json.JsonObject ?: return@firstNotNullOfOrNull null
                val remaining = (a["remaining"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                val total = (a["total"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
                if (remaining != null && total != null && total > 0) remaining to total else null
            }
        }

    suspend fun get(): ApiSettingsDto =
        apiCall.execute(Request.Builder().url(base).get().build(), decode = ::decode)

    /**
     * A partial update: `{source}`, `{selected_proxy_config_id}`, or
     * `{generation_settings: {only the changed keys}}`. Returns the whole settings object.
     */
    suspend fun patch(body: JsonObject): ApiSettingsDto =
        apiCall.execute(Request.Builder().url(base).patch(body.toString().toRequestBody(jsonMedia)).build(), decode = ::decode)

    /** `{name, api_url, api_key, model, prompt_id?}`. */
    suspend fun createProxy(body: JsonObject): ApiSettingsDto =
        apiCall.execute(
            Request.Builder().url("$base/proxy-configs").post(body.toString().toRequestBody(jsonMedia)).build(),
            maxAttempts = 1,
            decode = ::decode,
        )

    /** Only the fields present are changed; `api_key` is sent only when the user typed one. */
    suspend fun patchProxy(id: String, body: JsonObject): ApiSettingsDto =
        apiCall.execute(Request.Builder().url("$base/proxy-configs/$id").patch(body.toString().toRequestBody(jsonMedia)).build(), decode = ::decode)

    suspend fun deleteProxy(id: String) {
        apiCall.execute(Request.Builder().url("$base/proxy-configs/$id").delete().build()) { }
    }

    /** `{name, kind, content}` with kind `system` or `prefill`. */
    suspend fun createPrompt(body: JsonObject): PromptDto =
        apiCall.execute(
            Request.Builder().url(promptBase).post(body.toString().toRequestBody(jsonMedia)).build(),
            maxAttempts = 1,
        ) { json.decodeFromString(PromptDto.serializer(), it) }

    suspend fun patchPrompt(id: String, body: JsonObject): PromptDto =
        apiCall.execute(Request.Builder().url("$promptBase/$id").patch(body.toString().toRequestBody(jsonMedia)).build()) {
            json.decodeFromString(PromptDto.serializer(), it)
        }

    suspend fun deletePrompt(id: String): Boolean =
        apiCall.execute(Request.Builder().url("$promptBase/$id").delete().build()) {
            json.decodeFromString(PromptDeletedDto.serializer(), it).deleted
        }
}
