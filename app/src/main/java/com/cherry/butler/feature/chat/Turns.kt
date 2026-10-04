package com.cherry.butler.feature.chat

import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.local.SendJobEntity
import com.cherry.butler.core.data.local.SendJobState

/**
 * The transcript folded into turns. A user line is a turn of its own; consecutive bot
 * rows are one turn whose rows are its variants (swipes), of which one is shown.
 *
 * Which variant is shown, in order: the one being written right now; one whose job is
 * still queued or running; otherwise [Variants.pick] — the newest main one, else the
 * newest with words. That last fallback is the case the official client gets wrong: a
 * reply posted without its `is_main` (a crash between the two calls) is still the reply.
 */
sealed interface Turn {
    val key: String

    data class User(val message: MessageEntity) : Turn {
        override val key: String get() = "u${message.localId}"
    }

    data class Bot(
        val variants: List<MessageEntity>,
        val shown: MessageEntity,
        /** A swipe or continue job attached to this turn rather than to a user line. */
        val job: SendJobEntity?,
        /** Whether a user line comes before it, i.e. whether a new variant can be asked for. */
        val answersUser: Boolean,
    ) : Turn {
        override val key: String get() = "b${variants.first().localId}"
        val index: Int get() = variants.indexOfFirst { it.localId == shown.localId }
    }
}

fun foldTurns(
    messages: List<MessageEntity>,
    jobs: List<SendJobEntity>,
    liveIds: Set<Long>,
): List<Turn> {
    val activeBotRows = jobs.filter { it.state in SendJobState.active }.mapNotNullTo(HashSet()) { it.botMessageLocalId }
    val botJobs = jobs.filter { it.userMessageLocalId == null && it.botMessageLocalId != null }
        .associateBy { it.botMessageLocalId!! }

    val turns = ArrayList<Turn>(messages.size)
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        if (!m.isBot) {
            turns += Turn.User(m)
            i++
            continue
        }
        var j = i
        while (j < messages.size && messages[j].isBot) j++
        val group = messages.subList(i, j)
        // An empty row nobody is writing into is not a variant, it is a failed attempt;
        // its job (if any) still shows under the turn.
        val variants = group.filter { it.text.isNotEmpty() || it.localId in liveIds || it.localId in activeBotRows || !it.thinking.isNullOrEmpty() }
        if (variants.isEmpty()) {
            // Nothing but leftovers of failed attempts: no turn at all. The failure itself
            // is shown under the user's line, where its job belongs.
            i = j
            continue
        }
        val shown = variants.lastOrNull { it.localId in liveIds }
            ?: variants.lastOrNull { it.localId in activeBotRows }
            ?: Variants.pick(variants)
        val job = group.firstNotNullOfOrNull { botJobs[it.localId] }
        turns += Turn.Bot(variants, shown, job, answersUser = i > 0 && !messages[i - 1].isBot)
        i = j
    }
    return turns
}
