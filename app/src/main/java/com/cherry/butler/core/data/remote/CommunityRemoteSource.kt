package com.cherry.butler.core.data.remote

import com.cherry.butler.core.data.remote.dto.ChatCommentsDto
import com.cherry.butler.core.data.remote.dto.PublishedActivityDto
import com.cherry.butler.core.data.remote.dto.PublishedViewerDto
import com.cherry.butler.core.data.remote.dto.EmojiDefinitionsDto
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.CharacterDto
import com.cherry.butler.core.data.remote.dto.NotificationCountDto
import com.cherry.butler.core.data.remote.dto.NotificationsDto
import com.cherry.butler.core.data.remote.dto.PublishedChatsDto
import com.cherry.butler.core.data.remote.dto.PublishedContentDto
import com.cherry.butler.core.data.remote.dto.ReviewDto
import com.cherry.butler.core.data.remote.dto.ReviewReplyDto
import com.cherry.butler.core.data.remote.dto.ReviewReplyRequest
import com.cherry.butler.core.network.ApiCall
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The community around a character: comments and replies, similar characters, published
 * chats, and the notifications feed. Routes verified on the mobile base 2026-10-03.
 */
@Singleton
class CommunityRemoteSource @Inject constructor(
    private val apiCall: ApiCall,
    private val json: Json,
) {
    private val base = JanitorConfig.BACKEND_BASE
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun comments(characterId: String, page: Int): List<ReviewDto> =
        get("$base/reviews/$characterId?page=${page.coerceAtLeast(1)}") {
            json.decodeFromString(ListSerializer(ReviewDto.serializer()), it)
        }

    suspend fun replies(reviewId: String): List<ReviewReplyDto> =
        get("$base/reviews/comments/$reviewId") {
            json.decodeFromString(ListSerializer(ReviewReplyDto.serializer()), it)
        }

    /** Replies to a comment. The shape was captured on the web; it posts publicly as the user. */
    suspend fun reply(reviewId: String, content: String) {
        val body = json.encodeToString(ReviewReplyRequest.serializer(), ReviewReplyRequest(reviewId, content))
        val request = Request.Builder().url("$base/reviews/comment").post(body.toRequestBody(jsonMedia)).build()
        // One attempt: a retried POST could publish the reply twice.
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** `POST /reviews {character_id, content, is_like}` → 201 (§23.1). Once: a retry could post twice. */
    suspend fun postComment(characterId: String, content: String, isLike: Boolean) =
        post("$base/reviews", buildJsonObject {
            put("character_id", characterId)
            put("content", content)
            put("is_like", isLike)
        })

    /** `DELETE /reviews/{reviewId}` → 200 (§23.1). */
    suspend fun deleteComment(reviewId: String) {
        apiCall.execute(Request.Builder().url("$base/reviews/$reviewId").delete().build(), maxAttempts = 1) { }
    }

    suspend fun similar(characterId: String): List<CharacterDto> =
        get("$base/characters/$characterId/similar") {
            json.decodeFromString(ListSerializer(CharacterDto.serializer()), it)
        }

    suspend fun publishedChats(characterId: String): PublishedChatsDto =
        get("$base/chats/public/character/$characterId") { json.decodeFromString(PublishedChatsDto.serializer(), it) }

    suspend fun publishedChat(slug: String): PublishedContentDto =
        get("$base/chats/public/$slug/content") { json.decodeFromString(PublishedContentDto.serializer(), it) }

    // ---- published chats: reactions, comments, favourite (docs/JANITOR_API.md §22, §23.4, §31) ----

    suspend fun emojiDefinitions(): EmojiDefinitionsDto =
        get("$base/chats/emoji-definitions") { json.decodeFromString(EmojiDefinitionsDto.serializer(), it) }

    suspend fun publishedViewer(chatId: Long): PublishedViewerDto =
        get("$base/chats/public/$chatId/viewer") { json.decodeFromString(PublishedViewerDto.serializer(), it) }

    suspend fun publishedActivity(chatId: Long): PublishedActivityDto =
        get("$base/chats/public/$chatId/activity") { json.decodeFromString(PublishedActivityDto.serializer(), it) }

    /** Counts a read, as the website does on open. Best effort. */
    suspend fun registerView(chatId: Long) = post("$base/chats/public/$chatId/view", JsonObject(emptyMap()))

    /**
     * `POST /chats/public/{id}/react {emoji, message_id}` → 201 `{success}`; `DELETE` with the same
     * body takes it back → 200 `{success}` (both verified on `/mb` 2026-10-04). POST is not a
     * toggle: a second one leaves the reaction in place.
     */
    suspend fun react(chatId: Long, messageId: Long, emojiId: String, on: Boolean) {
        val body = buildJsonObject { put("emoji", emojiId); put("message_id", messageId) }.toString().toRequestBody(jsonMedia)
        val b = Request.Builder().url("$base/chats/public/$chatId/react")
        val request = if (on) b.post(body).build() else b.delete(body).build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    /** `POST` / `DELETE /chats/public/{id}/favorite` (§31). */
    suspend fun setPublishedFavorite(chatId: Long, on: Boolean) {
        val b = Request.Builder().url("$base/chats/public/$chatId/favorite")
        val request = if (on) b.post(JsonObject(emptyMap()).toString().toRequestBody(jsonMedia)).build() else b.delete().build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    suspend fun chatComments(chatId: Long, page: Int, limit: Int = 20): ChatCommentsDto =
        get("$base/chats/$chatId/comments?limit=$limit&page=${page.coerceAtLeast(1)}") { json.decodeFromString(ChatCommentsDto.serializer(), it) }

    /** `POST /chats/{chatId}/comments {content}` → 201 (§23.4). A top-level comment sends content alone. */
    suspend fun postChatComment(chatId: Long, content: String) =
        post("$base/chats/$chatId/comments", buildJsonObject { put("content", content) })

    /**
     * `POST /chats/comments/{id}/like` → 201 `{like_count, message: "Comment liked"}`; `DELETE` the same
     * path → 200 `{like_count, message: "Comment unliked"}` (verified 2026-10-04). POST is not a
     * toggle. Returns the count Janitor now holds.
     */
    suspend fun likeChatComment(commentId: String, on: Boolean): Int? {
        val b = Request.Builder().url("$base/chats/comments/$commentId/like")
        val request = if (on) b.post(JsonObject(emptyMap()).toString().toRequestBody(jsonMedia)).build() else b.delete().build()
        return apiCall.execute(request, maxAttempts = 1) { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject["like_count"]?.jsonPrimitive?.intOrNull }.getOrNull()
        }
    }

    /** `DELETE /chats/comments/{id}` → 200 `{success}` on the user's own comment (verified 2026-10-04). */
    suspend fun deleteChatComment(commentId: String) {
        apiCall.execute(Request.Builder().url("$base/chats/comments/$commentId").delete().build(), maxAttempts = 1) { }
    }

    suspend fun notifications(userId: String): NotificationsDto =
        get("${JanitorConfig.NOTIFS_BASE}/api/notifications/$userId") { json.decodeFromString(NotificationsDto.serializer(), it) }

    suspend fun unreadCount(userId: String): Int =
        get("${JanitorConfig.NOTIFS_BASE}/api/notifications/count/$userId") { json.decodeFromString(NotificationCountDto.serializer(), it).count }

    /** `POST .../read-all/{userId}` → `{success: true}` (verified on the mobile path 2026-10-04). */
    suspend fun markAllRead(userId: String) =
        post("${JanitorConfig.NOTIFS_BASE}/api/notifications/read-all/$userId", JsonObject(emptyMap()))

    /**
     * `{preferences: [{workflow, active, ...}]}`. Only overrides are stored; a workflow missing
     * from the list is on (§21.2).
     */
    suspend fun notificationPreferences(userId: String): Map<String, Boolean> =
        get("${JanitorConfig.NOTIFS_BASE}/api/preferences/$userId") { raw ->
            json.parseToJsonElement(raw).jsonObject["preferences"]?.jsonArray.orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val workflow = o["workflow"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                workflow to (o["active"]?.jsonPrimitive?.booleanOrNull ?: true)
            }.toMap()
        }

    /** `PUT .../preferences/{userId}/{workflow} {active}`. */
    suspend fun setNotificationPreference(userId: String, workflow: String, active: Boolean) {
        val request = Request.Builder()
            .url("${JanitorConfig.NOTIFS_BASE}/api/preferences/$userId/$workflow")
            .put(buildJsonObject { put("active", active) }.toString().toRequestBody(jsonMedia))
            .build()
        apiCall.execute(request, maxAttempts = 1) { }
    }

    // ---- favourite, follow, like (bodies from the website's own code, docs/JANITOR_API.md §31) ----

    /** `GET /favorites/myfavorites/{id}` answers a bare `true`/`false`. */
    suspend fun isFavorite(characterId: String): Boolean = get("$base/favorites/myfavorites/$characterId") { flag(it) ?: false }

    /** `{characterId, favoritesCount}`. */
    suspend fun favoriteCount(characterId: String): Int = get("$base/favorites/character/$characterId/count") { raw ->
        json.parseToJsonElement(raw).jsonObject["favoritesCount"]?.jsonPrimitive?.intOrNull ?: 0
    }

    suspend fun setFavorite(characterId: String, on: Boolean) =
        post("$base/favorites/${if (on) "favorite" else "unfavorite"}", buildJsonObject { put("characterId", characterId) })

    suspend fun isFollowing(userId: String): Boolean = get("$base/following/myfollowing/$userId") { flag(it) ?: false }

    suspend fun setFollowing(userId: String, on: Boolean) =
        post("$base/following/${if (on) "follow" else "unfollow"}", buildJsonObject { put("userId", userId) })

    /** One call toggles a comment's like; the answer says where it landed, when it says. */
    suspend fun toggleCommentLike(reviewId: String): Boolean? {
        val request = Request.Builder().url("$base/reviews/like/review/$reviewId")
            .post(buildJsonObject { put("data", "") }.toString().toRequestBody(jsonMedia)).build()
        return apiCall.execute(request, maxAttempts = 1) { flag(it) }
    }

    private suspend fun post(url: String, body: JsonObject) {
        apiCall.execute(Request.Builder().url(url).post(body.toString().toRequestBody(jsonMedia)).build(), maxAttempts = 1) { }
    }

    /** `true`, `false`, or the first boolean in an object (`{liked: true}` and the like). */
    private fun flag(raw: String): Boolean? {
        val e = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        (e as? JsonPrimitive)?.booleanOrNull?.let { return it }
        return (e as? JsonObject)?.values?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.booleanOrNull }
    }

    private suspend fun <T> get(url: String, decode: (String) -> T): T =
        apiCall.execute(Request.Builder().url(url).get().build()) { decode(it) }
}
