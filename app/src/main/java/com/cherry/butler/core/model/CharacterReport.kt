package com.cherry.butler.core.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Why a character is reported. Janitor knows these four and no others; [wire] is sent as it
 * is written here, to the letter.
 */
enum class ReportReason(val wire: String, val label: String) {
    Spam("spam", "Spam"),
    Stolen("stolen", "Stolen"),
    Illegal("illegal", "Illegal"),
    Other("other", "Other"),
}

/** A report of a character, as the website sends it. */
object CharacterReport {
    /**
     * The character's page on Janitor: the website's own address, id then the name as a slug,
     * as Janitor writes it. What is shared, and what a report points the moderators to.
     */
    fun pageUrl(characterId: String, name: String): String {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60)
        return "https://janitorai.com/characters/$characterId" + (if (slug.isEmpty()) "" else "_character-$slug")
    }

    /** `{character_id, other, reason, url}`: [details] is what the reader wrote, under `other` whatever the reason. */
    fun body(characterId: String, name: String, reason: ReportReason, details: String): JsonObject = buildJsonObject {
        put("character_id", characterId)
        put("other", details.trim())
        put("reason", reason.wire)
        put("url", pageUrl(characterId, name))
    }
}
