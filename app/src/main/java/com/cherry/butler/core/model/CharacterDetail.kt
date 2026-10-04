package com.cherry.butler.core.model

import com.cherry.butler.core.config.JanitorConfig

/** The full character as the detail screen needs it. */
data class CharacterDetail(
    val id: String,
    val name: String,
    val chatName: String?,
    val avatar: String?,
    val description: String,
    val personality: String?,
    val scenario: String?,
    val exampleDialogs: String?,
    val firstMessages: List<String>,
    val totalTokens: Int,
    val tagNames: List<String>,
    val customTags: List<String>,
    val chatCount: Long,
    val messageCount: Long,
    val creatorId: String,
    val creatorName: String,
    val creatorVerified: Boolean,
    val isNsfw: Boolean,
    val isImageNsfw: Boolean,
    val allowProxy: Boolean,
    val showDefinition: Boolean,
    val createdAt: Long?,
    val lorebooks: List<Lorebook> = emptyList(),
) {
    val avatarUrl: String? get() = JanitorConfig.avatarUrl(avatar)
    val openingMessage: String? get() = firstMessages.firstOrNull()?.takeIf { it.isNotBlank() }
}

/** One of the user's existing chats with a character. */
data class CharacterChat(
    val id: Long,
    val lastMessagePreview: String?,
    val messageCount: Int,
    val updatedAt: Long?,
    val personaId: String?,
)

/** A lorebook (script) attached to a character, as the character page lists it. */
data class Lorebook(
    val id: String,
    val title: String,
    val isPublic: Boolean,
    val updatedAt: Long?,
    val messageCount: Long,
)
