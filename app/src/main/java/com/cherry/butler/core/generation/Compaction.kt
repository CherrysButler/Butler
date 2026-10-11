package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity

/**
 * Compact mode's pure parts: which messages a compact folds into the memory, and what the
 * model is asked.
 *
 * A compact keeps the chat's last messages as they are and folds everything older into the
 * chat's summary, starting from the summary before it, so compacts chain: each one covers
 * the previous memory plus the messages that have since moved out of the kept window. Janitor
 * then sends the summary in place of every message up to `summary_chat_id`, and the messages
 * after it verbatim (docs/JANITOR_API.md §27.4).
 */
object Compaction {

    /** What one compact folds: [fold] in order, and the last of them, which the summary then covers up to. */
    data class Plan(val fold: List<MessageEntity>, val upTo: Long)

    /** A compact that would fold fewer messages than this is not worth its generation. */
    const val MIN_FOLD = 4

    /**
     * The messages to fold, given the chat's main line [history] in order, the [keep] newest to
     * leave as they are, and [coveredUpTo], the last message the current summary covers (null for
     * none). Null when there is too little to fold: the kept window plus what the summary already
     * holds is everything, or near it.
     */
    fun plan(history: List<MessageEntity>, keep: Int, coveredUpTo: Long?, minFold: Int = MIN_FOLD): Plan? {
        val confirmed = history.filter { it.serverId != null }
        val cut = confirmed.size - keep.coerceAtLeast(1)
        if (cut <= 0) return null
        val older = confirmed.subList(0, cut)
        val fold = if (coveredUpTo == null) older else older.filter { it.serverId!! > coveredUpTo }
        if (fold.size < minFold.coerceAtLeast(1)) return null
        return Plan(fold, fold.last().serverId!!)
    }

    /**
     * The compact's instruction, asked after the messages to fold: one memory written from the
     * [previous] one (the last compact or summary, if any) and those messages. It keeps what
     * continuity rests on (names, facts, relationships, open threads, the scene as it stands)
     * and is asked to stay short, since it is sent with every reply from then on.
     */
    fun prompt(previous: String?): String = buildString {
        append("[OOC: Pause the roleplay and do not continue it. Write the story's memory: one summary that will replace ")
        append("the older messages, so the story can go on from it and the most recent messages alone. ")
        val earlier = previous?.trim()?.takeIf { it.isNotEmpty() }
        if (earlier != null) {
            append("Start from the EARLIER MEMORY below and fold the messages above into it; keep everything it holds ")
            append("unless the story has since changed it. ")
        } else {
            append("Cover the messages above, from the start of the story. ")
        }
        append("Keep: every character's name and who they are; facts established about them and the world; ")
        append("relationships and how they have changed; promises, secrets, goals and threads still open; and the current ")
        append("scene: where everyone is, what is happening, what was last said and done. Drop wording, repetition and ")
        append("detail that no longer matters. Write plain past-tense notes under short headings (Characters, ")
        append("Relationships, Events so far, Open threads, Current scene), as short as they can be while keeping all of ")
        append("that, and never more than about 800 words. No roleplay, no commentary, nothing about these instructions. ")
        append("Answer with the memory only.")
        if (earlier != null) append("\n\nEARLIER MEMORY:\n<memory>\n").append(earlier).append("\n</memory>")
        append("]")
    }

    /** The memory as written, with a `<memory>` wrapper the model may echo back taken off. */
    fun clean(text: String): String {
        val t = text.trim()
        val inner = t.removePrefix("<memory>").removeSuffix("</memory>")
        return if (inner.length != t.length) inner.trim() else t
    }
}
