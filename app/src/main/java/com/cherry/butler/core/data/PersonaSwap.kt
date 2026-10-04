package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.ProfileRemoteSource
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.data.remote.dto.PersonaPatch
import com.cherry.butler.core.data.remote.dto.ProfileDto
import com.cherry.butler.core.network.ApiError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Make default": Janitor cannot change which persona is the default, because the default
 * *is* the profile (its display name, picture and `profile` text). So the two trade places:
 * the chosen persona's name, appearance and picture go into the profile, and the profile's
 * go into that persona's slot. To the user the personas simply swapped.
 *
 * It runs as the three steps the screen shows ([Plan]), after [prepare] reads both fresh
 * and writes a copy to the phone:
 * 1. [commitPicture]: the persona's picture becomes the profile picture. First, because it's
 *    the one step Janitor may refuse on content grounds (its profile-picture check is stricter
 *    than the persona one); refused, nothing has changed yet and the user decides;
 * 2. [swapNames]: the persona slot takes the default's details, the profile the persona's;
 * 3. [settle]: Butler keeps what the profile can't hold and selects the new default.
 * A failure in 2 puts back everything already changed, the picture included ([revert]).
 *
 * What the profile can't hold (pronouns, the persona's own picture file) is kept as a
 * [DefaultOrigin], so swapping back returns the persona exactly as it was. Janitor has no
 * pronouns for the default anywhere (its own app builds the default from name, avatar and
 * `profile`; a PATCH with `pronouns` is accepted and dropped, like any unknown key).
 *
 * Chats keep pointing at the same ids, so they follow the swap: chats started with the
 * default now show the new default, and chats started with the persona now show the old one.
 */
