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
) {
    val avatarUrl: String? get() = JanitorConfig.avatarUrl(characterAvatar)
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
) {
    val avatarUrl: String? get() = JanitorConfig.avatarUrl(characterAvatar)
}
