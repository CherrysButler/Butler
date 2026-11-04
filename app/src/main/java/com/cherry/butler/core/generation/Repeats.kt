package com.cherry.butler.core.generation

/**
 * What a draft repeats from the replies before it, found here rather than asked of the model,
 * which is poor at noticing its own habits ("a mix of", "the air thick with"). Phrases of three
 * to eight words that the draft shares with a recent reply, with at least two words in them
 * that carry meaning, longest first and never overlapping. Agent mode hands them to its check
 * as exact things to reword.
 */
object Repeats {
    private val WORD = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}]+)?""")

    private val COMMON = setOf(
        "a", "an", "the", "and", "or", "but", "of", "to", "in", "on", "at", "for", "with", "by", "from", "as", "into", "onto",
        "is", "are", "was", "were", "be", "been", "being", "am", "it", "its", "it's", "this", "that", "these", "those",
        "i", "you", "he", "she", "we", "they", "me", "him", "her", "us", "them", "my", "your", "his", "our", "their",
        "do", "does", "did", "not", "no", "so", "if", "then", "than", "there", "here", "up", "down", "out", "over", "just",
        "can", "could", "would", "should", "will", "what", "who", "how", "all", "about", "too", "very", "back", "now",
    )

    private data class Token(val word: String, val start: Int, val end: Int)

    private fun tokens(text: String) = WORD.findAll(text).map { Token(it.value.lowercase().replace('’', '\''), it.range.first, it.range.last + 1) }.toList()

    /** Phrases in [draft] that [earlier] replies already used, as written in the draft; at most [limit]. */
    fun find(draft: String, earlier: List<String>, limit: Int = 8): List<String> {
        val d = tokens(draft)
        if (d.size < MIN || earlier.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        for (e in earlier) {
            val w = tokens(e).map { it.word }
            for (n in MIN..MAX) for (i in 0..w.size - n) seen += w.subList(i, i + n).joinToString(" ")
        }
        val taken = BooleanArray(d.size)
        val found = ArrayList<Pair<Int, String>>()
        for (n in MAX downTo MIN) {
            for (i in 0..d.size - n) {
                if ((i until i + n).any { taken[it] }) continue
                val words = d.subList(i, i + n).map { it.word }
                if (words.count { it !in COMMON } < 2) continue
                if (words.joinToString(" ") !in seen) continue
                (i until i + n).forEach { taken[it] = true }
                found += i to draft.substring(d[i].start, d[i + n - 1].end)
            }
        }
        return found.sortedBy { it.first }.map { it.second }.distinctBy { it.lowercase() }.take(limit)
    }

    private const val MIN = 3
    private const val MAX = 8
}