@Singleton
class PersonaSwap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remote: ProfileRemoteSource,
    private val profiles: ProfileRepository,
    private val personas: PersonaRepository,
    private val json: Json,
    client: OkHttpClient,
) {
    /** Talks to the picture CDN and the presigned upload URL: no Janitor headers, or the upload's signature breaks. */
    private val plain: OkHttpClient = client.newBuilder().apply { interceptors().clear() }.build()

    @Serializable
    private data class Backup(
        val profileName: String,
        val profileAppearance: String?,
        val profileAvatar: String?,
        val personaId: String,
        val personaName: String,
        val personaAppearance: String?,
        val personaAvatar: String?,
        val defaultOrigin: DefaultOrigin? = null,
    )

    /** One swap in progress: what both looked like before, and what has been changed so far. */
    class Plan internal constructor(
        internal val profile: ProfileDto,
        internal val persona: PersonaDto,
        internal val origin: DefaultOrigin?,
    ) {
        /** The default's name, as it reads before the swap. */
        val defaultName: String get() = profile.name.ifBlank { profile.userName }

        /** Whether the persona has a picture to put on the profile at all. */
        val hasPicture: Boolean get() = !persona.avatar.isNullOrBlank()

        internal var pictureCommitted = false
        internal var slotRewritten = false
        internal val profileKeys = ArrayList<String>()
    }

    sealed interface PictureResult {
        data object Done : PictureResult
        /** The persona has no picture: the default will show none. */
        data object NoPicture : PictureResult
        /** Janitor turned it down; [reason] is its own words. Nothing has changed. */
        data class Refused(val reason: String) : PictureResult
    }

    /** Before the steps: reads both fresh and writes the phone's copy. */
    suspend fun prepare(personaId: String): Plan = withContext(Dispatchers.IO) {
        val profile = remote.mine()
        val persona = remote.personas().firstOrNull { it.id == personaId }
            ?: throw ApiError.Api(code = 404, janitorCode = "BUTLER_PERSONA_GONE", serverMessage = "That persona no longer exists.")
        // What the current default brought from its persona slot, unless the profile picture
        // has since been changed elsewhere (then the profile as it is now is what moves).
        val origin = personas.defaultOrigin?.takeIf { it.isCurrentFor(profile.avatar) }
        writeBackup(profile, persona, origin)
        Plan(profile, persona, origin)
    }

    /** Step 1: the persona's picture onto the profile. A refusal changes nothing. */
    suspend fun commitPicture(plan: Plan): PictureResult = withContext(Dispatchers.IO) {
        val url = JanitorConfig.personaAvatarUrl(plan.persona.avatar) ?: return@withContext PictureResult.NoPicture
        val file = copyPicture(url, type = "profile-avatar")
        try {
            remote.patchMine(buildJsonObject { put("avatar", JsonPrimitive(file)) })
        } catch (e: ApiError.Api) {
            if (e.code in 400..499) {
                return@withContext PictureResult.Refused(e.serverMessage?.takeIf { it.isNotBlank() } ?: "Janitor didn't accept this picture.")
            }
            throw e
        }
        plan.pictureCommitted = true
        PictureResult.Done
    }

    /**
     * Step 2: names (and what goes with them) trade places. The persona slot takes the old
     * default's name, appearance, picture and pronouns; the profile takes the persona's name
     * and appearance. A failure puts back everything this plan changed, the picture included.
     */
    suspend fun swapNames(plan: Plan) = withContext(Dispatchers.IO) {
        guarded(plan) {
            val profile = plan.profile
            // A default that came from a persona slot goes back with its own original file.
            val picture = when {
                plan.origin != null -> plan.origin.personaAvatar
                else -> profile.avatar?.takeIf { it.isNotBlank() }?.let { copyPicture(it, type = "avatar") }
            }
            remote.patchPersona(
                PersonaPatch(
                    id = plan.persona.id,
                    name = plan.defaultName,
                    appearance = profile.appearance.orEmpty(),
                    avatar = picture.orEmpty(),
                    pronouns = plan.origin?.pronouns,
                ),
            )
            plan.slotRewritten = true
            remote.patchMine(buildJsonObject { put("name", JsonPrimitive(plan.persona.name)) }); plan.profileKeys += "name"
            remote.patchMine(buildJsonObject { put("profile", JsonPrimitive(plan.persona.appearance.orEmpty())) }); plan.profileKeys += "profile"
        }
    }

    /**
     * Step 3: Butler remembers what the profile can't hold and makes the new default the one
     * new chats start as. [withoutPicture]: the user went on after a refused picture.
     */
    suspend fun settle(plan: Plan, withoutPicture: Boolean) = withContext(Dispatchers.IO) {
        val after = runCatching { remote.mine() }.getOrNull()
        personas.setDefaultOrigin(
            DefaultOrigin(
                personaAvatar = plan.persona.avatar?.takeIf { it.isNotBlank() },
                pronouns = plan.persona.pronouns,
                profileAvatarAtSwap = after?.avatar,
                // Janitor's profile picture isn't this persona's: Butler draws the default itself.
                pictureInButlerOnly = !plan.pictureCommitted,
                showPicture = !withoutPicture,
            ),
        )
        personas.select(null)
        runCatching { profiles.profile(refresh = true) }
        runCatching { profiles.personas(refresh = true) }
        Unit
    }

    /** Puts back everything this plan changed, newest first, best effort. */
    suspend fun revert(plan: Plan) = withContext(Dispatchers.IO) {
        val profile = plan.profile
        val persona = plan.persona
        if ("profile" in plan.profileKeys) runCatching { remote.patchMine(buildJsonObject { put("profile", JsonPrimitive(profile.appearance.orEmpty())) }) }
        if ("name" in plan.profileKeys) runCatching { remote.patchMine(buildJsonObject { put("name", JsonPrimitive(profile.name)) }) }
        plan.profileKeys.clear()
        if (plan.slotRewritten) {
            runCatching {
                remote.patchPersona(PersonaPatch(persona.id, persona.name, persona.appearance.orEmpty(), persona.avatar.orEmpty(), persona.pronouns))
            }
            plan.slotRewritten = false
        }
        if (plan.pictureCommitted) {
            // The old picture goes back up as a fresh copy (it passed the check before).
            profile.avatar?.takeIf { it.isNotBlank() }?.let { old ->
                runCatching {
                    val file = copyPicture(old, type = "profile-avatar")
                    remote.patchMine(buildJsonObject { put("avatar", JsonPrimitive(file)) })
                }
            }
            plan.pictureCommitted = false
        }
        runCatching { profiles.profile(refresh = true) }
        runCatching { profiles.personas(refresh = true) }
        Unit
    }

    private suspend fun guarded(plan: Plan, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            revert(plan)
            throw e
        }
    }

    /** Downloads a picture and uploads it into [type]'s folder; returns the name to save. */
    private suspend fun copyPicture(url: String, type: String): String {
        val response = plain.newCall(Request.Builder().url(url).get().build()).execute()
        val (bytes, mediaType) = response.use {
            if (!it.isSuccessful) throw ApiError.Api(code = it.code, janitorCode = "BUTLER_PICTURE", serverMessage = "Couldn't fetch a picture to copy.")
            it.body!!.bytes() to (it.header("Content-Type") ?: "image/webp")
        }
        val extension = when {
            "png" in mediaType -> "png"
            "jpeg" in mediaType || "jpg" in mediaType -> "jpg"
            "gif" in mediaType -> "gif"
            else -> url.substringAfterLast('.', "webp").substringBefore('?').takeIf { it.length in 3..4 } ?: "webp"
        }
        val slot = remote.uploadSlot(type, extension)
        val put = Request.Builder().url(slot.url).put(bytes.toRequestBody(mediaType.toMediaType())).build()
        plain.newCall(put).execute().use {
            if (!it.isSuccessful) throw ApiError.Api(code = it.code, janitorCode = "BUTLER_UPLOAD", serverMessage = "Couldn't upload the picture.")
        }
        return slot.filename
    }

    /** The phone's own copy of both, in case anything has to be put back by hand. Never holds config or keys. */
    private fun writeBackup(profile: ProfileDto, persona: PersonaDto, origin: DefaultOrigin?) {
        val backup = Backup(
            profileName = profile.name, profileAppearance = profile.appearance, profileAvatar = profile.avatar,
            personaId = persona.id, personaName = persona.name, personaAppearance = persona.appearance, personaAvatar = persona.avatar,
            defaultOrigin = origin,
        )
        runCatching {
            File(context.filesDir, "persona-swap-${System.currentTimeMillis()}.json")
                .writeText(json.encodeToString(Backup.serializer(), backup))
        }
        // Only the newest few are worth keeping by hand; older swaps are long settled.
        runCatching {
            context.filesDir.listFiles { f -> f.name.startsWith("persona-swap-") && f.name.endsWith(".json") }
                ?.sortedByDescending { it.name.removePrefix("persona-swap-").removeSuffix(".json").toLongOrNull() ?: 0L }
                ?.drop(KEEP_BACKUPS)
                ?.forEach { it.delete() }
        }
    }

    private companion object {
        const val KEEP_BACKUPS = 3
    }
}
