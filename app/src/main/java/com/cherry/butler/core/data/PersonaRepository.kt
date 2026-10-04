package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.config.JanitorConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.cherry.butler.core.data.remote.dto.PronounsDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who the user plays as. The profile itself is a persona — Janitor uses it whenever a
 * chat has no `persona_id` (verified 2026-10-03: such a chat's detail lists the profile
 * as its persona, id = user id, name = profile name) — and every saved persona is another.
 */
data class PersonaOption(
    /** null for the profile; a chat created with it sends no `persona_id`. */
    val id: String?,
    val name: String,
    val avatarUrl: String?,
)

/**
 * Set by "Make default" (PersonaSwap): what the persona now in the default slot brought that
 * the profile has no room for, so the next swap can hand it back exactly.
 */
@Serializable
data class DefaultOrigin(
    /** Its own picture file in the persona folder, reused as-is when it leaves the default slot. */
    val personaAvatar: String?,
    /** The profile has no pronouns field. */
    val pronouns: PronounsDto?,
    /** The profile picture right after the swap; if it changes elsewhere, the new one wins. */
    val profileAvatarAtSwap: String?,
    /** Janitor's profile picture isn't this persona's (refused, or it had none): Butler shows it. */
    val pictureInButlerOnly: Boolean,
    /** False when the user went on "without a picture": the default shows none. */
    val showPicture: Boolean = true,
) {
    fun isCurrentFor(profileAvatar: String?): Boolean = profileAvatar == profileAvatarAtSwap
}

/**
 * The persona switch: one choice, remembered across launches, that every new chat is
 * started with and that fills `{{user}}` wherever a chat does not say otherwise.
 *
 * Only the persona's id is stored, in plain preferences; it is not a secret.
 */
@Singleton
class PersonaRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val profile: ProfileRepository,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED, null))

    private val _defaultOrigin = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_ORIGIN, null)?.let { runCatching { json.decodeFromString(DefaultOrigin.serializer(), it) }.getOrNull() },
    )

    /** What the profile can't hold about the persona now in the default slot (see [DefaultOrigin]). */
    val defaultOrigin: DefaultOrigin? get() = _defaultOrigin.value

    fun setDefaultOrigin(origin: DefaultOrigin?) {
        _defaultOrigin.value = origin
        prefs.edit().putString(KEY_DEFAULT_ORIGIN, origin?.let { json.encodeToString(DefaultOrigin.serializer(), it) }).apply()
    }

    /** The profile first, then personas in the user's own order. */
    val options: StateFlow<List<PersonaOption>> = combine(profile.profile, profile.personas, _defaultOrigin) { p, personas, origin ->
        buildList {
            if (p != null) {
                val butlerOnly = origin != null && origin.pictureInButlerOnly && origin.isCurrentFor(p.avatar)
                val avatar = when {
                    !butlerOnly -> p.avatar
                    origin?.showPicture == false -> null
                    else -> JanitorConfig.personaAvatarUrl(origin?.personaAvatar)
                }
                add(PersonaOption(id = null, name = p.name.ifBlank { p.userName }, avatarUrl = avatar))
            }
            personas.forEach { add(PersonaOption(it.id, it.name, JanitorConfig.personaAvatarUrl(it.avatar))) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * The current choice. A persona deleted elsewhere falls back to the profile rather than
     * to a name that no longer exists.
     */
    val current: StateFlow<PersonaOption?> = combine(options, _selectedId) { all, id ->
        all.firstOrNull { it.id == id } ?: all.firstOrNull { it.id == null }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    fun select(id: String?) {
        _selectedId.value = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    /** Makes sure the profile and personas are in memory; failures leave what is there. */
    suspend fun load() {
        runCatching { profile.profile() }
        runCatching { profile.personas() }
    }

    private companion object {
        const val PREFS = "butler_prefs"
        const val KEY_SELECTED = "persona_id"
        const val KEY_DEFAULT_ORIGIN = "default_origin"
    }
}
