package com.cherry.butler.core.generation

import com.cherry.butler.core.network.ApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ClaudeApiTest {

    private val json = Json

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun turns(body: JsonObject): List<Pair<String, String>> = body["messages"]!!.jsonArray.map {
        val m = it.jsonObject
        m["role"]!!.jsonPrimitive.content to ((m["content"] as? JsonPrimitive)?.content ?: "<blocks>")
    }

    @Test
    fun `which proxies speak Claude's own API`() {
        assertTrue(ClaudeApi.isClaude("https://api.anthropic.com/v1/messages"))
        assertTrue(ClaudeApi.isClaude(" https://api.anthropic.com/v1 "))
        // A gateway that speaks it at its own address.
        assertTrue(ClaudeApi.isClaude("https://gateway.example/anthropic/v1/messages"))
        // Anthropic's own address for the OpenAI standard is the OpenAI standard.
        assertFalse(ClaudeApi.isClaude("https://api.anthropic.com/v1/chat/completions"))
        assertFalse(ClaudeApi.isClaude("https://openrouter.ai/api/v1/chat/completions"))
        assertFalse(ClaudeApi.isClaude("https://evil.example/api.anthropic.com"))
        assertFalse(ClaudeApi.isClaude("not an address"))
        // Only the host given: the messages address is filled in.
        assertEquals(ClaudeApi.URL, ClaudeApi.endpoint("https://api.anthropic.com/v1"))
        assertEquals("https://gateway.example/v1/messages", ClaudeApi.endpoint("https://gateway.example/v1/messages"))
    }

    @Test
    fun `the key goes as x-api-key, never as Authorization`() {
        val request = ClaudeApi.post("https://api.anthropic.com/v1/messages", "k", obj("""{"model":"m"}"""))
        assertEquals("k", request.header("x-api-key"))
        assertEquals(ClaudeApi.VERSION, request.header("anthropic-version"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun `Janitor's payload in Claude's shape`() {
        val body = ClaudeApi.body(
            obj(
                """{"model":"claude-haiku-5-5","stream":true,"temperature":0.8,"top_p":1,"top_k":0,"max_tokens":0,
                "frequency_penalty":0,"repetition_penalty":1,"transforms":["middle-out"],
                "messages":[
                  {"role":"system","content":"You write Mira."},
                  {"role":"system","content":"Mira is a baker."},
                  {"role":"assistant","content":"*Mira waves.*"},
                  {"role":"user","content":"Hi"},
                  {"role":"assistant","content":"Hello!"},
                  {"role":"system","content":"[Note: it is raining]"},
                  {"role":"user","content":"Nice day?"}
                ]}""",
            ),
        )
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        // The system lines that open the payload are the system prompt, in a field of their own.
        assertEquals("You write Mira.\n\nMira is a baker.", body["system"]!!.jsonPrimitive.content)
        // The chat opens on a user turn; a system line further down stays at its depth, in the user's turn.
        assertEquals(
            listOf("user" to "[Start]", "assistant" to "*Mira waves.*", "user" to "Hi", "assistant" to "Hello!", "user" to "[Note: it is raining]\n\nNice day?"),
            turns(body),
        )
        // No limit named: one is, since Claude needs it.
        assertEquals(ClaudeApi.DEFAULT_MAX_TOKENS, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals("true", body["stream"]!!.jsonPrimitive.content)
        // What this API refuses, and what means nothing to it, is not sent.
        assertEquals(setOf("model", "max_tokens", "system", "messages", "stream"), body.keys)
    }

    @Test
    fun `a chat that ends on the character's turn gets a turn to answer`() {
        val body = ClaudeApi.body(obj("""{"model":"m","max_tokens":300,"messages":[{"role":"user","content":"Hi"},{"role":"assistant","content":"Hel"}]}"""))
        assertEquals(listOf("user" to "Hi", "assistant" to "Hel", "user" to "[Continue]"), turns(body))
        assertEquals(300, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertFalse("stream" in body)
    }

    @Test
    fun `blank lines are left out, and a maker's name on the model too`() {
        val body = ClaudeApi.body(obj("""{"model":"anthropic/claude-opus-5-5","messages":[{"role":"system","content":"  "},{"role":"user","content":""},{"role":"user","content":"Hi"}]}"""))
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertFalse("system" in body)
        assertEquals(listOf("user" to "Hi"), turns(body))
    }

    @Test
    fun `a picture is Claude's picture`() {
        val body = ClaudeApi.body(
            obj(
                """{"model":"m","messages":[{"role":"user","content":[
                {"type":"text","text":"What is this?"},
                {"type":"image_url","image_url":{"url":"data:image/png;base64,QUJD"}},
                {"type":"image_url","image_url":{"url":"https://pics.example/a.jpg"}}]}]}""",
            ),
        )
        val blocks = body["messages"]!!.jsonArray.single().jsonObject["content"] as JsonArray
        assertEquals(listOf("text", "image", "image"), blocks.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        val bytes = blocks[1].jsonObject["source"]!!.jsonObject
        assertEquals("base64", bytes["type"]!!.jsonPrimitive.content)
        assertEquals("image/png", bytes["media_type"]!!.jsonPrimitive.content)
        assertEquals("QUJD", bytes["data"]!!.jsonPrimitive.content)
        assertEquals("https://pics.example/a.jpg", blocks[2].jsonObject["source"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the thinking level in Claude's words`() {
        fun body(level: String?, thinking: Boolean = true) = ClaudeApi.body(
            obj("""{"model":"m","messages":[{"role":"user","content":"Hi"}]${level?.let { ""","reasoning_effort":"$it"""" } ?: ""}}"""),
            thinking,
        )
        // The model's default: nothing is said about thinking.
        assertFalse(ClaudeApi.hasThinking(body(null)))
        val high = body("xhigh")
        assertEquals("adaptive", high["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("summarized", high["thinking"]!!.jsonObject["display"]!!.jsonPrimitive.content)
        assertEquals("xhigh", high["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("low", body("minimal")["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val off = body("none")
        assertEquals("disabled", off["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse("output_config" in off)
        // The OpenAI standard's field itself never goes.
        assertFalse("reasoning_effort" in high)
        // A model that refused the level is asked without it.
        assertFalse(ClaudeApi.hasThinking(body("high", thinking = false)))
        assertFalse(ClaudeApi.hasThinking(ClaudeApi.withoutThinking(high)))
        assertTrue(ClaudeApi.refusesThinking(400, """{"type":"error","error":{"type":"invalid_request_error","message":"output_config.effort: not supported on this model"}}"""))
        assertFalse(ClaudeApi.refusesThinking(400, """{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens: too large"}}"""))
        assertFalse(ClaudeApi.refusesThinking(401, "thinking"))
    }

    @Test
    fun `a reply read from Claude's stream`() {
        val stream = ClaudeApi.Stream(json)
        val events = listOf(
            """{"type":"message_start","message":{"id":"msg_1","type":"message","model":"claude-haiku-5-5"}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"She would smile."}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"abc"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"ping"}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Hello"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":" there"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":12}}""",
            """{"type":"message_stop"}""",
            "not json",
        ).flatMap(stream::read)
        assertEquals(
            listOf(
                GenerationEvent.Meta(requestId = null, contextUsagePercent = null, contextWindowFull = null, model = "claude-haiku-5-5"),
                GenerationEvent.Reasoning("She would smile."),
                GenerationEvent.Delta("Hello"),
                GenerationEvent.Delta(" there"),
                GenerationEvent.Done,
            ),
            events,
        )
    }

    @Test
    fun `a refusal with nothing written, and an error mid-reply, end the reply`() {
        try {
            ClaudeApi.Stream(json).read("""{"type":"message_delta","delta":{"stop_reason":"refusal"}}""")
            fail("a refusal is not a reply")
        } catch (e: ApiError.Api) {
            assertEquals("CLAUDE_REFUSAL", e.janitorCode)
        }
        // One that came after some of the reply keeps what was written.
        val wrote = ClaudeApi.Stream(json)
        wrote.read("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}""")
        assertTrue(wrote.read("""{"type":"message_delta","delta":{"stop_reason":"refusal"}}""").isEmpty())
        try {
            ClaudeApi.Stream(json).read("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
            fail("an error is not a reply")
        } catch (e: ApiError.Server) {
            assertEquals(529, e.code)
        }
    }

    @Test
    fun `a whole answer, and a stream's text, as the OpenAI standard's`() {
        val whole = ClaudeApi.asOpenAi(
            obj("""{"content":[{"type":"thinking","thinking":"hm"},{"type":"text","text":"One."},{"type":"text","text":" Two."}],"stop_reason":"max_tokens"}"""),
        )
        val choice = whole["choices"]!!.jsonArray.single().jsonObject
        assertEquals("One. Two.", choice["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("length", choice["finish_reason"]!!.jsonPrimitive.content)

        val chunk = ClaudeApi.asOpenAiChunk(obj("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}"""))!!
        assertEquals("Hi", chunk["choices"]!!.jsonArray.single().jsonObject["delta"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertNull(ClaudeApi.asOpenAiChunk(obj("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hm"}}""")))
        assertNull(ClaudeApi.asOpenAiChunk(obj("""{"type":"message_stop"}""")))
    }
}
