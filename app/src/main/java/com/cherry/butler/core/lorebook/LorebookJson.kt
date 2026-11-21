package com.cherry.butler.core.lorebook

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/** Reads a local lorebook into Butler's model and exports the SillyTavern World Info format. */
object LorebookJson {
    private val json = Json { prettyPrint = true }
    const val MAX_JSON_CHARS = 2_000_000

    fun parse(text: String, name: String = "", fallbackName: String = ""): Lorebook {
        require(text.length <= MAX_JSON_CHARS) { "Lorebook JSON must be under 2 million characters." }
        val root = try {
            json.parseToJsonElement(text.removePrefix("\uFEFF"))
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Invalid lorebook JSON.", e)
        }
        val imported = when (root) {
            is JsonArray -> JanitorLorebookImporter.parse(root)
            is JsonObject -> SillyTavernLorebookImporter.parse(root)
            else -> throw IllegalArgumentException("Expected a SillyTavern lorebook object or a Janitor entry array.")
        }
        return Lorebook(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { imported.name?.trim().orEmpty() }
                .ifEmpty { fallbackName.trim() }.take(200),
            entries = imported.entries,
            scanDepth = imported.scanDepth,
        )
    }

    /** SillyTavern's standalone World Info format, with entries keyed by their numeric UID. */
    fun export(book: Lorebook): String {
        val world = buildJsonObject {
            put("name", book.name)
            put("scan_depth", book.scanDepth)
            putJsonObject("entries") {
                book.entries.forEachIndexed { uid, entry ->
                    putJsonObject(uid.toString()) {
                        put("uid", uid)
                        put("displayIndex", uid)
                        putJsonArray("key") { entry.keys.forEach { add(it) } }
                        putJsonArray("keysecondary") { entry.secondaryKeys.forEach { add(it) } }
                        put("comment", entry.name)
                        put("addMemo", entry.name.isNotBlank())
                        put("content", entry.content)
                        put("constant", entry.constant)
                        put("disable", !entry.enabled)
                        put("selective", entry.selective)
                        put("selectiveLogic", SecondaryKeywordRuleMapper.toCode(entry.selectiveLogic))
                        put("order", entry.order)
                        put("position", 0)
                        put("caseSensitive", entry.caseSensitive)
                        put("matchWholeWords", entry.wholeWords)
                        put("scanDepth", entry.scanDepth)
                        put("probability", entry.probability)
                        put("useProbability", true)
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), world)
    }
}

internal data class ImportedLorebook(
    val entries: List<LoreEntry>,
    val name: String? = null,
    val scanDepth: Int = 4,
)

internal fun parseLorebookEntries(
    entries: List<JsonElement>,
    parseEntry: (JsonObject, String) -> LoreEntry,
): List<LoreEntry> {
    require(entries.size in 1..Lorebook.MAX_ENTRIES) { "A lorebook must contain 1 to ${Lorebook.MAX_ENTRIES} entries." }
    val parsed = entries.mapIndexed { index, value ->
        val label = "Entry ${index + 1}"
        val entry = value as? JsonObject ?: throw IllegalArgumentException("$label must be an object.")
        parseEntry(entry, label)
    }
    require(parsed.any { it.content.isNotBlank() }) { "The lorebook has no text content." }
    return parsed
}

internal fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.stringList(vararg keys: String): List<String>? {
    val value = keys.firstNotNullOfOrNull { this[it] } ?: return null
    require(value is JsonArray) { "Keywords must be arrays of strings." }
    return value.map {
        require(it is JsonPrimitive && it.isString) { "Keywords must be strings." }
        it.content.trim()
    }.filter { it.isNotEmpty() }
}
