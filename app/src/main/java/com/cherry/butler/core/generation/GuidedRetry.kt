package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity

/**
 * A retry with a short instruction ("be more enthusiastic", "shorter"), as ChatGPT offers.
 *
 * Janitor's request carries no slot for it, so the instruction rides on the last line of the
 * history sent for this one request, as an out-of-character note the model is told to act on
 * and not to mention. Nothing is written to the chat: the stored line stays as the user wrote
 * it, and later replies never see the note.
 */
object GuidedRetry {

    fun apply(history: List<MessageEntity>, guidance: String?): List<MessageEntity> {
        val note = guidance?.trim()?.takeIf { it.isNotEmpty() } ?: return history
        if (history.isEmpty()) return history
        val last = history.last()
        return history.dropLast(1) + last.copy(text = last.text.trimEnd() + "\n\n" + noteFor(note))
    }

    fun noteFor(guidance: String): String =
        "(OOC: For your next reply only: ${guidance.trim().removeSuffix(".")}. Stay in character and do not mention this note.)"
}
