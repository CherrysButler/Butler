package com.cherry.butler.core.generation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The last touch on a payload before it leaves for the user's proxy, after Butler's own
 * changes (OpenRouter options, add-ons, Choices' question, the agent's turns).
 *
 * Janitor assembles the payload as `system`, then the history as alternating `user` and
 * `assistant` lines. Butler can break the alternation: Write for me adds the draft as a user
 * line after a chat that already ends on one, Choices adds its question as a user line, and an
 * unanswered user line meets the next send's. Two lines of one role in a row are read by many
 * models (DeepSeek among them) as a broken turn, and some providers drop or reject them, so
 * each run of same-role lines is joined into one, with a blank line between. System lines are
 * never merged or moved.
 *
 * Janitor's payload also carries `transforms: ["middle-out"]`, left as it is. It is
 * OpenRouter's overflow rule: when a payload is still larger than the model's context, it
 * drops messages from the middle of the history (keeping the start and the end) until it
 * fits, instead of refusing it. Janitor already trims the history to the user's Context
 * setting, so it only acts when that setting is larger than the model's real window; then
 * the dropped middle is silently missing from what the model reads.
 */
object ProxyPayload {

    fun normalized(payload: JsonObject): JsonObject = mergeSameRole(payload)

    /** [payload] with each run of same-role `user` or `assistant` lines joined into one. */
    fun mergeSameRole(payload: JsonObject): JsonObject {
        val messages = (payload["messages"] as? JsonArray) ?: return payload
        val out = ArrayList<JsonElement>(messages.size)
        var merged = false
        for (m in messages) {
            val prev = out.lastOrNull()
            val joined = if (prev != null) join(prev, m) else null
            if (joined != null) {
                out[out.lastIndex] = joined
                merged = true
            } else {
                out += m
            }
        }
        return if (merged) JsonObject(payload + ("messages" to JsonArray(out))) else payload
    }

    /** [a] and [b] as one line, or null when they are not two plain lines of the same turn role. */
    private fun join(a: JsonElement, b: JsonElement): JsonObject? {
        val first = a as? JsonObject ?: return null
        val second = b as? JsonObject ?: return null
        val role = roleOf(first) ?: return null
        if (role !in MERGED || roleOf(second) != role) return null
        // Only plain text lines: parts (pictures), names and tool calls are left as they are.
        if (first.keys != PLAIN || second.keys != PLAIN) return null
        val x = textOf(first) ?: return null
        val y = textOf(second) ?: return null
        val text = when {
            x.isBlank() -> y
            y.isBlank() -> x
            else -> x.trimEnd() + "\n\n" + y.trimStart()
        }
        return JsonObject(first + ("content" to JsonPrimitive(text)))
    }

    private fun roleOf(m: JsonObject): String? = (m["role"] as? JsonPrimitive)?.contentOrNull

    private fun textOf(m: JsonObject): String? = (m["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private val MERGED = setOf("user", "assistant")
    private val PLAIN = setOf("role", "content")
}
