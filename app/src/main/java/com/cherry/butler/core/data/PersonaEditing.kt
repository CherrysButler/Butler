package com.cherry.butler.core.data

import android.net.Uri
import com.cherry.butler.core.data.remote.ProfileRemoteSource
import com.cherry.butler.core.data.remote.dto.PersonaPatch
import com.cherry.butler.core.data.remote.dto.PronounsDto
import com.cherry.butler.core.network.ApiError
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** The pronoun sets Janitor's personas carry, lowercase as Janitor stores them. */
enum class PronounSet(val label: String, val dto: PronounsDto?) {
    None("None", null),
    She("She", PronounsDto("she", "her", "her", "hers", "herself")),
    He("He", PronounsDto("he", "him", "his", "his", "himself")),
    They("They", PronounsDto("they", "them", "their", "theirs", "themselves")),
    ;

    companion object {
        /** Null when the saved set is none of these; the editor then leaves it as it is. */
        fun of(dto: PronounsDto?): PronounSet? = when {
            dto == null || dto.subjective.isNullOrBlank() -> None
            else -> entries.firstOrNull { it.dto == dto }
        }
    }
}

/** What the persona editor saves. [picture] is a new picture from the phone, or null to keep the old. */
data class PersonaDraft(
    val name: String,
    val appearance: String,
    /** Null leaves the saved pronouns alone (a set Butler has no button for). */
    val pronouns: PronounSet?,
    val picture: Uri?,
    /** The profile only: its public bio. */
    val aboutMe: String? = null,
    /** The profile only: its public page's look; null leaves it alone. */
    val style: ProfileStyle? = null,
)

/**
 * The public profile page's look, as Butler edits it: the stylesheet and two colours (`#RRGGBB`,
 * or blank for none). The background picture and its opacity are kept as they are.
 */
data class ProfileStyle(
    val css: String = "",
    val textColor: String = "",
    val backgroundColor: String = "",
) {
    /** [saved] with these three put in, every other key as it was. */
    fun into(saved: kotlinx.serialization.json.JsonObject?): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.JsonObject(
            saved.orEmpty() + mapOf(
                KEY_CSS to JsonPrimitive(css),
                KEY_TEXT to JsonPrimitive(textColor.trim()),
                KEY_BACKGROUND to JsonPrimitive(backgroundColor.trim()),
            ),
        )

    companion object {
        const val KEY_CSS = "custom_style"
        const val KEY_TEXT = "text_color"
        const val KEY_BACKGROUND = "background_color"

        fun of(saved: kotlinx.serialization.json.JsonObject?): ProfileStyle {
            fun text(key: String) = (saved?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
            return ProfileStyle(css = text(KEY_CSS), textColor = text(KEY_TEXT), backgroundColor = text(KEY_BACKGROUND))
        }
    }
}

/**
 * Creating, editing and deleting personas, and editing the profile, which is the default
 * persona (§29). Every change re-reads what it touched, so the persona list and the default
 * show what Janitor now holds.
 */
@Singleton
class PersonaEditing @Inject constructor(
    private val remote: ProfileRemoteSource,
    private val profiles: ProfileRepository,
    private val personas: PersonaRepository,
    private val pictures: PictureUpload,
) {
    /** A new persona; returns its id. */
    suspend fun create(draft: PersonaDraft): String {
        val avatar = draft.picture?.let { pictures.upload(it, "avatar") }.orEmpty()
        return create(draft.name, draft.appearance, avatar, draft.pronouns?.dto)
    }

    /**
     * A new persona from parts, as an import has them: pronouns as they were saved, and
     * [avatarFile] a picture already in Janitor's persona folder by its file name ("" for none).
     * [refresh] false leaves re-reading the list to the caller, once after many.
     */
    suspend fun create(name: String, appearance: String, avatarFile: String, pronouns: PronounsDto?, refresh: Boolean = true): String {
        val created = remote.createPersona(name.trim(), appearance, avatarFile, pronouns)
        if (refresh) profiles.personas(refresh = true)
        return created.id
    }

    suspend fun update(id: String, draft: PersonaDraft) {
        val saved = profiles.personas(refresh = true).firstOrNull { it.id == id }
            ?: throw ApiError.Api(code = 404, janitorCode = "BUTLER_NO_PERSONA", serverMessage = "That persona isn't on your account any more.")
        val avatar = draft.picture?.let { pictures.upload(it, "avatar") } ?: saved.avatar.orEmpty()
        remote.patchPersona(
            PersonaPatch(
                id = id,
                name = draft.name.trim(),
                appearance = draft.appearance,
                avatar = avatar,
                pronouns = if (draft.pronouns == null) saved.pronouns else draft.pronouns.dto,
            ),
        )
        profiles.personas(refresh = true)
    }

    suspend fun delete(id: String) {
        remote.deletePersona(id)
        if (personas.selectedId.value == id) personas.select(null)
        profiles.personas(refresh = true)
    }

    /**
     * The profile, one key at a time (§21.1), only what changed. The picture goes last: it
     * passes Janitor's stricter check (§29), and a refusal there leaves the rest saved. That
     * refusal is rethrown so the editor can show Janitor's reason.
     */
    suspend fun updateProfile(draft: PersonaDraft) {
        val now = profiles.profile(refresh = true)
        val name = draft.name.trim()
        if (name.isNotEmpty() && name != now.name) remote.patchMine(buildJsonObject { put("name", JsonPrimitive(name)) })
        if (draft.appearance != now.appearance.orEmpty()) remote.patchMine(buildJsonObject { put("profile", JsonPrimitive(draft.appearance)) })
        if (draft.aboutMe != null && draft.aboutMe != now.aboutMe.orEmpty()) remote.patchMine(buildJsonObject { put("about_me", JsonPrimitive(draft.aboutMe)) })
        // The whole style object goes back, so keys Butler doesn't edit (the background
        // picture, its opacity) survive whether Janitor merges a partial style or replaces it.
        if (draft.style != null && draft.style != ProfileStyle.of(now.style)) remote.patchMine(buildJsonObject { put("style", draft.style.into(now.style)) })
        try {
            draft.picture?.let { uri ->
                val file = pictures.upload(uri, "profile-avatar")
                // A new profile picture also retires whatever a swap left Butler showing:
                // DefaultOrigin.isCurrentFor no longer matches, so the profile's own wins.
                remote.patchMine(buildJsonObject { put("avatar", JsonPrimitive(file)) })
            }
        } finally {
            profiles.profile(refresh = true)
        }
    }
}
