package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.ProfileRemoteSource
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.data.remote.dto.ProfileDto
import com.cherry.butler.core.generation.GenerationEnvelope
import com.cherry.butler.core.generation.ProxyTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in user: profile, AI configuration, personas.
 *
 * Held in memory only. `config` carries the user's proxy API keys (docs/JANITOR_API.md §17.1), so
 * it is never written to Room, never logged, and only ever leaves this class as the
 * `userConfig` block of a generation envelope or a [ProxyTarget].
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val remote: ProfileRemoteSource,
    private val openRouter: OpenRouterOptionsStore,
) {
    private val mutex = Mutex()

    private val _profile = MutableStateFlow<ProfileDto?>(null)
    val profile: StateFlow<ProfileDto?> = _profile.asStateFlow()

    private val _personas = MutableStateFlow<List<PersonaDto>>(emptyList())
    val personas: StateFlow<List<PersonaDto>> = _personas.asStateFlow()

    suspend fun profile(refresh: Boolean = false): ProfileDto = mutex.withLock {
        _profile.value?.takeUnless { refresh } ?: remote.mine().also { _profile.value = it }
    }

    suspend fun personas(refresh: Boolean = false): List<PersonaDto> = mutex.withLock {
        if (!refresh && _personas.value.isNotEmpty()) return _personas.value
        remote.personas().sortedBy { it.order }.also { _personas.value = it }
    }

    /**
     * Who the user is playing in [chat] for a generation: the persona of the latest line they
     * wrote (a chat can change hands mid-way), else the chat's own. Its appearance falls back
     * to the chat's default-persona text, then the profile's: the default persona *is* the
     * profile (docs/JANITOR_API.md §29). One answer for replies, choices and summaries alike.
     */
    suspend fun playedPersona(chat: com.cherry.butler.core.data.local.ChatEntity, history: List<com.cherry.butler.core.data.local.MessageEntity>): PlayedPersona {
        val lastUser = history.lastOrNull { !it.isBot }
        val personaId = if (lastUser != null) lastUser.personaId else chat.personaId
        val persona = personaId?.let { id -> personas().firstOrNull { it.id == id } }
        val appearance = persona?.appearance ?: chat.defaultPersonaAppearance ?: profile().appearance.orEmpty()
        return PlayedPersona(persona, appearance)
    }

    data class PlayedPersona(val persona: PersonaDto?, val appearance: String)

    /** `{character_count, persona_count, script_count}`; not cached, it is cheap. */
    suspend fun counts() = remote.counts()

    suspend fun patchConfigKey(key: String, value: kotlinx.serialization.json.JsonElement) {
        remote.patchConfigKey(key, value)
        // Re-read rather than patch locally: the server is the source of truth for config.
        profile(refresh = true)
    }

    fun clear() {
        _profile.value = null
        _personas.value = emptyList()
    }

    // ---- generation inputs -----------------------------------------------------------

    /**
     * The selected proxy configuration, as the official client resolves it: the entry in
     * `config.proxyConfigurations` whose `id` equals `config.selectedProxyConfigId`
     * (verified 2026-09-23 — `reverseProxyKey`, `open_ai_reverse_proxy` and `openAiModel`
     * in the envelope all matched that entry).
     */
    fun selectedProxy(profile: ProfileDto): SelectedProxy? {
        val config = profile.config ?: return null
        val selectedId = config["selectedProxyConfigId"]?.jsonPrimitive?.contentOrNull ?: return null
        val entry = config["proxyConfigurations"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["id"]?.jsonPrimitive?.contentOrNull == selectedId }
            ?: return null
        val url = entry["apiUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val key = entry["apiKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val model = entry["model"]?.jsonPrimitive?.contentOrNull
        return SelectedProxy(id = selectedId, url = url, key = key, model = model)
    }

    /** Where the payload goes, and what OpenRouter options it picks up on the way. */
    fun proxyTarget(profile: ProfileDto): ProxyTarget? =
        selectedProxy(profile)?.let { proxy ->
            val options = openRouter.get(proxy.id).takeIf { OpenRouterOptions.isOpenRouter(proxy.url) }
            ProxyTarget(url = proxy.url, key = proxy.key, shape = { payload -> options?.applyTo(payload) ?: payload })
        }

    /**
     * A saved proxy's key, read fresh from Janitor, for cloning that proxy into a new one.
     * The caller hands it straight to the create request; it is never kept or shown.
     */
    internal suspend fun proxyKeyFor(proxyId: String): String? {
        val config = profile(refresh = true).config ?: return null
        return config["proxyConfigurations"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["id"]?.jsonPrimitive?.contentOrNull == proxyId }
            ?.get("apiKey")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }

    /** The `userConfig` block. Built fresh per generation; never cached as a string. */
    fun userConfig(profile: ProfileDto): JsonObject {
        val proxy = selectedProxy(profile)
        return GenerationEnvelope.userConfig(
            profileConfig = profile.config ?: JsonObject(emptyMap()),
            reverseProxyKey = proxy?.key,
            reverseProxyUrl = proxy?.url,
            routerEnabled = false,
        )
    }

    class SelectedProxy(val id: String, val url: String, key: String, val model: String?) {
        /** Exposed only to [ProfileRepository.proxyTarget] and the envelope builder. */
        internal val key: String = key
        override fun toString(): String = "SelectedProxy($url, model=$model)"
    }
}
