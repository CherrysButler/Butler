package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.CharacterPageDto
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The characters you made, and what a creator does with them. Every route and body here is the
 * website's, captured 2026-10-12 (docs/JANITOR_API.md §32):
 *
 * - `GET /characters/v2/mine`: your characters, private ones too, in the browse envelope.
 * - `POST /characters` and `PATCH /characters/{id}` with the whole definition: these two carry
 *   a Cloudflare Turnstile token of their own (`x-character-turnstile-token`), fresh for each
 *   save, which only a person can earn.
 * - `PATCH /characters/{id}` with a switch or two, `POST …/publish`, `PUT /reviews/settings/…`
 *   and `DELETE /characters/{id}` need no token.
 */
@Singleton
class StudioRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = JanitorConfig.BACKEND_BASE
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    enum class Privacy { All, Public, Private }

    enum class Sort(val wire: String, val label: String) {
        Latest("latest", "Newest"),
        Popular("popular", "Most chats"),
    }

    /** One page of your characters (34 a page, 1-indexed, as browsing). */
    suspend fun mine(userId: String, page: Int, privacy: Privacy, sort: Sort, search: String?, tagIds: List<Int>): CharacterPageDto {
        val url = "$base/characters/v2/mine".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.coerceAtLeast(1).toString())
            addQueryParameter("privacyFilter", "all")
            addQueryParameter("search", search?.trim().orEmpty())
            addQueryParameter("sort", sort.wire)
            addQueryParameter("user_id[]", userId)
            when (privacy) {
                Privacy.Public -> addQueryParameter("is_public", "true")
                Privacy.Private -> addQueryParameter("is_public", "false")
                Privacy.All -> Unit
            }
            tagIds.forEach { addQueryParameter("tag_id[]", it.toString()) }
        }.build()
        return apiCall.execute(Request.Builder().url(url).get().build()) { body -> json.decodeFromString(CharacterPageDto.serializer(), body) }
    }

    /** A new character from [body] (see CharacterDraft): 201 and the new row; its id comes back. */
    suspend fun create(body: JsonObject, turnstileToken: String): String {
        val request = Request.Builder()
            .url("$base/characters")
            .header(TURNSTILE_HEADER, turnstileToken)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        return apiCall.execute(request, maxAttempts = 1) { raw -> json.parseToJsonElement(raw).jsonObject.getValue("id").jsonPrimitive.content }
    }

    /** The whole definition saved over [id]'s: answers `{id, name}`. */
    suspend fun update(id: String, body: JsonObject, turnstileToken: String) {
        val request = Request.Builder()
            .url("$base/characters/$id")
            .header(TURNSTILE_HEADER, turnstileToken)
            .patch(body.toString().toRequestBody(jsonMedia))
            .build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /**
     * The switches on a character, one or more at a time: `allow_proxy`, `showdefinition`,
     * `allow_published_chats`. No token: the website sends these as they are flipped.
     */
    suspend fun setSwitches(id: String, switches: Map<String, Boolean>) {
        val body = JsonObject(switches.mapValues { JsonPrimitive(it.value) })
        val request = Request.Builder().url("$base/characters/$id").patch(body.toString().toRequestBody(jsonMedia)).build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** Public or private: `POST /characters/{id}/publish {desired_new_public_state}` → 201, no body. */
    suspend fun setPublic(id: String, public: Boolean) {
        val body = buildJsonObject { put("desired_new_public_state", JsonPrimitive(public)) }
        val request = Request.Builder().url("$base/characters/$id/publish").post(body.toString().toRequestBody(jsonMedia)).build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** Who may comment on [id]. */
    suspend fun commentMode(id: String): CommentMode {
        val request = Request.Builder().url("$base/reviews/settings/$id").get().build()
        return apiCall.execute(request) { raw -> CommentMode.of(json.decodeFromString(CommentSettingsDto.serializer(), raw).commentMode) }
    }

    suspend fun setCommentMode(id: String, mode: CommentMode) {
        val body = buildJsonObject { put("comment_mode", JsonPrimitive(mode.wire)) }
        val request = Request.Builder().url("$base/reviews/settings/$id").put(body.toString().toRequestBody(jsonMedia)).build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** Gone for good: `DELETE /characters/{id}` → `{success: true}`; the page is a 404 after. */
    suspend fun delete(id: String) {
        val request = Request.Builder().url("$base/characters/$id").delete().build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    companion object {
        const val TURNSTILE_HEADER = "x-character-turnstile-token"
    }
}

/** Who may comment on a character, as the website offers it. */
enum class CommentMode(val wire: String, val label: String) {
    Open("open", "Everyone"),
    FollowedOnly("followed_only", "Followers only"),
    Disabled("disabled", "No one"),
    ;

    companion object {
        fun of(wire: String?): CommentMode = entries.firstOrNull { it.wire == wire } ?: Open
    }
}

@Serializable
private data class CommentSettingsDto(@SerialName("comment_mode") val commentMode: String? = null)
