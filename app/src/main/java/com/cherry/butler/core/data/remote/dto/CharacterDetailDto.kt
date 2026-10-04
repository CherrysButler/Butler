package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /characters/{id}` — the full character, verified 2026-09-23 (docs/JANITOR_API.md §18.8, §24).
 *
 * Richer than the list row: definition fields, token counts, and a few flags the browse
 * card never needs. Two traps recorded there and honoured here: `example_dialogs` is
 * plural on read, and `custom_tags` is **null** on this endpoint where the list returns `[]`.
 */
@Serializable
data class CharacterDetailDto(
    val id: String,
    val name: String = "",
    val avatar: String? = null,
    @SerialName("raw_avatar") val rawAvatar: String? = null,
    @SerialName("chat_name") val chatName: String? = null,
    val description: String = "",
    val personality: String? = null,
    val scenario: String? = null,
    @SerialName("example_dialogs") val exampleDialogs: String? = null,
    @SerialName("first_message") val firstMessage: String? = null,
    /** Janitor leaves empty intro slots as `null` (seen 2026-10-03); they are dropped on use. */
    @SerialName("first_messages") val firstMessages: List<String?> = emptyList(),
    @SerialName("token_counts") val tokenCounts: TokenCountsDto? = null,

    val tags: List<TagDto> = emptyList(),
    @SerialName("custom_tags") val customTags: List<String>? = null,
    val stats: CharacterStatsDto = CharacterStatsDto(),
    /** Lorebooks and other scripts attached to the character (verified 2026-10-03). */
    val scripts: List<ScriptDto> = emptyList(),

    @SerialName("creator_id") val creatorId: String = "",
    @SerialName("creator_name") val creatorName: String = "",
    @SerialName("creator_verified") val creatorVerified: Boolean = false,
    @SerialName("creator_plusbadge") val creatorPlusBadge: Boolean = false,
    @SerialName("creator_display_prefs") val creatorDisplayPrefs: CreatorDisplayPrefsDto? = null,

    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    @SerialName("is_image_nsfw") val isImageNsfw: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("is_force_remove") val isForceRemove: Boolean = false,
    @SerialName("is_explicit_for_anon") val isExplicitForAnon: Boolean = false,
    @SerialName("allow_proxy") val allowProxy: Boolean = true,
    @SerialName("allow_published_chats") val allowPublishedChats: Boolean = true,
    val showdefinition: Boolean = false,
    @SerialName("showDefinitionOverride") val showDefinitionOverride: Boolean = false,

    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("first_published_at") val firstPublishedAt: String? = null,
)

@Serializable
data class TokenCountsDto(
    @SerialName("personality_tokens") val personality: Int = 0,
    @SerialName("scenario_tokens") val scenario: Int = 0,
    @SerialName("example_dialog_tokens") val exampleDialog: Int = 0,
    @SerialName("first_message_tokens") val firstMessage: Int = 0,
    @SerialName("total_tokens") val total: Int = 0,
)

@Serializable
data class ScriptDto(
    val id: String,
    /** `lorebook` on every sample so far. */
    val type: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("message_count") val messageCount: Long = 0,
)
