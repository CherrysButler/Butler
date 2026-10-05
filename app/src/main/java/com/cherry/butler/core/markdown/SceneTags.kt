package com.cherry.butler.core.markdown

/**
 * Tags a model is asked to wrap parts of its reply in (Butter mode, Highlights): never shown
 * as text. Each has a pair of private-use characters so the markdown parser can carry them
 * through untouched and lift them out as spans afterwards.
 */
enum class SceneTag(val tag: String) {
    Butter("butter"),
    // Highlights' moods. Order matters: each tag's private characters follow from it.
    Romantic("romantic"),
    Erotic("erotic"),
    Dangerous("dangerous"),
    Sad("sad"),
    Funny("funny"),
    ;

    internal val open: Char get() = (BASE + ordinal * 2).toChar()
    internal val close: Char get() = (BASE + ordinal * 2 + 1).toChar()

    companion object {
        /** Clear of the persona marks (U+E000/U+E001, Placeholders.kt). */
        private const val BASE = 0xE100
        fun of(name: String): SceneTag? = entries.firstOrNull { it.tag.equals(name, ignoreCase = true) }
        internal fun ofChar(c: Char): Pair<SceneTag, Boolean>? {
            val i = c.code - BASE
            if (i < 0 || i >= entries.size * 2) return null
            return entries[i / 2] to (i % 2 == 0)
        }
    }
}

object SceneTags {

    private val TAG = Regex("""<(/?)([a-zA-Z]+)\s*>""")
    /** A tag still being streamed at the very end: `<`, `</but`, `<butte`. */
    private val PARTIAL_AT_END = Regex("""<\/?[a-zA-Z]*$""")

    /** [text] with every known tag removed, as Janitor should receive it. */
    fun strip(text: String): String = TAG.replace(text) { m -> if (SceneTag.of(m.groupValues[2]) != null) "" else m.value }

    /** Whether [text] holds a [tag] pair. */
    fun has(text: String, tag: SceneTag): Boolean =
        text.contains("<${tag.tag}>", ignoreCase = true) && text.contains("</${tag.tag}>", ignoreCase = true)

    /** Whether [text] holds any known tag at all. */
    fun hasAny(text: String): Boolean = SceneTag.entries.any { text.contains("<${it.tag}>", ignoreCase = true) }

    /**
     * Known tags turned into their private characters for the parser; a half-streamed tag at
     * the end is dropped so it never flashes on screen.
     */
    fun toMarks(text: String): String {
        val marked = TAG.replace(text) { m ->
            val tag = SceneTag.of(m.groupValues[2]) ?: return@replace m.value
            (if (m.groupValues[1] == "/") tag.close else tag.open).toString()
        }
        val partial = PARTIAL_AT_END.find(marked) ?: return marked
        val name = partial.value.trimStart('<', '/')
        return if (SceneTag.entries.any { it.tag.startsWith(name, ignoreCase = true) }) marked.substring(0, partial.range.first) else marked
    }

    /**
     * Only the [tag] parts of [text], in order, as one passage: the butter version of a reply.
     * Parts from different paragraphs stay apart; parts from one paragraph run on. Null when
     * there are none.
     */
    fun only(text: String, tag: SceneTag): String? {
        val pattern = Regex("<${tag.tag}>(.*?)</${tag.tag}>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val parts = pattern.findAll(text).toList()
        if (parts.isEmpty()) return null
        val out = StringBuilder()
        var lastEnd = -1
        for (m in parts) {
            val piece = strip(m.groupValues[1]).trim()
            if (piece.isEmpty()) continue
            if (out.isNotEmpty()) out.append(if (text.substring(lastEnd, m.range.first).contains("\n\n")) "\n\n" else " ")
            out.append(piece)
            lastEnd = m.range.last + 1
        }
        return out.toString().ifEmpty { null }
    }
}
