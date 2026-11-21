package com.cherry.butler.core.lorebook

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Janitor's standalone export stores the entries directly in a JSON array. */
internal object JanitorLorebookImporter {
    fun parse(root: JsonArray): ImportedLorebook = ImportedLorebook(parseLorebookEntries(root, ::parseEntry))

    private fun parseEntry(entry: JsonObject, label: String): LoreEntry {
        val content = entry.string("content") ?: throw IllegalArgumentException("$label needs text content.")
        val name = entry.string("comment")?.takeIf { it.isNotBlank() } ?: entry.string("name").orEmpty()
        val keys = entry.stringList("key") ?: rawKeywords(entry, "keysRaw", "keywordsRaw")
        val secondaryKeys = entry.stringList("keysecondary") ?: rawKeywords(entry, "keysecondaryRaw")
        val mode = entry.string("activationMode")
        // Preserve the text of scripted entries for editing without activating them as standard lore.
        val standard = mode == "standard" || (mode == null && entry.string("activationScript").isNullOrBlank())
        return LoreEntry(
            content = content,
            name = name,
            keys = keys,
            secondaryKeys = secondaryKeys,
            constant = entry.bool("constant") ?: false,
            enabled = entry.bool("enabled") != false && standard,
            selective = secondaryKeys.isNotEmpty(),
            selectiveLogic = SecondaryKeywordRuleMapper.fromCode(entry.int("selectiveLogic")),
            caseSensitive = entry.bool("case_sensitive") ?: false,
            wholeWords = entry.bool("matchWholeWords") ?: false,
            order = entry.int("insertion_order") ?: 100,
            scanDepth = entry.int("depth")?.coerceIn(0, 100),
            probability = (entry.int("probability") ?: 100).coerceIn(0, 100),
        )
    }

    private fun rawKeywords(entry: JsonObject, vararg fields: String): List<String> {
        val raw = fields.firstNotNullOfOrNull { entry.string(it)?.takeIf { value -> value.isNotBlank() } }
            ?: return emptyList()
        return raw.split(',', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
