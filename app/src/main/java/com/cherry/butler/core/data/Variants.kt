package com.cherry.butler.core.data

import com.cherry.butler.core.data.local.MessageEntity

/**
 * Which reply of a turn counts. A turn's replies (swipes) are consecutive bot rows.
 *
 * Janitor keeps `is_main: true` on every variant that was ever selected and never unsets
 * one (verified 2026-10-03: two variants of one turn both `main` on the server, and no
 * captured official request ever writes `is_main: false`). So "main" alone is ambiguous;
 * the newest main variant is the chosen one. Butler also writes `false` on the others when
 * the user picks, so its own chats stay unambiguous for every client.
 */
object Variants {

    /** The chosen reply of one turn's rows: the newest main one, else the newest with words. */
    fun pick(group: List<MessageEntity>): MessageEntity =
        group.lastOrNull { it.isMain }
            ?: group.lastOrNull { it.text.isNotEmpty() }
            ?: group.last()

    /**
     * The transcript as the model should read it: every user line, and one reply per turn.
     * [prefer] wins inside its own turn (the reply being continued, or a new variant).
     */
    fun collapse(rows: List<MessageEntity>, prefer: Long? = null): List<MessageEntity> {
        val out = ArrayList<MessageEntity>(rows.size)
        var i = 0
        while (i < rows.size) {
            if (!rows[i].isBot) {
                out += rows[i]
                i++
                continue
            }
            var j = i
            while (j < rows.size && rows[j].isBot) j++
            val group = rows.subList(i, j)
            out += group.firstOrNull { it.localId == prefer } ?: pick(group)
            i = j
        }
        return out
    }
}
