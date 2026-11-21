package com.cherry.butler.feature.lorebook

import com.cherry.butler.core.lorebook.LoreEntry

data class LoreEntryForm(
    val value: LoreEntry,
    val keys: String = value.keys.joinToString("\n"),
    val secondaryKeys: String = value.secondaryKeys.joinToString("\n"),
    val order: String = value.order.toString(),
    val scanDepth: String = value.scanDepth?.toString().orEmpty(),
    val probability: String = value.probability.toString(),
) {
    fun entry(): LoreEntry {
        require(value.content.isNotBlank()) { "Add the text that the model should receive." }
        val priority = order.toIntOrNull()
        require(priority != null) { "Priority must be a whole number." }
        val chance = probability.toIntOrNull()
        require(chance != null && chance in 0..100) { "Probability must be between 0 and 100%." }
        val depth = scanDepth.takeIf { it.isNotBlank() }?.toIntOrNull()
        require(scanDepth.isBlank() || (depth != null && depth in 0..100)) { "Scan depth must be blank or between 0 and 100." }
        fun keywords(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val primary = keywords(keys)
        require(!value.enabled || value.constant || primary.isNotEmpty()) { "Add a primary keyword or turn on Always active." }
        return value.copy(keys = primary, secondaryKeys = keywords(secondaryKeys), order = priority, scanDepth = depth, probability = chance)
    }
}

data class LoreEntrySession(val id: String?, val original: LoreEntryForm, val form: LoreEntryForm = original) {
    val dirty get() = form != original
}
