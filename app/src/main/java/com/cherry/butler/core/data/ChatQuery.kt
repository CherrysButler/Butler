package com.cherry.butler.core.data

/**
 * What the Chats search box asks: free words, matched against the character and chat names,
 * and at most one `persona:` filter, which keeps only chats played as that persona. A name
 * with spaces goes in quotes (`persona:"Night Owl"`); unquoted, a part of a name is enough.
 */
data class ChatQuery(
    /** The words left once the filter is taken out, trimmed. */
    val text: String,
    /** The persona named after `persona:`, as typed; null when there is no filter. */
    val persona: String?,
    /** Whether the name was given whole (in quotes, as a picked suggestion puts it). */
    val exact: Boolean,
    /** Whether the filter is still being typed: it ends the box, with no space after it yet. */
    val typingPersona: Boolean,
) {
    companion object {
        const val PREFIX = "persona:"
        private val filter = Regex("""(?i)(?<![^\s])persona:(?:"([^"]*)"?|(\S*))""")

        fun parse(box: String): ChatQuery {
            val m = filter.find(box) ?: return ChatQuery(box.trim(), null, exact = false, typingPersona = false)
            val quoted = m.groups[1]
            val name = (quoted?.value ?: m.groups[2]?.value.orEmpty()).trim()
            val closedQuote = quoted != null && m.value.endsWith("\"") && m.value.length > PREFIX.length + 1
            val text = (box.substring(0, m.range.first) + " " + box.substring(m.range.last + 1)).trim().replace(Regex("""\s+"""), " ")
            val atEnd = m.range.last == box.lastIndex
            return ChatQuery(
                text = text,
                persona = name,
                exact = closedQuote,
                typingPersona = atEnd && !closedQuote,
            )
        }

        /** The box with its filter set to [name], whole, and a space to type on after it. */
        fun withPersona(box: String, name: String): String {
            val m = filter.find(box)
            val token = PREFIX + "\"" + name + "\" "
            if (m == null) return (box.trimEnd() + " " + token).trimStart()
            val rest = (box.substring(0, m.range.first) + box.substring(m.range.last + 1)).trim()
            return (token + rest).trimEnd() + if (rest.isEmpty()) " " else ""
        }

        /** The box with an empty filter begun at its end, for the suggestions to fill. */
        fun startPersona(box: String): String =
            if (filter.containsMatchIn(box)) box else (box.trimEnd() + " " + PREFIX).trimStart()
    }
}
