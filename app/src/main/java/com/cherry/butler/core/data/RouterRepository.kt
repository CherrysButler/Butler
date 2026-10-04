package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.RouterRemoteSource
import com.cherry.butler.core.data.remote.dto.RouterCatalogDto
import com.cherry.butler.core.data.remote.dto.RouterConfigDto
import com.cherry.butler.core.data.remote.dto.RouterModelDto
import com.cherry.butler.core.data.remote.dto.RouterWalletDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** A catalog group as the picker shows it: its label and its models in catalog order. */
data class RouterGroup(val label: String, val models: List<RouterModelDto>)

/**
 * Janitor Router: Janitor's paid, metered models. The catalog and wallet read on any
 * account; configuring it needs Janitor Plus, and the API says so (`JANITOR_ROUTER_DISABLED`).
 * Every write sends the whole config, as the API requires, and re-reads it after.
 */
@Singleton
class RouterRepository @Inject constructor(
    private val remote: RouterRemoteSource,
    private val settings: SettingsRepository,
) {
    private val _catalog = MutableStateFlow<RouterCatalogDto?>(null)
    val catalog: StateFlow<RouterCatalogDto?> = _catalog.asStateFlow()

    private val _config = MutableStateFlow<RouterConfigDto?>(null)
    val config: StateFlow<RouterConfigDto?> = _config.asStateFlow()

    private val _wallet = MutableStateFlow<RouterWalletDto?>(null)
    val wallet: StateFlow<RouterWalletDto?> = _wallet.asStateFlow()

    /** All three at once; a part that fails leaves what it had. Throws only if the config can't be read. */
    suspend fun load() = coroutineScope {
        val cat = async { runCatching { remote.catalog() }.getOrNull() }
        val wal = async { runCatching { remote.wallet() }.getOrNull() }
        val conf = remote.config()
        _config.value = conf
        cat.await()?.let { _catalog.value = it }
        wal.await()?.let { _wallet.value = it }
    }

    /** The catalog's groups with their models resolved; a model in no group is listed under "Other". */
    fun groups(): List<RouterGroup> {
        val c = _catalog.value ?: return emptyList()
        val byId = c.models.associateBy { it.modelId }
        val grouped = c.groups.map { g -> RouterGroup(g.label, g.modelIds.mapNotNull { byId[it] }) }.filter { it.models.isNotEmpty() }
        val placed = c.groups.flatMap { it.modelIds }.toSet()
        val rest = c.models.filterNot { it.modelId in placed }
        return if (rest.isEmpty()) grouped else grouped + RouterGroup("Other", rest)
    }

    suspend fun setEnabled(on: Boolean) = write { it.copy(enabled = on) }

    suspend fun setModel(modelId: String) = write { it.copy(modelId = modelId) }

    suspend fun setShowThinking(on: Boolean) = write { it.copy(showThinking = on) }

    suspend fun setFavorite(modelId: String, on: Boolean) {
        remote.setFavorite(modelId, on)
        _config.value = remote.config()
    }

    suspend fun claimBonus() {
        remote.claimBonus()
        _wallet.value = remote.wallet()
    }

    private suspend fun write(change: (RouterConfigDto) -> RouterConfigDto) {
        val next = change(_config.value ?: remote.config())
        remote.putConfig(buildJsonObject {
            put("enabled", next.enabled)
            put("model_id", next.modelId)
            put("model_params", next.modelParams)
            put("show_thinking", next.showThinking)
            put("web_search_enabled", next.webSearchEnabled)
            put("system_prompt", next.systemPrompt)
            put("prefill_prompt", next.prefillPrompt)
            put("forbidden_words", JsonArray(next.forbiddenWords.map { JsonPrimitive(it) }))
        })
        _config.value = remote.config()
        // `/api-settings` mirrors `router_enabled`; the chat's chip and the Model section read that.
        runCatching { settings.load(refresh = true) }
    }
}
