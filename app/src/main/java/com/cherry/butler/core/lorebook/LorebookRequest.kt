package com.cherry.butler.core.lorebook

import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.markdown.fillUser

object LorebookRequest {
    /** Match the scene as the player sees it, before any request-only instructions are added. */
    fun instructionFor(
        library: LorebookLibrary,
        chatId: Long,
        ownerId: String,
        personaId: String?,
        userName: String,
        history: List<MessageEntity>,
    ): String? {
        val enabled = library.enabled(LorebookLibrary.chat(chatId), LorebookLibrary.persona(personaId, ownerId))
        if (enabled.isEmpty()) return null

        val messages = history.filter { it.serverId != null }.map { it.text.fillUser(userName) }
        val lore = LorebookMatcher.context(library.books.filter { it.id in enabled }, messages, library.contextCharLimit)
        if (lore.isEmpty()) return null
        return "(OOC: Use the following world information as context for this scene.\n<lorebook>\n${lore.fillUser(userName)}\n</lorebook>)"
    }
}
