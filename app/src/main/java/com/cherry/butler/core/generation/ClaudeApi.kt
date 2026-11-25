package com.cherry.butler.core.generation

import com.cherry.butler.core.network.ApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Claude's own API (Anthropic's Messages API), for a proxy whose address is Anthropic's
 * (`https://api.anthropic.com/v1/messages`) or any other host's `/v1/messages`. It is not the
 * OpenAI standard the other proxies speak, so the payload Janitor assembled is rewritten on the
 * way out and the answer is read in Claude's own shape:
 *
 * - the key travels as `x-api-key`, with an `anthropic-version`, not as `Authorization`;
 * - the system prompt is a field of its own (`system`), not a line of the chat;
 * - the chat is `user` and `assistant` turns only, and it opens and ends on a `user` turn
 *   (Janitor's opens on the character's greeting, which Claude refuses with a 400);
 * - `max_tokens` has to be there;
 * - `temperature`, `top_p` and `top_k` are gone from this version of the API (a 400 for any
 *   value), and the OpenAI-only fields mean nothing to it, so none of them are sent;
 * - thinking is `thinking: {type: "adaptive"}` with `output_config: {effort}`.
 *
 * Read from Anthropic's API reference, 2026-11-25. Nothing here is Janitor's: the website's
 * own Claude setting sends nothing a browser's network tab shows, and Butler doesn't need it.
 */
object ClaudeApi {

    const val VERSION = "2026-10-21"
    const val URL = "https://api.anthropic.com/v1/messages"

    /** Room for the thinking and the reply when Janitor's payload names no limit. */
    const val DEFAULT_MAX_TOKENS = 16_000

    private const val HOST = "api.anthropic.com"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Whether a proxy at [url] speaks Claude's API rather than the OpenAI standard. */
    fun isClaude(url: String): Boolean {
        val u = url.trim().toHttpUrlOrNull() ?: return false
        val path = u.encodedPath.trimEnd('/')
        // Anthropic also answers the OpenAI standard, at its own address for it.
        if (path.endsWith("/chat/completions")) return false
        return u.host.equals(HOST, ignoreCase = true) || path.endsWith("/v1/messages")
    }

    /** Where the request goes: [url], or Anthropic's messages address when only its host was given. */
    fun endpoint(url: String): String {
        val u = url.trim().toHttpUrlOrNull() ?: return url.trim()
        return if (u.host.equals(HOST, ignoreCase = true) && !u.encodedPath.trimEnd('/').endsWith("/messages")) URL else url.trim()
    }

    /** The request that carries [body] (already in Claude's shape, see [body]) to [url]. */
    fun post(url: String, key: String, body: JsonObject): Request = Request.Builder()
        .url(endpoint(url))
        .post(body.toString().toRequestBody(JSON))
        .header("x-api-key", key)
        .header("anthropic-version", VERSION)
        .build()

    // ---- the request -----------------------------------------------------------------

