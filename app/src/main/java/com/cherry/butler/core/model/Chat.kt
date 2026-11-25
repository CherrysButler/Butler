package com.cherry.butler.core.model

import com.cherry.butler.core.config.JanitorConfig

/** A row on the chats tab. */
data class ChatSummary(
    val id: Long,
    val characterId: String,
    val characterName: String,
    val characterAvatar: String?,
    val characterDeleted: Boolean,
    val lastMessageAt: Long?,
    val lastMessagePreview: String?,
    val messageCount: Int,
    /** Fills `{{user}}`; null when the chat's detail has not been mirrored yet. */
    val personaName: String?,
    /** The chat's own name, when Janitor has told of one; lists fall back to the character's. */
    val title: String? = null,
    /** Who it is played as: null is the profile itself. */
    val personaId: String? = null,
    val personaAvatar: String? = null,
    /** The name the character goes by in chat, where known: what `{{char}}` in a preview stands for. */
    val chatName: String? = null,
) {
    val avatarUrl: String? get() = JanitorConfig.avatarUrl(characterAvatar)
    val personaAvatarUrl: String? get() = JanitorConfig.personaAvatarUrl(personaAvatar)
    /** The name to put in place of `{{char}}`. */
    val nameInChat: String get() = chatName?.takeIf { it.isNotBlank() } ?: characterName
    /** What the chat is called in a list: its own name, else the character's. */
    val shownTitle: String get() = title?.takeIf { it.isNotBlank() } ?: characterName
}

/** A character on the chats tab, with everything the user has said to them folded in. */
data class ChatGroup(
    val characterId: String,
    val characterName: String,
    val characterAvatar: String?,
    val characterDeleted: Boolean,
    val latestChatId: Long,
    val lastMessageAt: Long?,
    val lastMessagePreview: String?,
    val personaName: String?,
    val chatCount: Int,
    val pinned: Boolean = false,
    /** The name it goes by in chat, where known: what `{{char}}` in a preview stands for. */
    val chatName: String? = null,
) {
    val avatarUrl: String? get() = JanitorConfig.avatarUrl(characterAvatar)
    /** The name to put in place of `{{char}}`. */
    val nameInChat: String get() = chatName?.takeIf { it.isNotBlank() } ?: characterName
}
