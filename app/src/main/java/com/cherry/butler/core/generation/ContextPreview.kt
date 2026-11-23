package com.cherry.butler.core.generation

/**
 * What Butler sends with a chat's next reply, for the chat panel's Context page: every part
 * with its size. The character's definition and Janitor's own prompt are added on Janitor's
 * side and never reach the app, so they are not here; [percent] is the share of the model's
 * context the last reply used, as the server reported it, and counts them too.
 */
data class ContextPreview(
    val percent: Int?,
    val memory: String?,
    /** Compact mode: the memory stands in for the lines it summarizes. */
    val memoryReplacesHistory: Boolean,
    val personaName: String,
    val personaAppearance: String,
    val lore: List<Lore>,
    /** Reply length, Butter mode and Lessons, when any is on. */
    val instructions: String?,
    val lines: List<Line>,
) {
    data class Lore(val name: String, val content: String, val constant: Boolean, val chance: Int)
    data class Line(val isBot: Boolean, val text: String)

    val loreChars: Int get() = lore.sumOf { it.content.length }
    val historyChars: Int get() = lines.sumOf { it.text.length }

    companion object {
        /** A rough token count: about 3.7 characters a token for English prose. */
        fun tokens(chars: Int): Int = if (chars <= 0) 0 else maxOf(1, Math.round(chars / 3.7f))
    }
}
