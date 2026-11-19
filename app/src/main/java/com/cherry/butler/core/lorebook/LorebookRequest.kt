package com.cherry.butler.core.lorebook

import com.cherry.butler.core.markdown.USER_PLACEHOLDER
import kotlinx.serialization.json.*

/** Runs immediately before transport selection, including retries, alternatives and Continue. */
object LorebookRequest {
    fun prepare(envelope: JsonObject, library: LorebookLibrary): JsonObject {
        if ((envelope["generateMode"] as? JsonPrimitive)?.content !in setOf("NEW", "ALTERNATIVE", "CONTINUE", "SUGGESTION")) return envelope
        val chat = envelope["chat"] as? JsonObject ?: return envelope
        val chatId = (chat["id"] as? JsonPrimitive)?.longOrNull ?: return envelope
        val personaId = (chat["persona_id"] as? JsonPrimitive)?.contentOrNull
        val owner = (chat["user_id"] as? JsonPrimitive)?.contentOrNull
        val enabled = library.enabled(LorebookLibrary.chat(chatId), LorebookLibrary.persona(personaId, owner))
        if (enabled.isEmpty()) return envelope
        val messages = envelope["chatMessages"] as? JsonArray ?: return envelope
        val text = messages.mapNotNull { ((it as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull }
        var lore = LorebookMatcher.context(library.books.filter { it.id in enabled }, text, charLimit = library.contextCharLimit)
        if (lore.isEmpty()) return envelope
        val persona = (envelope["personas"] as? JsonArray)?.filterIsInstance<JsonObject>()
            ?.firstOrNull { (it["id"] as? JsonPrimitive)?.contentOrNull == personaId }
        val profile = envelope["profile"] as? JsonObject
        val name = ((persona ?: profile)?.get("name") as? JsonPrimitive)?.contentOrNull
        if (!name.isNullOrBlank()) lore = USER_PLACEHOLDER.replace(lore, Regex.escapeReplacement(name))
        val instruction = "(OOC: Use the following world information as context for this scene.\n<lorebook>\n$lore\n</lorebook>)"
        val out = messages.toMutableList()
        val lastUser = out.indexOfLast { ((it as? JsonObject)?.get("is_bot") as? JsonPrimitive)?.booleanOrNull == false }
        if (lastUser >= 0) {
            val line = out[lastUser] as JsonObject
            val original = (line["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            out[lastUser] = JsonObject(line + ("message" to JsonPrimitive("$original\n\n$instruction")))
        } else {
            // Also works for an opening/Continue before the first user message.
            out += buildJsonObject {
                put("chat_id", chatId)
                put("is_bot", false)
                put("is_main", true)
                put("message", instruction)
            }
        }
        val refetch = envelope["forcedPromptGenerationCacheRefetch"] as? JsonObject ?: JsonObject(emptyMap())
        return JsonObject(envelope + mapOf(
            "chatMessages" to JsonArray(out),
            "forcedPromptGenerationCacheRefetch" to JsonObject(refetch + ("chat" to JsonPrimitive(true))),
        ))
    }
}
