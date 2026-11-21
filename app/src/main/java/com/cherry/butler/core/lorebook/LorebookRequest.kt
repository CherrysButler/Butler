package com.cherry.butler.core.lorebook

import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.markdown.fillUser

object LorebookRequest {
    /** Match the scene as the player sees it, before any request-only instructions are added. */
    fun instructionFor(
        books: List<Lorebook>,
        charLimit: Int,
        userName: String,
        history: List<MessageEntity>,
    ): String? {
        val messages = history.filter { it.serverId != null }.map { it.text.fillUser(userName) }
        val lore = LorebookMatcher.context(books, messages, charLimit)
        if (lore.isEmpty()) return null
        return "(OOC: Use the following world information as context for this scene.\n<lorebook>\n${lore.fillUser(userName)}\n</lorebook>)"
    }
}
