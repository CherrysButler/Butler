package com.cherry.butler.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * A character card, the format characters travel in between SillyTavern, Chub, RisuAI and
 * the rest: a PNG of the character with its definition hidden in a text chunk (`chara`, base64
 * JSON; `ccv3` for V3), or the JSON on its own. Read: V1 (six fields at the top level), V2
 * (`chara_card_v2`, the fields under `data`), V3 (`chara_card_v3`, the same plus a nickname),
 * and the old TavernAI names (`char_persona`, `char_greeting`, …). Written: V2, which every
 * app reads. Spec: github.com/malfoyslastname/character-card-spec-v2.
 */
data class CharacterCard(
    val name: String,
    /** V3's name in the chat, when it isn't [name]. */
    val nickname: String = "",
    /** The definition: who the character is. */
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val exampleMessages: String = "",
    /** Notes for the people reading the card (Janitor's public description). */
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "",
    /** How many lorebook entries the card carries (Janitor's characters have no place for them). */
    val lorebookEntries: Int = 0,
) {
    /**
     * What Butler's editor takes: the definition and personality as one personality (Janitor
     * has a single box for both), the first message and the alternates as intros, and the
     * creator's notes as the page's description. [tagIds] are Janitor's tags the card's tags
     * name; the rest stay the creator's own tags.
     */
    fun toDraft(janitorTags: Map<String, Int>): CharacterDraft {
        val matched = tags.mapNotNull { janitorTags[it.trim().lowercase()] }.distinct()
        val own = tags.map { it.trim().removePrefix("#") }
            // "NSFW" is Janitor's switch, not a tag.
            .filter { it.isNotEmpty() && janitorTags[it.lowercase()] == null && !it.equals("nsfw", ignoreCase = true) }
            .distinctBy { it.lowercase() }
        return CharacterDraft(
            name = name.trim(),
            chatName = nickname.trim().takeIf { it != name.trim() }.orEmpty(),
            description = notesHtml(creatorNotes),
            personality = listOf(description.trim(), personality.trim()).filter { it.isNotEmpty() }.joinToString("\n\n"),
            scenario = scenario.trim(),
            exampleDialogs = exampleMessages.trim(),
            firstMessages = (listOf(firstMessage) + alternateGreetings).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") },
            tagIds = matched,
            customTags = own,
            nsfw = tags.any { it.trim().equals("nsfw", ignoreCase = true) },
        )
    }

    /** What the card carries that a Janitor character has nowhere to keep, said plainly. */
    val leftOut: List<String>
        get() = listOfNotNull(
            lorebookEntries.takeIf { it > 0 }?.let { if (it == 1) "its lorebook (1 entry)" else "its lorebook ($it entries)" },
            "its system prompt".takeIf { systemPrompt.isNotBlank() },
            "its post-history instructions".takeIf { postHistoryInstructions.isNotBlank() },
        )

    /** The card as V2 JSON. */
    fun toJson(): JsonObject = buildJsonObject {
        put("spec", "chara_card_v2")
        put("spec_version", "2.0")
        put("data", buildJsonObject {
            put("name", name)
            put("description", description)
            put("personality", personality)
            put("scenario", scenario)
            put("first_mes", firstMessage)
            put("mes_example", exampleMessages)
            put("creator_notes", creatorNotes)
            put("system_prompt", systemPrompt)
            put("post_history_instructions", postHistoryInstructions)
            put("alternate_greetings", buildJsonArray { alternateGreetings.forEach { add(JsonPrimitive(it)) } })
            put("tags", buildJsonArray { tags.forEach { add(JsonPrimitive(it)) } })
            put("creator", creator)
            put("character_version", characterVersion)
            put("extensions", buildJsonObject { if (nickname.isNotBlank()) put("nickname", nickname) })
        })
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /** Whether [bytes] are a PNG. */
        fun isPng(bytes: ByteArray): Boolean = bytes.size >= 8 && bytes.copyOf(8).contentEquals(PNG_SIGNATURE)

        /**
         * The card in [bytes]: a card PNG or its JSON. Throws [IllegalArgumentException] with a
         * sentence for the person when it isn't one.
         */
        fun read(bytes: ByteArray): CharacterCard {
            val text = if (isPng(bytes)) {
                val chunks = textChunks(bytes)
                val payload = chunks["ccv3"] ?: chunks["chara"]
                    ?: throw IllegalArgumentException("That picture has no character card in it. Only card PNGs (from SillyTavern, Chub and the like) carry one.")
                runCatching { String(Base64.getMimeDecoder().decode(payload.trim()), Charsets.UTF_8) }
                    .getOrElse { payload }
            } else {
                String(bytes, Charsets.UTF_8).removePrefix("﻿")
            }
            val root = runCatching { json.parseToJsonElement(text) as JsonObject }
                .getOrElse { throw IllegalArgumentException("That file isn't a character card.") }
            return of(root)
        }

        /** A card from its JSON: V3, V2, V1 or TavernAI's old names. */
        fun of(root: JsonObject): CharacterCard {
            val d = (root["data"] as? JsonObject)?.takeIf { root.str("spec")?.startsWith("chara_card") == true || it.containsKey("name") } ?: root
            fun s(vararg keys: String) = keys.firstNotNullOfOrNull { d.str(it)?.takeIf(String::isNotEmpty) }.orEmpty()
            val name = s("name", "char_name")
            require(name.isNotBlank() || s("description", "char_persona").isNotBlank()) { "That file isn't a character card." }
            val book = d["character_book"] as? JsonObject
            return CharacterCard(
                name = name.ifBlank { "Unnamed" },
                nickname = s("nickname") .ifEmpty { (d["extensions"] as? JsonObject)?.str("nickname").orEmpty() },
                description = s("description", "char_persona"),
                personality = s("personality"),
                scenario = s("scenario", "world_scenario"),
                firstMessage = s("first_mes", "char_greeting"),
                alternateGreetings = d.strings("alternate_greetings"),
                exampleMessages = s("mes_example", "example_dialogue"),
                creatorNotes = s("creator_notes", "creatorcomment"),
                systemPrompt = s("system_prompt"),
                postHistoryInstructions = s("post_history_instructions"),
                tags = d.strings("tags"),
                creator = s("creator"),
                characterVersion = s("character_version"),
                lorebookEntries = (book?.get("entries") as? JsonArray)?.size ?: 0,
            )
        }

        /** [png] with [card] written into it as a V2 card (any card it held before taken out). */
        fun writePng(png: ByteArray, card: CharacterCard): ByteArray {
            require(isPng(png)) { "Not a PNG." }
            val payload = Base64.getEncoder().encodeToString(card.toJson().toString().toByteArray(Charsets.UTF_8))
            val out = ByteArrayOutputStream(png.size + payload.length + 64)
            out.write(PNG_SIGNATURE)
            var i = 8
            while (i + 8 <= png.size) {
                val length = png.int(i)
                val type = String(png, i + 4, 4, Charsets.ISO_8859_1)
                val end = i + 12 + length
                if (end > png.size) break
                val keyword = if (type == "tEXt" || type == "iTXt" || type == "zTXt") keyword(png, i + 8, length) else null
                if (type == "IEND") out.chunk("tEXt", "chara".toByteArray(Charsets.ISO_8859_1) + 0 + payload.toByteArray(Charsets.ISO_8859_1))
                if (keyword != "chara" && keyword != "ccv3") out.write(png, i, end - i)
                i = end
            }
            return out.toByteArray()
        }

        /** Every text chunk's keyword and text (tEXt, iTXt and zTXt, compressed or not). */
        internal fun textChunks(png: ByteArray): Map<String, String> {
            val found = LinkedHashMap<String, String>()
            var i = 8
            while (i + 8 <= png.size) {
                val length = png.int(i)
                if (length < 0 || i + 12 + length > png.size) break
                val type = String(png, i + 4, 4, Charsets.ISO_8859_1)
                val data = png.copyOfRange(i + 8, i + 8 + length)
                val nul = data.indexOf(0)
                if (nul > 0) {
                    val key = String(data, 0, nul, Charsets.ISO_8859_1)
                    val text = when (type) {
                        "tEXt" -> String(data, nul + 1, data.size - nul - 1, Charsets.ISO_8859_1)
                        "zTXt" -> inflate(data.copyOfRange(nul + 2, data.size))?.let { String(it, Charsets.ISO_8859_1) }
                        "iTXt" -> {
                            // keyword \0 flag method language \0 translated \0 text
                            val compressed = data.getOrNull(nul + 1) == 1.toByte()
                            var p = nul + 3
                            repeat(2) { p = data.indexOf(0, p).let { z -> if (z < 0) data.size else z + 1 } }
                            val body = data.copyOfRange(p.coerceAtMost(data.size), data.size)
                            (if (compressed) inflate(body) else body)?.let { String(it, Charsets.UTF_8) }
                        }
                        else -> null
                    }
                    if (text != null && key !in found) found[key] = text
                }
                if (type == "IEND") break
                i += 12 + length
            }
            return found
        }

        /** Janitor's description is the website's HTML; a card's notes are usually plain text. */
        internal fun notesHtml(notes: String): String {
            val t = notes.trim()
            if (t.isEmpty()) return ""
            if (Regex("<(p|br|div|span|b|i|em|strong|a|img|h[1-6])\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return t
            return t.split(Regex("\\n\\s*\\n")).joinToString("") { para ->
                "<p>" + para.trim().escapeHtml().replace("\n", "<br>") + "</p>"
            }
        }

        /** The website's HTML as plain text, for a card's notes. */
        fun plainText(html: String): String = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li)>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull != null }?.contentOrNull
        private fun JsonObject.strings(key: String): List<String> = when (val v: JsonElement? = this[key]) {
            is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> v.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            else -> emptyList()
        }

        private fun ByteArray.int(at: Int): Int =
            ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

        private fun ByteArray.indexOf(b: Int, from: Int = 0): Int {
            for (k in from until size) if (this[k] == b.toByte()) return k
            return -1
        }

        private fun keyword(png: ByteArray, from: Int, length: Int): String? {
            for (k in from until from + length) if (png[k] == 0.toByte()) return String(png, from, k - from, Charsets.ISO_8859_1)
            return null
        }

        private fun inflate(data: ByteArray): ByteArray? = runCatching {
            val inflater = Inflater()
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            inflater.end()
            out.toByteArray()
        }.getOrNull()

        private operator fun ByteArray.plus(b: Int): ByteArray = this + byteArrayOf(b.toByte())

        private fun ByteArrayOutputStream.chunk(type: String, data: ByteArray) {
            val t = type.toByteArray(Charsets.ISO_8859_1)
            val len = data.size
            write(byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()))
            write(t)
            write(data)
            val crc = CRC32().apply { update(t); update(data) }.value
            write(byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte()))
        }
    }
}
