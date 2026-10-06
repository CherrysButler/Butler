package com.cherry.butler.core.data

import kotlinx.serialization.json.JsonPrimitive
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
    private val content: ContentPrefs,
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

    // ---- persona groups ----------------------------------------------------------------

    private val _groups = MutableStateFlow<List<com.cherry.butler.core.data.remote.dto.PersonaGroupDto>>(emptyList())

    /** The user's persona groups, in their order. */
    val groups: StateFlow<List<com.cherry.butler.core.data.remote.dto.PersonaGroupDto>> = _groups.asStateFlow()

    suspend fun groups(refresh: Boolean = false) {
        if (!refresh && _groups.value.isNotEmpty()) return
        _groups.value = remote.groups().sortedBy { it.order }
    }

    suspend fun createGroup(name: String, color: String): com.cherry.butler.core.data.remote.dto.PersonaGroupDto =
        remote.createGroup(name, color).also { _groups.value = _groups.value + it }

    suspend fun updateGroup(id: String, name: String, color: String) {
        val updated = remote.updateGroup(id, name, color)
        _groups.value = _groups.value.map { if (it.id == id) updated else it }
    }

    /** Deletes the group; its personas stay, out of any group, which the mirror shows at once. */
    suspend fun deleteGroup(id: String) {
        remote.deleteGroup(id)
        _groups.value = _groups.value.filterNot { it.id == id }
        _personas.value = _personas.value.map { if (it.groupId == id) it.copy(groupId = null) else it }
    }

    /** Moves the group [by] places (-1 up, +1 down) and saves the whole order. */
    suspend fun moveGroup(id: String, by: Int) {
        val list = _groups.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        val before = _groups.value
        _groups.value = list
        runCatching { remote.reorderGroups(list.map { it.id }) }.onFailure { _groups.value = before; throw it }
    }

    suspend fun movePersona(personaId: String, groupId: String?) {
        remote.movePersona(personaId, groupId)
        _personas.value = _personas.value.map { if (it.id == personaId) it.copy(groupId = groupId) else it }
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
        val selectedId = profile.config?.get("selectedProxyConfigId")?.jsonPrimitive?.contentOrNull ?: return null
        return proxy(profile, selectedId)
    }

    /** Any saved proxy configuration, by its id in `config.proxyConfigurations`. */
    fun proxy(profile: ProfileDto, id: String): SelectedProxy? {
        val entry = profile.config?.get("proxyConfigurations")?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["id"]?.jsonPrimitive?.contentOrNull == id }
            ?: return null
        val url = entry["apiUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val key = entry["apiKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val model = entry["model"]?.jsonPrimitive?.contentOrNull
        return SelectedProxy(id = id, url = url, key = key, model = model)
    }

    /**
     * `userConfig` and proxy target for a generation by [writer] rather than the chat's own
     * choice. The legacy config Janitor reads is rewritten the way its own switch writes it
     * (`api`, `open_ai_mode`, `selectedProxyConfigId`, `openAiModel`; docs/JANITOR_API.md
     * §27.1), and a proxy's payload is also stamped with its model on the way out, so the
     * answer comes from the model asked for whatever Janitor assembled. A proxy that no
     * longer exists falls back to the chat's own choice.
     */
    fun inputsFor(profile: ProfileDto, writer: Writer): Pair<JsonObject, ProxyTarget?> {
        val base = profile.config ?: JsonObject(emptyMap())
        return when (writer) {
            Writer.SameAsChat -> userConfig(profile) to proxyTarget(profile)
            Writer.Jllm -> GenerationEnvelope.userConfig(
                profileConfig = JsonObject(base + mapOf("api" to JsonPrimitive("janitor"), "open_ai_mode" to JsonPrimitive("api_key"))),
                reverseProxyKey = null,
                reverseProxyUrl = null,
                routerEnabled = false,
                allowMobileNsfw = content.allowMobileNsfw.value,
            ) to null
            is Writer.Proxy -> {
                val saved = proxy(profile, writer.id) ?: return userConfig(profile) to proxyTarget(profile)
                val p = writer.model?.let { SelectedProxy(saved.id, saved.url, saved.key, it) } ?: saved
                val config = JsonObject(
                    base + buildMap {
                        put("api", JsonPrimitive("openai"))
                        put("open_ai_mode", JsonPrimitive("proxy"))
                        put("selectedProxyConfigId", JsonPrimitive(p.id))
                        p.model?.let { put("openAiModel", JsonPrimitive(it)) }
                    },
                )
                val options = openRouter.get(p.id).takeIf { OpenRouterOptions.isOpenRouter(p.url) }
                val target = ProxyTarget(url = p.url, key = p.key, shape = { payload ->
                    val shaped = options?.applyTo(payload) ?: payload
                    p.model?.let { JsonObject(shaped + ("model" to JsonPrimitive(it))) } ?: shaped
                })
                GenerationEnvelope.userConfig(
                    profileConfig = config, reverseProxyKey = p.key, reverseProxyUrl = p.url, routerEnabled = false,
                    allowMobileNsfw = content.allowMobileNsfw.value,
                ) to target
            }
        }
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
            allowMobileNsfw = content.allowMobileNsfw.value,
        )
    }

    class SelectedProxy(val id: String, val url: String, key: String, val model: String?) {
        /** Exposed only to [ProfileRepository.proxyTarget] and the envelope builder. */
        internal val key: String = key
        override fun toString(): String = "SelectedProxy($url, model=$model)"
    }
}
