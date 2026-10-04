package com.cherry.butler.core.stream

/**
 * An incremental parser for the XML-ish tags a model is asked to emit inside its reply —
 * `<think>` today; `<butter>` and highlight tags later are just registry entries.
 *
 * Built for a stream, which is the only reason it is not a regex:
 *
 * - A tag can arrive split across chunks (`<thi` … `nk>`). Nothing is emitted for a
 *   possible tag until it resolves, so the screen never flashes a half-written `<thi`.
 * - Only registered names are tags. `<3`, `<grins>`, and every other angle bracket the
 *   model writes pass through as text, because destroying content is worse than
 *   rendering a bracket.
 * - A `<` that does not turn into a tag within [MAX_TAG_LENGTH] characters, or that meets
 *   a newline first, is released as text. The parser can never hold text hostage.
 * - [finish] flushes whatever is pending, so a stream that ends mid-tag loses nothing.
 */
class TagStreamParser(knownTags: Collection<String>) {

    sealed interface Event {
        data class Text(val text: String) : Event
        data class Open(val tag: String) : Event
        data class Close(val tag: String) : Event
    }

    private val known = knownTags.map { it.lowercase() }.toHashSet()
    private val pending = StringBuilder()
    private var inTag = false

    fun feed(chunk: CharSequence): List<Event> {
        if (chunk.isEmpty()) return emptyList()
        val events = ArrayList<Event>(2)
        val text = StringBuilder()

        fun emitText() {
            if (text.isNotEmpty()) {
                events += Event.Text(text.toString())
                text.setLength(0)
            }
        }

        for (c in chunk) {
            if (!inTag) {
                if (c == '<') {
                    emitText()
                    inTag = true
                    pending.setLength(0)
                    pending.append(c)
                } else {
                    text.append(c)
                }
                continue
            }

            val prev = pending.lastOrNull()
            pending.append(c)
            when {
                c == '>' -> {
                    inTag = false
                    val match = TAG.matchEntire(pending)
                    val name = match?.groupValues?.get(2)?.lowercase()
                    if (match != null && name in known) {
                        // Text that preceded the tag must reach the collector first.
                        emitText()
                        events += if (match.groupValues[1].isEmpty()) Event.Open(name!!) else Event.Close(name!!)
                    } else {
                        text.append(pending)
                    }
                    pending.setLength(0)
                }
                // A tag name never contains whitespace (`< b`, `<3 forever`). Only a
                // trailing space run before `>` is tolerated, so a non-space after a
                // space means this was never a tag and the reader gets it back now.
                (c.isWhitespace() && pending.length == 2) ||
                    (prev != null && prev.isWhitespace() && !c.isWhitespace() && c != '>') ||
                    c == '\n' || c == '<' || pending.length > MAX_TAG_LENGTH -> {
                    // Not a tag after all. Release it, and if this char opened a new
                    // candidate, start over from it.
                    inTag = false
                    if (c == '<') {
                        pending.setLength(pending.length - 1)
                        text.append(pending)
                        inTag = true
                        pending.setLength(0)
                        pending.append('<')
                    } else {
                        text.append(pending)
                        pending.setLength(0)
                    }
                }
            }
        }
        emitText()
        return events
    }

    /** End of stream: anything still pending was never a tag. */
    fun finish(): List<Event> {
        if (!inTag || pending.isEmpty()) return emptyList()
        inTag = false
        val leftover = pending.toString()
        pending.setLength(0)
        return listOf(Event.Text(leftover))
    }

    fun reset() {
        inTag = false
        pending.setLength(0)
    }

    private companion object {
        const val MAX_TAG_LENGTH = 32
        val TAG = Regex("""^<(/?)([A-Za-z][A-Za-z0-9_-]*)\s*/?>$""")
    }
}
