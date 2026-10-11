package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptAddonsTest {
    private fun line(text: String, bot: Boolean) = MessageEntity(
        serverId = 1, chatId = 1, isBot = bot, isMain = true, text = text, createdAt = 0,
        rating = null, personaId = null, generationRequestIds = emptyList(), thinking = null,
        streamState = null, cachedAt = 0,
    )

    private fun payload(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun contents(p: JsonObject) = p["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content }

    @Test
    fun `default length and no butter send nothing`() {
        assertNull(PromptAddons.instruction(GenerateMode.New, ReplyLength.Default, butter = false))
    }

    @Test
    fun `a length rule rides on new replies and swipes only`() {
        val rule = ReplyLength.Short.rule!!
        assertEquals(rule, PromptAddons.instruction(GenerateMode.New, ReplyLength.Short, butter = false))
        assertEquals(rule, PromptAddons.instruction(GenerateMode.Alternative, ReplyLength.Short, butter = false))
        assertNull(PromptAddons.instruction(GenerateMode.Continue, ReplyLength.Short, butter = false))
        assertNull(PromptAddons.instruction(GenerateMode.Suggestion, ReplyLength.Long, butter = false))
    }

    @Test
    fun `length comes before butter in one instruction`() {
        val both = PromptAddons.instruction(GenerateMode.New, ReplyLength.Medium, butter = true)!!
        assertEquals(ReplyLength.Medium.rule + "\n\n" + PromptAddons.BUTTER, both)
    }

    @Test
    fun `every length but default names paragraphs and words`() {
        for (length in ReplyLength.entries - ReplyLength.Default) {
            val rule = length.rule!!
            assertTrue("paragraph" in rule && "words" in rule, length.name)
        }
    }

    @Test
    fun `on a proxy the instruction joins the first system message`() {
        val p = payload("""{"model":"m","messages":[{"role":"system","content":"You are Mia."},{"role":"assistant","content":"Hi."},{"role":"user","content":"Hello"},{"role":"system","content":"Later note"}]}""")
        val out = PromptAddons.intoPayload(p, ReplyLength.Short.rule!!)
        val lines = contents(out)
        assertEquals("system" to "You are Mia.\n\n${ReplyLength.Short.rule}", lines[0])
        assertEquals(lines.drop(1), contents(p).drop(1))
        assertEquals("m", out["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun `on a proxy with no system message one is added first`() {
        val p = payload("""{"messages":[{"role":"user","content":"Hello"}]}""")
        val lines = contents(PromptAddons.intoPayload(p, "Rule"))
        assertEquals(listOf("system" to "Rule", "user" to "Hello"), lines)
    }

    @Test
    fun `on JLLM the instruction rides on the last user line, out of character`() {
        val history = listOf(line("Hi.", bot = true), line("Hello", bot = false), line("How are you?", bot = false))
        val out = PromptAddons.intoHistory(history, ReplyLength.Long.rule!!)
        assertEquals(listOf("Hi.", "Hello"), out.take(2).map { it.text })
        assertEquals("How are you?\n\n(OOC: ${ReplyLength.Long.rule})", out[2].text)
        // Never stored: the history handed in is untouched.
        assertEquals("How are you?", history[2].text)
    }

    @Test
    fun `on JLLM a history with no user line is left alone`() {
        val history = listOf(line("Hi.", bot = true))
        assertEquals(history, PromptAddons.intoHistory(history, "Rule"))
    }

    @Test
    fun `an unknown stored length reads as default`() {
        assertEquals(ReplyLength.Default, ReplyLength.of(null))
        assertEquals(ReplyLength.Default, ReplyLength.of("Huge"))
        assertEquals(ReplyLength.Medium, ReplyLength.of("Medium"))
    }
}