    /**
     * [payload], an OpenAI chat payload, as a Messages request. [thinking] false leaves the
     * thinking level out, for a model that refused it and for the small jobs that need none.
     */
    fun body(payload: JsonObject, thinking: Boolean = true): JsonObject {
        val system = StringBuilder()
        fun addSystem(text: String) {
            if (text.isBlank()) return
            if (system.isNotEmpty()) system.append("\n\n")
            system.append(text.trim())
        }
        // A payload that already names its system prompt Claude's way keeps it.
        when (val s = payload["system"]) {
            is JsonPrimitive -> addSystem(s.contentOrNull.orEmpty())
            is JsonArray -> s.forEach { part -> addSystem(((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()) }
            else -> Unit
        }

        val turns = ArrayList<Turn>()
        for (m in (payload["messages"] as? JsonArray).orEmpty()) {
            val line = m as? JsonObject ?: continue
            val role = (line["role"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val blocks = blocks(line["content"])
            if (blocks.isEmpty()) continue
            // The system lines that open the payload are the system prompt. One further down
            // (a note Butler or Janitor put at a depth) stays at its depth, as a user line.
            if ((role == "system" || role == "developer") && turns.isEmpty()) {
                blocks.forEach { b -> addSystem((b["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()) }
                continue
            }
            val turn = if (role == "assistant") "assistant" else "user"
            val last = turns.lastOrNull()
            if (last != null && last.role == turn) last.add(blocks) else turns += Turn(turn).also { it.add(blocks) }
        }
        // Claude's chat opens on a user turn and has nothing to answer after its own.
        if (turns.firstOrNull()?.role != "user") turns.add(0, Turn("user").also { it.add(listOf(text(OPENING))) })
        if (turns.last().role != "user") turns += Turn("user").also { it.add(listOf(text(CONTINUE))) }

        return buildJsonObject {
            put("model", (payload["model"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim().removePrefix("anthropic/"))
            put("max_tokens", (payload["max_tokens"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: DEFAULT_MAX_TOKENS)
            if (system.isNotEmpty()) put("system", system.toString())
            put("messages", buildJsonArray { turns.forEach { add(it.json()) } })
            if ((payload["stream"] as? JsonPrimitive)?.booleanOrNull == true) put("stream", true)
            stops(payload["stop"]).takeIf { it.isNotEmpty() }?.let { stops -> put("stop_sequences", JsonArray(stops.map { JsonPrimitive(it) })) }
            if (thinking) when (val effort = effort(payload)) {
                null -> Unit
                OFF -> put("thinking", buildJsonObject { put("type", "disabled") })
                else -> {
                    // Summaries, so the thinking can be shown: left alone it comes back empty.
                    put("thinking", buildJsonObject { put("type", "adaptive"); put("display", "summarized") })
                    put("output_config", buildJsonObject { put("effort", effort) })
                }
            }
        }
    }

    /** Whether [body] asks for a thinking level. */
    fun hasThinking(body: JsonObject): Boolean = "thinking" in body || "output_config" in body

    fun withoutThinking(body: JsonObject): JsonObject = JsonObject(body - "thinking" - "output_config")

    /**
     * Whether a refusal is about the thinking level and nothing else: a model from before
     * adaptive thinking takes neither it nor an effort, and some can't have it switched off.
     * The same request without the level is then worth sending.
     */
    fun refusesThinking(code: Int, answer: String): Boolean =
        code == 400 && listOf("thinking", "output_config", "effort").any { answer.contains(it, ignoreCase = true) }

    /** One turn of the chat, its lines joined: two turns of one role in a row are one turn. */
    private class Turn(val role: String) {
        private val blocks = ArrayList<JsonObject>()

        fun add(more: List<JsonObject>) {
            for (b in more) {
                val last = blocks.lastOrNull()
                val a = last?.let(::textOf)
                val z = textOf(b)
                if (a != null && z != null) blocks[blocks.lastIndex] = text(a.trimEnd() + "\n\n" + z.trimStart()) else blocks += b
            }
        }

        fun json(): JsonObject = buildJsonObject {
            put("role", role)
            val only = blocks.singleOrNull()?.let(::textOf)
            if (only != null) put("content", only) else put("content", JsonArray(blocks))
        }
    }

    private fun text(text: String): JsonObject = buildJsonObject { put("type", "text"); put("text", text) }

    private fun textOf(block: JsonObject): String? =
        (block["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { (block["type"] as? JsonPrimitive)?.contentOrNull == "text" }

    /** An OpenAI line's content as Claude's blocks: its text, and its pictures. Blank text is left out (Claude refuses it). */
    private fun blocks(content: JsonElement?): List<JsonObject> = when (content) {
        is JsonPrimitive -> content.contentOrNull?.takeIf { it.isNotBlank() }?.let { listOf(text(it)) }.orEmpty()
        is JsonArray -> content.mapNotNull { part ->
            val p = part as? JsonObject ?: return@mapNotNull (part as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::text)
            when ((p["type"] as? JsonPrimitive)?.contentOrNull) {
                "text" -> (p["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::text)
                "image_url" -> picture(((p["image_url"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull ?: (p["image_url"] as? JsonPrimitive)?.contentOrNull)
                // Already Claude's own.
                "image" -> p.takeIf { it["source"] is JsonObject }
                else -> null
            }
        }
        else -> emptyList()
    }

    /** A picture as Claude takes one: the bytes of a `data:` address, or the address itself. */
    private fun picture(url: String?): JsonObject? {
        if (url.isNullOrBlank()) return null
        val source = if (url.startsWith("data:")) {
            val type = url.substringAfter("data:").substringBefore(';').ifBlank { "image/jpeg" }
            val data = url.substringAfter("base64,", "").ifBlank { return null }
            buildJsonObject { put("type", "base64"); put("media_type", type); put("data", data) }
        } else {
            buildJsonObject { put("type", "url"); put("url", url) }
        }
        return buildJsonObject { put("type", "image"); put("source", source) }
    }

    private fun stops(stop: JsonElement?): List<String> = when (stop) {
        is JsonPrimitive -> listOfNotNull(stop.contentOrNull)
        is JsonArray -> stop.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }.filter { it.isNotBlank() }

    /** The thinking level the payload carries (the OpenAI standard's field), in Claude's words; [OFF] for none at all. */
    private fun effort(payload: JsonObject): String? = when ((payload["reasoning_effort"] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
        null -> null
        "none" -> OFF
        "minimal", "low" -> "low"
        "medium" -> "medium"
        "high" -> "high"
        "xhigh" -> "xhigh"
        "max" -> "max"
        else -> null
    }

    private const val OFF = "off"

    /** What stands in for the user's turn where Janitor's chat has none: before the greeting, and after a reply to carry on from. */
    private const val OPENING = "[Start]"
    private const val CONTINUE = "[Continue]"

    // ---- the answer ------------------------------------------------------------------

    /**
     * One reply as it streams in. Each of the stream's events is handed to [read], which gives
     * back what it means; a refusal or an error ends the reply with an [ApiError].
     */
    class Stream(private val json: Json) {
        private var wrote = false

        fun read(data: String): List<GenerationEvent> {
            val event = runCatching { json.parseToJsonElement(data.trim()) as? JsonObject }.getOrNull() ?: return emptyList()
            return when ((event["type"] as? JsonPrimitive)?.contentOrNull) {
                "message_start" -> ((event["message"] as? JsonObject)?.get("model") as? JsonPrimitive)?.contentOrNull
                    ?.let { listOf(GenerationEvent.Meta(requestId = null, contextUsagePercent = null, contextWindowFull = null, model = it)) }
                    .orEmpty()
                "content_block_delta" -> {
                    val delta = event["delta"] as? JsonObject
                    when ((delta?.get("type") as? JsonPrimitive)?.contentOrNull) {
                        "text_delta" -> (delta?.get("text") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
                            ?.let { wrote = true; listOf(GenerationEvent.Delta(it)) }.orEmpty()
                        "thinking_delta" -> (delta?.get("thinking") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
                            ?.let { listOf(GenerationEvent.Reasoning(it)) }.orEmpty()
                        else -> emptyList()
                    }
                }
                "message_delta" -> {
                    val stop = ((event["delta"] as? JsonObject)?.get("stop_reason") as? JsonPrimitive)?.contentOrNull
                    // A refusal with nothing written is not a reply to keep, and trying again won't change it.
                    if (stop == "refusal" && !wrote) {
                        throw ApiError.Api(200, janitorCode = "CLAUDE_REFUSAL", serverMessage = "Claude declined to write this reply.", retryable = false)
                    }
                    emptyList()
                }
                "message_stop" -> listOf(GenerationEvent.Done)
                "error" -> throw error(event)
                else -> emptyList()
            }
        }
    }

    /** An error Claude sent in place of, or in the middle of, a reply. */
    private fun error(event: JsonObject): ApiError {
        val e = event["error"] as? JsonObject
        val busy = (e?.get("type") as? JsonPrimitive)?.contentOrNull == "overloaded_error"
        val message = (e?.get("message") as? JsonPrimitive)?.contentOrNull
        return if (busy) ApiError.Server(529) else ApiError.Api(502, janitorCode = "PROXY_ERROR", serverMessage = message ?: "Claude ended the reply with an error.", retryable = false)
    }

    /**
     * A whole answer (one asked for without `stream`) in the OpenAI standard's shape, so the
     * code that reads a proxy's answer reads Claude's too: the text, without the thinking,
     * and `finish_reason: "length"` when the answer ran out of room.
     */
    fun asOpenAi(answer: JsonObject): JsonObject {
        val text = (answer["content"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::textOf) }.joinToString("")
        val stop = (answer["stop_reason"] as? JsonPrimitive)?.contentOrNull
        return buildJsonObject {
            put("choices", buildJsonArray {
                add(buildJsonObject {
                    put("message", buildJsonObject { put("role", "assistant"); put("content", text) })
                    put("finish_reason", if (stop == "max_tokens") "length" else "stop")
                })
            })
        }
    }

    /** One event of a stream as the OpenAI standard's chunk, when it carries reply text; null for every other event. */
    fun asOpenAiChunk(event: JsonObject): JsonObject? {
        val delta = (event["delta"] as? JsonObject)?.takeIf { (event["type"] as? JsonPrimitive)?.contentOrNull == "content_block_delta" } ?: return null
        if ((delta["type"] as? JsonPrimitive)?.contentOrNull != "text_delta") return null
        val text = (delta["text"] as? JsonPrimitive)?.contentOrNull ?: return null
        return buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject { put("delta", buildJsonObject { put("content", text) }) }) })
        }
    }
}
