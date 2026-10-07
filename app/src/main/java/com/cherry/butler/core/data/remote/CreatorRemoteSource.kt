package com.cherry.butler.core.data.remote

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.CharacterPageDto
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** A user's public profile: `GET /profiles/{id}` (website and mobile alike, 2026-10-07). */
@Serializable
data class CreatorProfileDto(
    val id: String,
    @SerialName("user_name") val userName: String = "",
    val avatar: String? = null,
    /** Editor HTML, with inline styles and sometimes `<style>` blocks. */
    @SerialName("about_me") val aboutMe: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    /** A string on the wire ("20612"). */
    @SerialName("followers_count") private val followers: JsonElement? = null,
    val style: CreatorStyleDto? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val followersCount: Long
        get() = runCatching { followers?.jsonPrimitive?.let { it.longOrNull ?: it.content.toLongOrNull() } }.getOrNull() ?: 0L
}

/** The profile's look, as the creator set it. Every field may be missing (`{}` is common). */
@Serializable
data class CreatorStyleDto(
    @SerialName("text_color") val textColor: String? = null,
    @SerialName("background_color") val backgroundColor: String? = null,
    /** A file name; where it is served from is not known yet, so it is not drawn. */
    @SerialName("background_image") val backgroundImage: String? = null,
    @SerialName("background_opacity") val backgroundOpacity: Int? = null,
    /** A stylesheet aimed at the website's profile page (see CreatorCss). */
    @SerialName("custom_style") val customStyle: String? = null,
)

/** One lorebook (Janitor calls them scripts) from `GET /script/user/{id}`. */
@Serializable
data class LorebookDto(
    val id: String,
    val type: String = "lorebook",
    val title: String = "",
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    /** A colour name the website tints the card with ("orange"). */
    val theme: String? = null,
    @SerialName("message_count") val messageCount: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class LorebookPageDto(val scripts: List<LorebookDto> = emptyList(), val total: Int = 0)

/**
 * A creator's page: the profile, their characters (the same search Browse uses, by
 * `user_id[]`, newest first, as the website asks) and their public lorebooks. Routes as the
 * website calls them, verified on the mobile base 2026-10-07.
 */
@Singleton
class CreatorRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = JanitorConfig.BACKEND_BASE

    suspend fun profile(userId: String): CreatorProfileDto =
        get("$base/profiles/$userId") { json.decodeFromString(CreatorProfileDto.serializer(), it) }

    suspend fun characters(userId: String, page: Int): CharacterPageDto {
        val url = "$base/characters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("language", "en")
            .addQueryParameter("sort", "latest")
            .addQueryParameter("user_id[]", userId)
            .build()
        return apiCall.execute(Request.Builder().url(url).get().build()) { json.decodeFromString(CharacterPageDto.serializer(), it) }
    }

    suspend fun lorebooks(userId: String, page: Int): LorebookPageDto =
        get("$base/script/user/$userId?page=${page.coerceAtLeast(1)}") { json.decodeFromString(LorebookPageDto.serializer(), it) }

    private suspend fun <T> get(url: String, decode: (String) -> T): T =
        apiCall.execute(Request.Builder().url(url).get().build()) { decode(it) }
}
