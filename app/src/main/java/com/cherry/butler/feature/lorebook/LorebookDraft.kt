package com.cherry.butler.feature.lorebook

import com.cherry.butler.core.lorebook.LoreEntry
import com.cherry.butler.core.lorebook.Lorebook
import java.util.UUID

data class EditableLoreEntry(val id: String = UUID.randomUUID().toString(), val entry: LoreEntry)

/** A private working copy. Library bindings and generation keep using the last saved book. */
data class LorebookDraft(
    val original: Lorebook?,
    val id: String = original?.id ?: UUID.randomUUID().toString(),
    val name: String = original?.name.orEmpty(),
    val scanDepth: String = (original?.scanDepth ?: 4).toString(),
    val entries: List<EditableLoreEntry> = original?.entries.orEmpty().map { EditableLoreEntry(entry = it) },
) {
    val dirty: Boolean get() = if (original == null) name.isNotEmpty() || scanDepth != "4" || entries.isNotEmpty()
        else name != original.name || scanDepth != original.scanDepth.toString() || entries.map { it.entry } != original.entries

    fun toBook(): Lorebook {
        require(name.isNotBlank()) { "Give the lorebook a name." }
        require(name.trim().length <= 200) { "Use at most 200 characters for the name." }
        val depth = scanDepth.toIntOrNull()
        require(depth != null && depth in 0..100) { "Scan depth must be between 0 and 100 messages." }
        require(entries.isNotEmpty() && entries.size <= 5000) { "Add 1 to 5000 entries." }
        require(entries.any { it.entry.content.isNotBlank() }) { "Add text content to at least one entry." }
        return Lorebook(id, name.trim(), entries.map { it.entry }, original?.favorite ?: false, depth)
    }

    companion object {
        fun fromImport(book: Lorebook) = LorebookDraft(
            original = null,
            id = book.id,
            name = book.name,
            scanDepth = book.scanDepth.toString(),
            entries = book.entries.map { EditableLoreEntry(entry = it) },
        )
    }
}

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
