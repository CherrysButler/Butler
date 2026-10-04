package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * AI settings wire shapes — `GET /mb/api-settings`, verified 2026-10-03 (docs/JANITOR_API.md §27).
 *
 * The response carries the user's proxy API keys in clear (`proxy_configs[].api_key`,
 * and again inside `legacy_config`). These classes deliberately do not model either:
 * the key is reduced to "is one saved" at decode time, and `legacy_config` is not read
 * at all — the generation envelope gets its copy from the profile, which the server
 * keeps in sync. Nothing here may be logged or written to disk.
 */

@Serializable
data class ApiSettingsDto(
    val settings: SettingsDto,
    @SerialName("proxy_configs") val proxyConfigs: List<ProxyConfigDto> = emptyList(),
    /**
     * Present on `GET`, absent from every `PATCH` response (verified 2026-10-03). Null means
     * "not sent", not "none": the caller keeps the list it already has.
     */
    val prompts: List<PromptDto>? = null,
)

@Serializable
data class SettingsDto(
    /** `"proxy"` or `"janitor"` (JLLM / Janitor Router). */
    val source: String = "proxy",
    @SerialName("router_enabled") val routerEnabled: Boolean = false,
    @SerialName("selected_proxy_config_id") val selectedProxyConfigId: String? = null,
    /** Kept raw: 14+ keys, written back one key at a time with a partial PATCH. */
    @SerialName("generation_settings") val generationSettings: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ProxyConfigDto(
    val id: String,
    val name: String = "",
    @SerialName("api_url") val apiUrl: String = "",
    val model: String = "",
    val position: Int = 0,
    /**
     * The same proxy's id in the profile's legacy `config.proxyConfigurations` (which is
     * what generation reads). The two id spaces differ; this links them (verified 2026-10-04).
     */
    @SerialName("client_id") val clientId: String? = null,
    val prompt: PromptDto? = null,
    /**
     * Never the key itself: whether one is saved. Decoded by [ProxyKeyPresence] so the
     * string is dropped as soon as it is read.
     */
    @SerialName("api_key")
    @Serializable(with = ProxyKeyPresence::class)
    val hasKey: Boolean = false,
)

@Serializable
data class PromptDto(
    val id: String,
    val name: String = "",
    /** `system` or `prefill` (the server rejects anything else). */
    val kind: String = "system",
    val content: String = "",
)

/** `DELETE /prompt-library/{id}` → `{deleted, cleared_live_slot, cleared_preset_ids}`. */
@Serializable
data class PromptDeletedDto(val deleted: Boolean = false)
