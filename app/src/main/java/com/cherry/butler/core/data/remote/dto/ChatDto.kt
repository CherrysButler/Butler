package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * Chat wire shapes — verified against `/mb` on 2026-09-23 (docs/JANITOR_API.md §17.2, §18, §24).
 * Chat and message ids are ints on the wire; character, persona and user ids are UUIDs.
 * Nothing here is guessed: every field was observed on a live response.
 */

/** `GET /chats/list` — `{items, page, pageSize, hasMore}`; page while `hasMore`. */
@Serializable
data class ChatListPageDto(
    val items: List<ChatSummaryDto> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val hasMore: Boolean = false,
)

@Serializable
data class ChatSummaryDto(
    val id: Long,
    val character: ChatCharacterRefDto,
    /** UUIDs of the folders this chat is in (verified 2026-10-04; they were wrongly Longs here). */
    @SerialName("folder_ids") val folderIds: List<String> = emptyList(),
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
)

/** The reduced character embedded in a chat list row — six fields, not the full object. */
@Serializable
data class ChatCharacterRefDto(
    val id: String,
    val name: String = "",
    val avatar: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("is_force_removed") val isForceRemoved: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = true,
)

/** `GET /chats/{id}`. */
@Serializable
data class ChatDetailDto(
    val chat: ChatDto,
    val character: ChatCharacterDto,
    val chatMessages: List<MessageDto> = emptyList(),
    @SerialName("fork_source_chat_id") val forkSourceChatId: Long? = null,
    val personas: List<PersonaDto> = emptyList(),
)

@Serializable
data class ChatDto(
    val id: Long,
    @SerialName("character_id") val characterId: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("persona_id") val personaId: String? = null,
    @SerialName("can_publish") val canPublish: Boolean = false,
    @SerialName("published_message_count") val publishedMessageCount: Int? = null,
    @SerialName("published_slug") val publishedSlug: String? = null,
    /** The memory summary; empty string until summarisation has run. */
    val summary: String? = null,
    /** The message id the summary covers up to. */
    @SerialName("summary_chat_id") val summaryChatId: Long? = null,
)

/** The character as embedded in a chat detail — enough to render the transcript header and the opening turn. */
@Serializable
data class ChatCharacterDto(
    val id: String,
    val name: String = "",
    val avatar: String? = null,
    @SerialName("chat_name") val chatName: String? = null,
    val description: String = "",
    @SerialName("first_message") val firstMessage: String? = null,
    /** Janitor leaves empty intro slots as `null` (seen 2026-10-03); they are dropped on use. */
    @SerialName("first_messages") val firstMessages: List<String?> = emptyList(),
    @SerialName("allow_proxy") val allowProxy: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("is_force_removed") val isForceRemoved: Boolean = false,
    @SerialName("is_image_nsfw") val isImageNsfw: Boolean = false,
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("soundcloud_track_id") val soundcloudTrackId: String? = null,
)

@Serializable
data class MessageDto(
    val id: Long,
    @SerialName("chat_id") val chatId: Long,
    @SerialName("character_id") val characterId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_bot") val isBot: Boolean = false,
    /** The swipe selector: true for the variant currently shown. */
    @SerialName("is_main") val isMain: Boolean = true,
    val message: String = "",
    /** Only ever observed as null; the populated shape is unknown, so it is kept raw. */
    val rating: JsonElement? = null,
    val metadata: MessageMetadataDto? = null,
)

@Serializable
data class MessageMetadataDto(
    @SerialName("persona_id") val personaId: String? = null,
    /**
     * Observed as both `[1]` and `["http-…"]` across captures — ints and strings — so it
     * is read as primitives and normalised to strings by [generationRequestIdStrings].
     */
    @SerialName("generation_request_ids") val generationRequestIds: List<JsonPrimitive> = emptyList(),
) {
    val generationRequestIdStrings: List<String>
        get() = generationRequestIds.mapNotNull { it.contentOrNull }
}

// ---- write bodies -------------------------------------------------------------

/** `POST /chats` response — the new chat's id is all the caller needs, the rest is echoed. */
@Serializable
data class ChatCreatedDto(
    val id: Long,
    @SerialName("character_id") val characterId: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("persona_id") val personaId: String? = null,
    val summary: String? = null,
    @SerialName("summary_chat_id") val summaryChatId: Long? = null,
)

/** `PATCH /chats/{id}/messages/{mid}` → `{ "success": true }`. Nothing else comes back. */
@Serializable
data class SuccessDto(val success: Boolean = false)

/**
 * `GET /chats/character/{characterId}/chats?page=` — the user's existing chats with one
 * character. Verified on `/mb` 2026-09-23; yet another envelope shape.
 */
@Serializable
data class CharacterChatsDto(
    val chats: List<CharacterChatDto> = emptyList(),
    val hasMore: Boolean = false,
    val totalChats: Int = 0,
    val creatorId: String? = null,
    val creatorUserName: String? = null,
)

@Serializable
data class CharacterChatDto(
    val id: Long,
    @SerialName("character_id") val characterId: String,
    @SerialName("character_name") val characterName: String? = null,
    @SerialName("character_avatar") val characterAvatar: String? = null,
    @SerialName("character_is_public") val characterIsPublic: Boolean = true,
    /** Message count, despite the name. */
    @SerialName("chat_count") val chatCount: Int = 0,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null,
    @SerialName("persona_id") val personaId: String? = null,
    val summary: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * `POST /chats/{id}/messages` → 201.
 *
 * The web client also sends `_localThinkingContent` / `_localThinkingKey` on bot
 * messages; the server does not return them on read, so they are client-local and
 * deliberately not sent — reasoning lives in Butler's own store.
 */
@Serializable
data class PostMessageRequest(
    @SerialName("is_bot") val isBot: Boolean,
    @SerialName("is_main") val isMain: Boolean,
    val message: String,
    /** Built by the caller so exactly the keys the official client sends are present. */
    val metadata: JsonObject,
    @SerialName("character_id") val characterId: String,
    @SerialName("chat_id") val chatId: Long,
    /** Sent on bot messages by the official client; omitted (null) on user messages. */
    @SerialName("created_at") val createdAt: String? = null,
    val rating: JsonElement? = null,
)

@Serializable
data class MessageMetadataWrite(
    @SerialName("persona_id") val personaId: String? = null,
    @SerialName("generation_request_ids") val generationRequestIds: List<String>? = null,
)

/** A chat folder: `{id, name, chat_count, created_at, updated_at}` (verified 2026-10-04). */
@Serializable
data class FolderDto(
    val id: String,
    val name: String = "",
    @SerialName("chat_count") val chatCount: Int = 0,
)

/** `GET /chats/folders` → `{folders: [...]}`. */
@Serializable
data class FoldersDto(val folders: List<FolderDto> = emptyList())
