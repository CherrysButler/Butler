package com.cherry.butler.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /mb/characters` — verified against the live API 2026-09-23 (see `docs/JANITOR_API.md` §4.1).
 *
 * Every field here was observed on a real response; nothing is guessed. Where the doc's
 * earlier static-analysis guess disagreed, the live shape won.
 */
@Serializable
data class CharacterPageDto(
    val data: List<CharacterDto> = emptyList(),
    val page: Int = 1,
    val size: Int = 0,
    /**
     * A **lower bound**, not a count — it saturates at [paginationLimit] and the server
     * reports `total_relation: "gte"`. Never derive a page count from it; page until a
     * short page comes back.
     */
    val total: Int = 0,
    @SerialName("pagination_limit") val paginationLimit: Int = 0,
    @SerialName("total_relation") val totalRelation: String? = null,
    @SerialName("filtered_total") val filteredTotal: Int = 0,
    /** Only present on `/characters/v2/mine`. */
    @SerialName("top_custom_tags") val topCustomTags: List<String> = emptyList(),
)

@Serializable
data class CharacterDto(
    val id: String,
    val name: String = "",
    val description: String = "",
    /** Differs from [description] on some rows — prefer this one on mobile surfaces. */
    @SerialName("mobileDescription") val mobileDescription: String? = null,
    /** A bare filename, **not** a URL. Resolve via `JanitorConfig.avatarUrl()`. */
    val avatar: String? = null,

    @SerialName("creator_id") val creatorId: String = "",
    /** The name it goes by in chat, what `{{char}}` means; null when the list doesn't carry it. */
    @SerialName("chat_name") val chatName: String? = null,
    @SerialName("creator_name") val creatorName: String = "",
    @SerialName("creator_verified") val creatorVerified: Boolean = false,
    @SerialName("creator_plusbadge") val creatorPlusBadge: Boolean = false,
    /** Null on most rows — the only field observed null across the sample. */
    @SerialName("creator_display_prefs") val creatorDisplayPrefs: CreatorDisplayPrefsDto? = null,

    val stats: CharacterStatsDto = CharacterStatsDto(),
    /** Full objects, unlike [customTags]. Don't merge the two. */
    val tags: List<TagDto> = emptyList(),
    /** Free-text creator tags — plain strings. */
    @SerialName("custom_tags") val customTags: List<String> = emptyList(),

    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("public_chat_count") val publicChatCount: Int = 0,

    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    @SerialName("is_image_nsfw") val isImageNsfw: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("is_force_remove") val isForceRemove: Boolean = false,
    @SerialName("is_proxy_enabled") val isProxyEnabled: Boolean = false,
    val showdefinition: Boolean = false,

    @SerialName("scheduled_publish_at") val scheduledPublishAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Note: no timezone suffix, unlike the other two. */
    @SerialName("first_published_at") val firstPublishedAt: String? = null,
)

@Serializable
data class CharacterStatsDto(
    /** Lifetime totals across all users, not this account's. */
    val chat: Long = 0,
    val message: Long = 0,
)

@Serializable
data class TagDto(
    /** An **int**, unlike character/creator ids which are UUID strings. */
    val id: Int,
    /** Carries a leading emoji; [slug] is the clean key. */
    val name: String = "",
    val slug: String = "",
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreatorDisplayPrefsDto(
    @SerialName("show_plus_badge") val showPlusBadge: Boolean = false,
    @SerialName("show_member_since") val showMemberSince: Boolean = false,
    @SerialName("username_color") val usernameColor: String? = null,
    @SerialName("show_shiny_username") val showShinyUsername: Boolean = false,
)

/** The description a list shows: mobileDescription, the mobile-facing copy, when it has one. */
val CharacterDto.shownDescription: String get() = mobileDescription?.takeIf { it.isNotBlank() } ?: description

/** The creator's own username colour, when they set one. */
val CharacterDto.creatorColorOrNull: String? get() = creatorDisplayPrefs?.usernameColor?.takeIf { it.isNotBlank() }
