package com.cherry.butler.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterDraftTest {

    // Counts from OpenAI's own tiktoken (cl100k_base), which matched every count the website sent.
    @Test
    fun `tokens are counted as the website counts them`() {
        assertEquals(12, TokenCounts.count("A quiet lighthouse keeper who talks to the gulls."))
        assertEquals(22, TokenCounts.count("{{user}}: Is it always this windy?\n{{char}}: Only on days that end in y."))
        assertEquals(9, TokenCounts.count("Héllo — naïve café ☕"))
        assertEquals(0, TokenCounts.count(""))
    }

    @Test
    fun `the total takes the longest first message, not all of them`() {
        val draft = CharacterDraft(
            name = "Mara",
            personality = "A quiet lighthouse keeper who talks to the gulls.",
            scenario = "Storm season on a small island; the ferry is late.",
            exampleDialogs = "{{user}}: Is it always this windy?\n{{char}}: Only on days that end in y.",
            firstMessages = listOf("*waves* Hello there, traveller! Mind the stairs.", "", "Hi."),
        )
        val t = TokenCounts.of(draft)
        assertEquals(listOf(12, 2), t.intros)
        assertEquals(12 + 12 + 22 + 12, t.total)
    }

    @Test
    fun `the body is the website's, blank first messages left out`() {
        val draft = CharacterDraft(
            name = "  Mara ",
            chatName = "Mara",
            firstMessages = listOf("Hello.", "  ", "Again."),
            tagIds = listOf(6, 2),
            customTags = listOf("lighthouse"),
            nsfw = true,
            avatar = "abc.webp",
        )
        val created = draft.toBody(TokenCounts.of(draft), creating = true)
        assertEquals("Mara", created["name"]!!.jsonPrimitive.content)
        assertEquals("", created["first_message"]!!.jsonPrimitive.content)
        assertEquals(listOf("Hello.", "Again."), created["first_messages"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf(6, 2), created["tag_ids"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(JsonNull, created["scheduled_publish_at"])
        assertEquals(JsonNull, created["silent_publish"])
        val counts = created["token_counts"]!!.jsonObject
        assertEquals(setOf("example_dialog_tokens", "first_message_tokens", "first_messages_tokens", "personality_tokens", "scenario_tokens", "total_tokens"), counts.keys)
        assertTrue(counts["first_messages_tokens"] is JsonArray)

        // An edit names its first intro in first_message, as the website's editor does.
        val edited = draft.toBody(TokenCounts.of(draft), creating = false)
        assertEquals("Hello.", edited["first_message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a character needs a name and something to say first`() {
        assertFalse(CharacterDraft(name = "Mara").savable)
        assertFalse(CharacterDraft(firstMessages = listOf("Hello.")).savable)
        assertTrue(CharacterDraft(name = "Mara", firstMessages = listOf("", "Hello.")).savable)
    }

    @Test
    fun `the profile's style goes back whole, only the edited keys changed`() {
        val saved = kotlinx.serialization.json.buildJsonObject {
            put("custom_style", kotlinx.serialization.json.JsonPrimitive(".old {}"))
            put("background_image", kotlinx.serialization.json.JsonPrimitive("bg.webp"))
            put("background_opacity", kotlinx.serialization.json.JsonPrimitive(80))
        }
        val now = ProfileStyle.of(saved)
        assertEquals(".old {}", now.css)
        val sent = now.copy(css = ".new {}", textColor = "#FFFFFF").into(saved)
        assertEquals(".new {}", sent["custom_style"]!!.jsonPrimitive.content)
        assertEquals("#FFFFFF", sent["text_color"]!!.jsonPrimitive.content)
        assertEquals("bg.webp", sent["background_image"]!!.jsonPrimitive.content)
        assertEquals(80, sent["background_opacity"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a draft kept on the device comes back as it was written`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val kept = StoredDraft(
            id = "new-1",
            draft = CharacterDraft(name = "Mara", firstMessages = listOf("Hello.", ""), tagIds = listOf(6), customTags = listOf("lighthouse"), nsfw = true, avatar = "a.webp"),
            pictureUrl = "https://ella.janitorai.com/bot-avatars/a.webp",
            updatedAt = 42,
        )
        val back = json.decodeFromString(StoredDraft.serializer(), json.encodeToString(StoredDraft.serializer(), kept))
        assertEquals(kept, back)
        assertTrue(back.isNew)
        assertEquals("edit-abc", StoredDraft.idFor("abc"))
    }
}
