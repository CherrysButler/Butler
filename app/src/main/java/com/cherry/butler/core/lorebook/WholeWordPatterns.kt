package com.cherry.butler.core.lorebook

internal data class LorebookCandidate(val entry: LoreEntry, val scanDepth: Int, val patterns: Map<String, Regex>)

/** A match keeps its candidates even if the chat's books change in the meantime. */
internal class LorebookSelection(candidates: List<LorebookCandidate>) {
    val candidates = candidates.sortedWith(
        compareByDescending<LorebookCandidate> { it.entry.constant }.thenByDescending { it.entry.order },
    )

    companion object {
        fun from(books: List<Lorebook>): LorebookSelection {
            val patterns = WholeWordPatterns()
            return LorebookSelection(books.flatMap { book ->
                book.entries.mapNotNull { patterns.prepare(it, book.scanDepth) }
            })
        }
    }
}

/** Owned by one chat. Each active entry holds one reference per distinct keyword and case mode. */
internal class WholeWordPatterns {
    private data class Key(val keyword: String, val caseSensitive: Boolean)
    private class Counted(val regex: Regex, var references: Int = 0)

    private val compiled = HashMap<Key, Counted>()

    fun prepare(entry: LoreEntry, bookDepth: Int): LorebookCandidate? {
        if (!entry.enabled || entry.content.isBlank() || entry.probability <= 0) return null
        val patterns = if (entry.wholeWords && !entry.constant && entry.keys.isNotEmpty()) {
            buildMap {
                fun addKeyword(keyword: String) {
                    if (containsKey(keyword)) return
                    val counted = compiled.getOrPut(Key(keyword, entry.caseSensitive)) {
                        val pattern = "(?<![\\p{L}\\p{N}_])${Regex.escape(keyword)}(?![\\p{L}\\p{N}_])"
                        Counted(if (entry.caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE))
                    }
                    counted.references++
                    put(keyword, counted.regex)
                }
                entry.keys.forEach(::addKeyword)
                if (entry.selective) entry.secondaryKeys.forEach(::addKeyword)
            }
        } else emptyMap()
        return LorebookCandidate(entry, entry.scanDepth ?: bookDepth, patterns)
    }

    fun release(candidate: LorebookCandidate) {
        for (keyword in candidate.patterns.keys) {
            val key = Key(keyword, candidate.entry.caseSensitive)
            val counted = compiled.getValue(key)
            if (--counted.references == 0) compiled.remove(key)
        }
    }
}
