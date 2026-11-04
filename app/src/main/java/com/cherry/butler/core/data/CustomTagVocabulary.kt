package com.cherry.butler.core.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Every creator's tag (`custom_tags`) Butler has seen on characters, and how often, for
 * suggestions as a tag is typed ("ocx" → #ocxcanon, #ocxoc). Janitor has no search for them,
 * so the words come from the characters that pass through Browse, and from a search for what
 * is being typed. Lowercase, without `#`. In memory, plus [snapshot]/[restore] for keeping.
 */
class CustomTagVocabulary {
    private val counts = ConcurrentHashMap<String, Int>()

    fun learn(tags: Collection<String>) {
        for (raw in tags) {
            val tag = normalize(raw) ?: continue
            counts.merge(tag, 1, Int::plus)
        }
        if (counts.size > MAX) {
            // The least seen go first, so the vocabulary stays small and useful.
            counts.entries.sortedBy { it.value }.take(counts.size - MAX).forEach { counts.remove(it.key) }
        }
    }

    /**
     * Up to [limit] tags for what's typed: those that start with it first, then those that
     * have it inside, each most seen first. [exclude] are tags already picked.
     */
    fun suggest(typed: String, limit: Int = 8, exclude: Collection<String> = emptyList()): List<String> {
        val q = normalize(typed) ?: return emptyList()
        val skip = exclude.mapNotNull(::normalize).toSet() + q
        val matches = counts.entries.filter { it.key !in skip && q in it.key }
        return matches.sortedWith(compareBy<Map.Entry<String, Int>> { !it.key.startsWith(q) }.thenByDescending { it.value }.thenBy { it.key })
            .take(limit).map { it.key }
    }

    fun snapshot(): Map<String, Int> = HashMap(counts)

    fun restore(saved: Map<String, Int>) {
        saved.forEach { (k, v) -> normalize(k)?.let { counts.merge(it, v, Int::plus) } }
    }

    companion object {
        const val MAX = 4_000

        fun normalize(raw: String): String? =
            raw.trim().removePrefix("#").lowercase().filterNot { it.isWhitespace() }.takeIf { it.isNotEmpty() }
    }
}
