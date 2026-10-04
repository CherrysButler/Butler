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
)

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
        val created = remote.createPersona(draft.name.trim(), draft.appearance, avatar, draft.pronouns?.dto)
        profiles.personas(refresh = true)
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
