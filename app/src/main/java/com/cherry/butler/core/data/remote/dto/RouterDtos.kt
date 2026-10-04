package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Janitor Router on `/mb/janitor-router` (docs/JANITOR_API.md §17.4; writes probed 2026-10-04).
 * Prices and balances are integer micro-USD. Every write is refused with
 * `JANITOR_ROUTER_DISABLED` unless the account has Janitor Plus.
 */

@Serializable
data class RouterCatalogDto(
    val enabled: Boolean = false,
    @SerialName("default_model_id") val defaultModelId: String? = null,
    val groups: List<RouterGroupDto> = emptyList(),
    val models: List<RouterModelDto> = emptyList(),
)

@Serializable
data class RouterGroupDto(val label: String = "", @SerialName("model_ids") val modelIds: List<String> = emptyList())

@Serializable
data class RouterModelDto(
    @SerialName("model_id") val modelId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("model_creator") val creator: String = "",
    val provider: String = "",
    val tier: String = "",
    @SerialName("context_length") val contextLength: Int = 0,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("input_price_usd_micros_per_1m") val inputMicrosPer1m: Long = 0,
    @SerialName("output_price_usd_micros_per_1m") val outputMicrosPer1m: Long = 0,
    @SerialName("is_recommended") val recommended: Boolean = false,
    @SerialName("supports_reasoning") val reasoning: Boolean = false,
    @SerialName("supports_vision") val vision: Boolean = false,
    @SerialName("supports_web_search") val webSearch: Boolean = false,
)

@Serializable
data class RouterConfigDto(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("favorite_model_ids") val favoriteModelIds: List<String> = emptyList(),
    @SerialName("model_params") val modelParams: JsonObject = JsonObject(emptyMap()),
    @SerialName("show_thinking") val showThinking: Boolean = true,
    @SerialName("web_search_enabled") val webSearchEnabled: Boolean = false,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    @SerialName("prefill_prompt") val prefillPrompt: String? = null,
    @SerialName("forbidden_words") val forbiddenWords: List<String> = emptyList(),
)

@Serializable
data class RouterWalletDto(
    @SerialName("wallet_available") val walletAvailable: Boolean = false,
    @SerialName("can_generate") val canGenerate: Boolean = false,
    @SerialName("is_low_balance") val lowBalance: Boolean = false,
    @SerialName("total_balance_usd_micros") val totalMicros: Long = 0,
    @SerialName("included_balance_usd_micros") val includedMicros: Long = 0,
    @SerialName("purchased_balance_usd_micros") val purchasedMicros: Long = 0,
    @SerialName("topups_enabled") val topupsEnabled: Boolean = false,
    @SerialName("signup_bonus") val signupBonus: RouterBonusDto? = null,
)

@Serializable
data class RouterBonusDto(
    @SerialName("amount_usd_micros") val amountMicros: Long = 0,
    val claimable: Boolean = false,
    val claimed: Boolean = false,
)
