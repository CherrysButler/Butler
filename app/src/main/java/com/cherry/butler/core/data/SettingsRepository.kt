package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.SettingsRemoteSource
import com.cherry.butler.core.data.remote.dto.ApiSettingsDto
import com.cherry.butler.core.data.remote.dto.PromptDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

enum class Provider(val wire: String) {
    /** The user's own reverse proxy (OpenRouter and the like). */
    Proxy("proxy"),
    /** Janitor's own model (JLLM), or the paid router when the account has it on. */
    Janitor("janitor");

    companion object {
        fun from(wire: String) = entries.firstOrNull { it.wire == wire } ?: Proxy
    }
}

data class ProxyConfig(
    val id: String,
    val name: String,
    val apiUrl: String,
    val model: String,
    /** Whether a key is saved. The key itself never leaves the server response. */
    val hasKey: Boolean,
    val promptId: String?,
    val promptName: String?,
    /** This proxy's id in the profile's legacy config, which generation reads. */
    val clientId: String? = null,
) {
    /** Both ids it goes by, so options saved here are found when generating. */
    val ids: List<String> get() = listOfNotNull(id, clientId)
}

data class Prompt(val id: String, val name: String, val kind: String, val content: String)

data class AiSettings(
    val provider: Provider,
    val routerEnabled: Boolean,
    val selectedProxyId: String?,
    val proxies: List<ProxyConfig>,
    val prompts: List<Prompt>,
    val generation: JsonObject,
) {
    val selectedProxy: ProxyConfig? get() = proxies.firstOrNull { it.id == selectedProxyId }
}

/** What the proxy editor hands back. A null [apiKey] means "leave the saved key alone". */
data class ProxyDraft(
    val id: String?,
    val name: String,
    val apiUrl: String,
    val model: String,
    val apiKey: String?,
    val promptId: String?,
)

/**
 * The user's AI configuration. Every write goes to `/api-settings`, comes back as the
 * full settings object, and is followed by a profile re-read: the server mirrors writes
 * into the profile's legacy config, and that copy is what the generation envelope's
 * `userConfig` is built from (verified 2026-10-03 — provider, selected proxy, model and
 * sampler values all follow within the same response).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val remote: SettingsRemoteSource,
    private val profile: ProfileRepository,
    private val openRouter: OpenRouterOptionsStore,
) {
    private val mutex = Mutex()
    private val _settings = MutableStateFlow<AiSettings?>(null)
    /** JLLM's allowance as (remaining, total), when the account has one. */
    suspend fun jllmAllowance(): Pair<Int, Int>? = runCatching { remote.jllmAllowance() }.getOrNull()

    val settings: StateFlow<AiSettings?> = _settings.asStateFlow()

    suspend fun load(refresh: Boolean = false): AiSettings = mutex.withLock {
        _settings.value?.takeUnless { refresh } ?: publish(remote.get())
    }

    suspend fun setProvider(provider: Provider) = write { remote.patch(buildJsonObject { put("source", provider.wire) }) }

    suspend fun selectProxy(id: String) = write { remote.patch(buildJsonObject { put("selected_proxy_config_id", id) }) }

    /** One sampler key; the server merges it into the rest. */
    suspend fun setGeneration(key: String, value: JsonElement) = write {
        remote.patch(buildJsonObject { put("generation_settings", buildJsonObject { put(key, value) }) })
    }

    suspend fun saveProxy(draft: ProxyDraft) = write {
        val body = buildJsonObject {
            put("name", draft.name.trim())
            put("api_url", draft.apiUrl.trim())
            put("model", draft.model.trim())
            draft.apiKey?.let { put("api_key", it.trim()) }
            draft.promptId?.let { put("prompt_id", it) }
        }
        if (draft.id == null) remote.createProxy(body) else remote.patchProxy(draft.id, body)
    }

    suspend fun deleteProxy(id: String) = write {
        val ids = _settings.value?.proxies?.firstOrNull { it.id == id }?.ids ?: listOf(id)
        remote.deleteProxy(id)
        ids.forEach(openRouter::remove)
        remote.get()
    }

    /**
     * A copy of a saved proxy, key included: the key is read from Janitor (where the
     * original keeps it) straight into the create request, never shown or kept. Its
     * OpenRouter options come along. Returns the new proxy's id.
     */
    suspend fun cloneProxy(id: String): String {
        val source = load(refresh = true).proxies.firstOrNull { it.id == id } ?: error("That proxy no longer exists.")
        val before = _settings.value?.proxies.orEmpty().map { it.id }.toSet()
        val after = saveProxy(
            ProxyDraft(
                id = null,
                name = "${source.name} copy".take(MAX_NAME),
                apiUrl = source.apiUrl,
                model = source.model,
                // The profile config, where the key is read from, knows this proxy by its client id.
                apiKey = if (source.hasKey) profile.proxyKeyFor(source.clientId ?: id) ?: error("Couldn't read the key to copy.") else null,
                promptId = source.promptId,
            ),
        )
        val copy = after.proxies.firstOrNull { it.id !in before } ?: error("Janitor didn't return the copy.")
        openRouter.set(copy.ids, openRouter.get(id))
        return copy.id
    }

    suspend fun savePrompt(id: String?, name: String, kind: String, content: String): Prompt {
        val body = buildJsonObject {
            put("name", name.trim())
            put("content", content)
            if (id == null) put("kind", kind)
        }
        val saved = if (id == null) remote.createPrompt(body) else remote.patchPrompt(id, body)
        val prompt = saved.toPrompt()
        _settings.update { s ->
            s?.copy(prompts = if (s.prompts.any { it.id == prompt.id }) s.prompts.map { if (it.id == prompt.id) prompt else it } else s.prompts + prompt)
        }
        // A prompt linked to the selected proxy changes what generations carry.
        runCatching { profile.profile(refresh = true) }
        return prompt
    }

    suspend fun deletePrompt(id: String) {
        remote.deletePrompt(id)
        load(refresh = true)
        runCatching { profile.profile(refresh = true) }
    }

    fun clear() {
        _settings.value = null
    }

    private suspend fun write(call: suspend () -> ApiSettingsDto): AiSettings {
        val fresh = publish(call())
        // The envelope reads the profile's mirrored config; bring it up to date now so the
        // very next send uses what the user just chose.
        runCatching { profile.profile(refresh = true) }
        return fresh
    }

    private fun publish(dto: ApiSettingsDto): AiSettings {
        val settings = AiSettings(
            provider = Provider.from(dto.settings.source),
            routerEnabled = dto.settings.routerEnabled,
            selectedProxyId = dto.settings.selectedProxyConfigId,
            proxies = dto.proxyConfigs.sortedBy { it.position }.map {
                ProxyConfig(it.id, it.name, it.apiUrl, it.model, it.hasKey, it.prompt?.id, it.prompt?.name, it.clientId)
            },
            prompts = dto.prompts?.map { it.toPrompt() } ?: _settings.value?.prompts.orEmpty(),
            generation = dto.settings.generationSettings,
        )
        _settings.value = settings
        return settings
    }

    private fun PromptDto.toPrompt() = Prompt(id, name, kind, content)

    private companion object {
        /** Janitor's limit on a proxy configuration's name (docs/JANITOR_API.md §27.2). */
        const val MAX_NAME = 255
    }
}
