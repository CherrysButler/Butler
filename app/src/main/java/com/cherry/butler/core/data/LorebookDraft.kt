package com.cherry.butler.core.data

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
