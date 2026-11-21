package com.cherry.butler.core.lorebook

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** SillyTavern World Info files and Character Books, including ones embedded in cards. */
internal object SillyTavernLorebookImporter {
    fun parse(root: JsonObject): ImportedLorebook {
        val book = root["character_book"] as? JsonObject
            ?: (root["data"] as? JsonObject)?.get("character_book") as? JsonObject
            ?: root
        val entries = when (val value = book["entries"]) {
            is JsonObject -> value.values.toList()
            is JsonArray -> value
            else -> throw IllegalArgumentException("The SillyTavern lorebook needs an entries object or array.")
        }
        return ImportedLorebook(
            entries = parseLorebookEntries(entries, ::parseEntry),
            name = book.string("name"),
            scanDepth = (book.int("scan_depth") ?: 4).coerceIn(0, 100),
        )
    }

    private fun parseEntry(entry: JsonObject, label: String): LoreEntry {
        val extensions = entry["extensions"] as? JsonObject
        val content = entry.string("content") ?: throw IllegalArgumentException("$label needs text content.")
        val secondaryKeys = entry.stringList("keysecondary", "secondary_keys").orEmpty()
        return LoreEntry(
            content = content,
            name = entry.string("comment") ?: entry.string("name") ?: "",
            keys = entry.stringList("key", "keys").orEmpty(),
            secondaryKeys = secondaryKeys,
            constant = entry.bool("constant") ?: false,
            enabled = entry.bool("enabled") != false && entry.bool("disable") != true,
            selective = entry.bool("selective") ?: secondaryKeys.isNotEmpty(),
            selectiveLogic = SecondaryKeywordRuleMapper.fromCode(entry.int("selectiveLogic") ?: extensions?.int("selectiveLogic")),
            caseSensitive = entry.bool("caseSensitive") ?: entry.bool("case_sensitive") ?: extensions?.bool("case_sensitive") ?: false,
            wholeWords = entry.bool("matchWholeWords") ?: extensions?.bool("match_whole_words") ?: false,
            order = entry.int("order") ?: entry.int("insertion_order") ?: 100,
            scanDepth = (entry.int("scanDepth") ?: extensions?.int("scan_depth"))?.coerceIn(0, 100),
            probability = if (entry.bool("useProbability") == false) 100
                else (entry.int("probability") ?: extensions?.int("probability") ?: 100).coerceIn(0, 100),
        )
    }
}
