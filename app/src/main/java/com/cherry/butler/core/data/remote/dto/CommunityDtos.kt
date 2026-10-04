package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * The community surface around a character, verified on `/mb` 2026-10-03 (docs/JANITOR_API.md §28):
 * comments (Janitor calls them reviews), their replies, similar characters, published
 * chats and notifications. Every field below was seen on the wire.
 */

/** A comment's author: `user_profiles` on reviews and their replies. */
@Serializable
data class CommentAuthorDto(
    @SerialName("user_name") val userName: String = "",
    val avatar: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

/** `GET /reviews/{characterId}?page=N` → a bare array, 20 a page; a short page is the last. */
@Serializable
data class ReviewDto(
    val id: String,
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_liked_by_user") val isLikedByUser: Boolean = false,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_profiles") val author: CommentAuthorDto? = null,
)

/** `GET /reviews/comments/{reviewId}` → a bare array of replies. */
@Serializable
data class ReviewReplyDto(
    val id: String,
    @SerialName("review_id") val reviewId: String? = null,
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_profiles") val author: CommentAuthorDto? = null,
)

/** `POST /reviews/comment {review_id, content}` — captured on the web (§23.4). */
@Serializable
data class ReviewReplyRequest(
    @SerialName("review_id") val reviewId: String,
    val content: String,
)

/** `GET /chats/public/character/{characterId}` → `{chats, total}`. */
@Serializable
data class PublishedChatsDto(
    val chats: List<PublishedChatDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class PublishedChatDto(
    val id: Long,
    val slug: String = "",
    val title: String? = null,
    val description: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("published_at") val publishedAt: String? = null,
    val publisher: PublisherDto? = null,
    /** `{comment_count, confetti_count, favorite_count, fork_count, view_count}`. */
    val stats: JsonObject? = null,
)

@Serializable
data class PublisherDto(
    @SerialName("user_id") val userId: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

/** `GET /chats/public/{slug}/content`: the read-only transcript of a published chat. */
@Serializable
data class PublishedContentDto(
    val chat: PublishedChatInfoDto? = null,
    val character: PublishedCharacterDto? = null,
    val chatMessages: List<MessageDto> = emptyList(),
    val publisher: PublisherDto? = null,
    val personas: List<PublishedPersonaDto> = emptyList(),
)

@Serializable
data class PublishedChatInfoDto(
    val id: Long,
    val slug: String = "",
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class PublishedCharacterDto(
    val id: String,
    val name: String = "",
    val avatar: String? = null,
    @SerialName("chat_name") val chatName: String? = null,
)

@Serializable
data class PublishedPersonaDto(
    val id: String? = null,
    val name: String = "",
    val avatar: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
)

/** `GET /chats/emoji-definitions` → `{all: [...]}` (verified on `/mb` 2026-10-04). */
@Serializable
data class EmojiDefinitionsDto(val all: List<EmojiDto> = emptyList())

/** One reaction emoji: [id] is what `POST .../react` takes; [img] is a path under the media host. */
@Serializable
data class EmojiDto(
    val id: String,
    val img: String = "",
    val label: String = "",
    @SerialName("sortOrder") val sortOrder: Int = 0,
    @SerialName("isQuick") val isQuick: Boolean = false,
)

/** `GET /chats/public/{id}/viewer` → `{is_favorited, user_reactions}` (verified 2026-10-04). */
@Serializable
data class PublishedViewerDto(
    @SerialName("is_favorited") val isFavorited: Boolean = false,
    /** Shape only seen empty; read defensively (`message_id`, `emoji`). */
    @SerialName("user_reactions") val userReactions: List<JsonObject> = emptyList(),
)

/** `GET /chats/public/{id}/activity` → `{forks_by_message, reactions, stats}` (verified 2026-10-04). */
@Serializable
data class PublishedActivityDto(
    /** Shape only seen empty; read defensively (`message_id`, `emoji`, `count`). */
    val reactions: List<JsonObject> = emptyList(),
    /** `{fork_count, view_count, comment_count, confetti_count, favorite_count}`. */
    val stats: JsonObject? = null,
)

/** `GET /chats/{chatId}/comments?limit&page` → the fifth pagination envelope (§22.3). */
@Serializable
data class ChatCommentsDto(
    val comments: List<ChatCommentDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    @SerialName("has_more") val hasMore: Boolean = false,
)

/** A comment on a published chat (§22.3). Soft-deleted rows arrive with [deletedAt] set. */
@Serializable
data class ChatCommentDto(
    val id: String,
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("message_id") val messageId: Long? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("is_liked_by_user") val isLikedByUser: Boolean = false,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_avatar") val userAvatar: String? = null,
    @SerialName("user_verified") val userVerified: Boolean = false,
)

/** `GET {NOTIFS_BASE}/api/notifications/{userId}` → `{notifications, hasMore}`. */
@Serializable
data class NotificationsDto(
    val notifications: List<NotificationDto> = emptyList(),
    val hasMore: Boolean = false,
)

@Serializable
data class NotificationDto(
    val id: String,
    val subject: String = "",
    val body: String = "",
    val createdAt: String? = null,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    /** One of the 20 workflows in §21.2, e.g. `chat-comment-liked`. */
    val workflow: String? = null,
    /** Workflow-specific: chatSlug, characterName, characterAvatar, commentContent, … */
    val data: JsonObject? = null,
    val redirect: NotificationRedirectDto? = null,
)

@Serializable
data class NotificationRedirectDto(val url: String? = null)

@Serializable
data class NotificationCountDto(val count: Int = 0)
