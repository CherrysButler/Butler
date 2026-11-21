package com.cherry.butler.core.lorebook

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Lorebook(
    val id: String,
    val name: String,
    val entries: List<LoreEntry>,
    val favorite: Boolean = false,
    val scanDepth: Int = 4,
) {
    companion object {
        const val MAX_ENTRIES = 5_000
    }
}

@Serializable
data class LoreEntry(
    val content: String,
    val keys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val constant: Boolean = false,
    val enabled: Boolean = true,
    val selective: Boolean = false,
    @Serializable(with = SecondaryKeywordRuleMapper::class)
    val selectiveLogic: SecondaryKeywordRule = SecondaryKeywordRule.MatchAny,
    val caseSensitive: Boolean = false,
    val wholeWords: Boolean = false,
    val order: Int = 100,
    val scanDepth: Int? = null,
    val probability: Int = 100,
    val name: String = "",
)

object LorebookScope {
    fun chat(id: Long) = "chat:$id"
    fun persona(id: String) = "persona:$id"
    fun persona(id: String?, defaultOwnerId: String?): String? =
        id?.let(::persona) ?: defaultOwnerId?.takeIf { it.isNotBlank() }?.let { "persona:default:$it" }
}

object LorebookMatcher {
    const val DEFAULT_CONTEXT_CHAR_LIMIT = 24_000

    /** History first (newest messages last), then only the entries admitted in the previous round. */
    fun context(
        books: List<Lorebook>,
        messages: List<String>,
        charLimit: Int = DEFAULT_CONTEXT_CHAR_LIMIT,
        roll: () -> Int = { Random.nextInt(100) },
    ): String {
        require(charLimit >= 0) { "Lore character limit cannot be negative." }
        var pending = books.flatMap { book ->
            book.entries.filter { it.enabled && it.content.isNotBlank() && it.probability > 0 }
                .map { Candidate(it, it.scanDepth ?: book.scanDepth) }
        }.sortedWith(compareByDescending<Candidate> { it.entry.constant }.thenByDescending { it.entry.order })
        val history = pending.map { it.scanDepth }.distinct()
            .associateWith { messages.takeLast(it).joinToString("\n") }
        val selected = ArrayList<LoreEntry>()
        var remaining = charLimit
        var previousRoundText: String? = null
        while (pending.isNotEmpty()) {
            val round = activateRound(
                pending = pending,
                textFor = { previousRoundText ?: history.getValue(it.scanDepth) },
                charLimit = charLimit,
                remainingChars = remaining,
                hasSelected = selected.isNotEmpty(),
                roll = roll,
            )
            if (round.activated.isEmpty()) break
            selected += round.activated
            pending = round.pending
            remaining = round.remainingChars
            previousRoundText = round.activated.joinToString("\n") { it.content }
        }
        return selected.sortedBy { it.order }.joinToString("\n\n") { it.content }
    }

    private data class Candidate(val entry: LoreEntry, val scanDepth: Int)
    private data class Round(val pending: List<Candidate>, val activated: List<LoreEntry>, val remainingChars: Int)

    /** Consumes matched candidates once, even when their probability or budget check fails. */
    private fun activateRound(
        pending: List<Candidate>,
        textFor: (Candidate) -> String,
        charLimit: Int,
        remainingChars: Int,
        hasSelected: Boolean,
        roll: () -> Int,
    ): Round {
        val next = ArrayList<Candidate>()
        val activated = ArrayList<LoreEntry>()
        var remaining = remainingChars
        for (candidate in pending) {
            val entry = candidate.entry
            if (!triggered(entry, textFor(candidate))) {
                next += candidate
                continue
            }
            if (entry.probability < 100 && roll() >= entry.probability) continue
            val size = entry.content.length.toLong() + if (hasSelected || activated.isNotEmpty()) 2 else 0
            if (charLimit != 0 && size > remaining) continue
            activated += entry
            if (charLimit != 0) remaining -= size.toInt()
        }
        // Only unmatched candidates survive. Every nonempty round therefore shrinks the search space.
        return Round(next, activated, remaining)
    }

    private fun triggered(entry: LoreEntry, text: String): Boolean {
        // Constants seed the first round without keyword conditions.
        if (entry.constant) return true
        if (entry.keys.none { matches(text, it, entry) }) return false
        if (!entry.selective || entry.secondaryKeys.isEmpty()) return true
        return when (entry.selectiveLogic) {
            SecondaryKeywordRule.MatchAny -> entry.secondaryKeys.any { matches(text, it, entry) }
            SecondaryKeywordRule.NotAll -> !entry.secondaryKeys.all { matches(text, it, entry) }
            SecondaryKeywordRule.MatchNone -> entry.secondaryKeys.none { matches(text, it, entry) }
            SecondaryKeywordRule.MatchAll -> entry.secondaryKeys.all { matches(text, it, entry) }
        }
    }

    private fun matches(text: String, key: String, entry: LoreEntry): Boolean {
        if (!entry.wholeWords) return text.contains(key, ignoreCase = !entry.caseSensitive)
        val options = if (entry.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(key)}(?![\\p{L}\\p{N}_])", options).containsMatchIn(text)
    }
}
