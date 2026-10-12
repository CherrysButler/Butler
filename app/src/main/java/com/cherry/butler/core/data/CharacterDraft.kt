package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.dto.CharacterDetailDto
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A character as its creator writes it: what the editor holds and what the website's
 * `POST /characters` and `PATCH /characters/{id}` take (captured 2026-10-12, §32). Kept on the
 * device as written (see CharacterDrafts), so it's serializable as it stands.
 */
@kotlinx.serialization.Serializable
data class CharacterDraft(
    val name: String = "",
    /** What the character is called in the chat itself, when it isn't [name]. */
    val chatName: String = "",
    /** The public description on the character's page (the website's creator notes). */
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val exampleDialogs: String = "",
    /** At least one; blank slots are dropped on save. */
    val firstMessages: List<String> = listOf(""),
    val tagIds: List<Int> = emptyList(),
    /** Creators' own tags, without the `#`. */
    val customTags: List<String> = emptyList(),
    val nsfw: Boolean = false,
    /** The saved picture's file name; empty for none yet. */
    val avatar: String = "",
) {
    /** The intros that will be saved: the blank ones left out. */
    val intros: List<String> get() = firstMessages.filter { it.isNotBlank() }

    /** Whether Janitor would take it: a name, and something for the character to say first. */
    val savable: Boolean get() = name.isNotBlank() && intros.isNotEmpty()

    /**
     * The body, exactly in the website's shape. On a new character `first_message` is sent
     * empty and Janitor takes the first of `first_messages`; an edit sends that first one in it.
     */
    fun toBody(tokens: TokenCounts, creating: Boolean): JsonObject = buildJsonObject {
        put("avatar", JsonPrimitive(avatar))
        put("chat_name", JsonPrimitive(chatName.trim()))
        put("custom_tags", JsonArray(customTags.map(::JsonPrimitive)))
        put("description", JsonPrimitive(description))
        put("example_dialogs", JsonPrimitive(exampleDialogs))
        put("first_message", JsonPrimitive(if (creating) "" else intros.firstOrNull().orEmpty()))
        put("first_messages", JsonArray(intros.map(::JsonPrimitive)))
        put("is_nsfw", JsonPrimitive(nsfw))
        put("name", JsonPrimitive(name.trim()))
        put("personality", JsonPrimitive(personality))
        put("scenario", JsonPrimitive(scenario))
        put("scheduled_publish_at", JsonNull)
        put("silent_publish", JsonNull)
        put("tag_ids", JsonArray(tagIds.map(::JsonPrimitive)))
        put("token_counts", tokens.toJson())
    }

    companion object {
        /** A saved character, ready to edit. */
        fun of(c: CharacterDetailDto): CharacterDraft = CharacterDraft(
            name = c.name,
            chatName = c.chatName.orEmpty(),
            description = c.description,
            personality = c.personality.orEmpty(),
            scenario = c.scenario.orEmpty(),
            exampleDialogs = c.exampleDialogs.orEmpty(),
            firstMessages = c.firstMessages.filterNotNull().filter { it.isNotBlank() }
                .ifEmpty { listOfNotNull(c.firstMessage?.takeIf { it.isNotBlank() }) }
                .ifEmpty { listOf("") },
            tagIds = c.tags.map { it.id },
            customTags = c.customTags.orEmpty(),
            nsfw = c.isNsfw,
            avatar = c.avatar.orEmpty(),
        )
    }
}

/**
 * A definition's size, counted as the website counts it: OpenAI's cl100k tokens per field, and
 * a total of personality, scenario, example dialogs and the longest intro (the one a chat might
 * open with that costs the most). Matched against every count the website sent (2026-10-12).
 */
data class TokenCounts(
    val personality: Int,
    val scenario: Int,
    val exampleDialogs: Int,
    val intros: List<Int>,
) {
    val longestIntro: Int get() = intros.maxOrNull() ?: 0
    val total: Int get() = personality + scenario + exampleDialogs + longestIntro

    fun toJson(): JsonObject = buildJsonObject {
        put("example_dialog_tokens", JsonPrimitive(exampleDialogs))
        put("first_message_tokens", JsonPrimitive(longestIntro))
        put("first_messages_tokens", JsonArray(intros.map(::JsonPrimitive)))
        put("personality_tokens", JsonPrimitive(personality))
        put("scenario_tokens", JsonPrimitive(scenario))
        put("total_tokens", JsonPrimitive(total))
    }

    companion object {
        /** Built once, on first use: the table takes a moment to read. */
        // The lazy registry reads only the table asked for; the app ships cl100k's alone.
        private val cl100k: Encoding by lazy { Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE) }

        fun count(text: String): Int = if (text.isEmpty()) 0 else cl100k.countTokensOrdinary(text)

        fun of(draft: CharacterDraft): TokenCounts = TokenCounts(
            personality = count(draft.personality),
            scenario = count(draft.scenario),
            exampleDialogs = count(draft.exampleDialogs),
            intros = draft.intros.map(::count),
        )
    }
}
