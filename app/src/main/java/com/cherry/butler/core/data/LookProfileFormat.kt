package com.cherry.butler.core.data

import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.design.CustomColors
import com.cherry.butler.core.design.CustomToken
import com.cherry.butler.core.design.RpLook
import com.cherry.butler.core.design.ThinkingLook
import com.cherry.butler.core.design.ThoughtLook
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A look saved under a name: the theme and the Custom look's colours, the chat layout, the
 * thinking line, how chat text is drawn, and the two fonts (by key; null leaves the font as
 * it is, as when the font wasn't here to save).
 */
data class LookProfile(
    val id: String,
    val name: String,
    val theme: AppTheme = AppTheme.JanitorClassic,
    val chatStyle: ChatStyle = ChatStyle.Story,
    val thinkingLook: ThinkingLook = ThinkingLook.Butler,
    val thoughtLook: ThoughtLook = ThoughtLook.Stripe,
    val custom: CustomColors = CustomColors.Default,
    val text: RpLook = RpLook(),
    val appFont: String? = null,
    val chatFont: String? = null,
) {
    /** Whether [now] wears this look; a font the profile doesn't set counts as worn. */
    fun sameLookAs(now: LookProfile): Boolean =
        copy(id = now.id, name = now.name, appFont = appFont ?: now.appFont, chatFont = chatFont ?: now.chatFont) == now
}

/**
 * A [LookProfile] as JSON, for the profiles kept on the phone and for a file to share. The
 * file is `{format, version, name, theme, chat_style, thinking_look, thought_look, custom,
 * text, fonts}`, colours as "#AARRGGBB". Reading is forgiving of what it doesn't know: a
 * missing or unknown value is Butler's default, an unknown field is ignored. What isn't a
 * Butler look at all is refused.
 */
object LookProfileFormat {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val compact = Json { ignoreUnknownKeys = true }

    const val FORMAT = "butler.look"
    private const val VERSION = 1
    private const val UNNAMED = "Imported look"

    /** One profile as a file to share. The id stays on the phone. */
    fun toJson(profile: LookProfile): String = json.encodeToString(JsonObject.serializer(), encode(profile, withId = false))

    /**
     * A shared file as a profile, its id empty (the caller gives it one). Throws
     * [IllegalArgumentException] for text that isn't JSON or isn't a Butler look.
     */
    fun parse(text: String): LookProfile {
        val o = runCatching { json.parseToJsonElement(text.trimStart('﻿', ' ', '\n', '\r', '\t')) }.getOrNull() as? JsonObject
            ?: throw IllegalArgumentException("That file isn't a look profile.")
        require(o.string("format") == FORMAT) { "That file isn't a Butler look profile." }
        return decode(o, id = "")
    }

    /** Every saved profile, for the phone's own storage. */
    fun encodeAll(profiles: List<LookProfile>): String =
        compact.encodeToString(JsonArray.serializer(), buildJsonArray { profiles.forEach { add(encode(it, withId = true)) } })

    /** What [encodeAll] wrote; anything unreadable in it is left out rather than losing the rest. */
    fun decodeAll(text: String?): List<LookProfile> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { compact.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
        return array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            runCatching { decode(o, id) }.getOrNull()
        }
    }

    private fun encode(p: LookProfile, withId: Boolean): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("version", VERSION)
        if (withId) put("id", p.id)
        put("name", p.name)
        put("theme", p.theme.key)
        put("chat_style", p.chatStyle.key)
        put("thinking_look", p.thinkingLook.key)
        put("thought_look", p.thoughtLook.key)
        put("custom", buildJsonObject {
            put("accent", hex(p.custom.accent))
            put("ground", hex(p.custom.ground))
            put("corners", p.custom.corners)
            put("overrides", buildJsonObject { p.custom.overrides.toSortedMap().forEach { (k, v) -> put(k, hex(v)) } })
        })
        put("text", buildJsonObject {
            p.text.narration?.let { put("narration", hex(it)) }
            p.text.speech?.let { put("speech", hex(it)) }
            p.text.action?.let { put("action", hex(it)) }
            p.text.thought?.let { put("thought", hex(it)) }
            p.text.strong?.let { put("strong", hex(it)) }
            put("italic_actions", p.text.italicActions)
            put("show_quotes", p.text.showQuotes)
            put("size", p.text.textSize)
        })
        put("fonts", buildJsonObject {
            p.appFont?.let { put("app", it) }
            p.chatFont?.let { put("chat", it) }
        })
    }

    private fun decode(o: JsonObject, id: String): LookProfile {
        val custom = o["custom"] as? JsonObject
        val text = o["text"] as? JsonObject
        val fonts = o["fonts"] as? JsonObject
        val defaults = RpLook()
        return LookProfile(
            id = id,
            name = o.string("name")?.trim()?.take(NAME_MAX)?.takeIf { it.isNotEmpty() } ?: UNNAMED,
            theme = AppTheme.entries.firstOrNull { it.key == o.string("theme") } ?: AppTheme.JanitorClassic,
            chatStyle = ChatStyle.entries.firstOrNull { it.key == o.string("chat_style") } ?: ChatStyle.Story,
            thinkingLook = ThinkingLook.entries.firstOrNull { it.key == o.string("thinking_look") } ?: ThinkingLook.Butler,
            thoughtLook = ThoughtLook.entries.firstOrNull { it.key == o.string("thought_look") } ?: ThoughtLook.Stripe,
            custom = CustomColors(
                accent = color(custom?.get("accent")) ?: CustomColors.Default.accent,
                ground = color(custom?.get("ground")) ?: CustomColors.Default.ground,
                // Only colours Butler knows to set; a newer Butler's extras are dropped.
                overrides = (custom?.get("overrides") as? JsonObject).orEmpty()
                    .filterKeys { CustomToken.of(it) != null }
                    .mapNotNull { (k, v) -> color(v)?.let { k to it } }
                    .toMap(),
                corners = ((custom?.get("corners") as? JsonPrimitive)?.floatOrNull ?: 1f).coerceIn(0f, 2f),
            ),
            text = RpLook(
                narration = color(text?.get("narration")),
                speech = color(text?.get("speech")),
                action = color(text?.get("action")),
                thought = color(text?.get("thought")),
                strong = color(text?.get("strong")),
                italicActions = (text?.get("italic_actions") as? JsonPrimitive)?.booleanOrNull ?: defaults.italicActions,
                showQuotes = (text?.get("show_quotes") as? JsonPrimitive)?.booleanOrNull ?: defaults.showQuotes,
                textSize = ((text?.get("size") as? JsonPrimitive)?.intOrNull ?: defaults.textSize).coerceIn(com.cherry.butler.core.design.MIN_TEXT_SIZE, com.cherry.butler.core.design.MAX_TEXT_SIZE),
            ),
            appFont = fonts?.string("app")?.takeIf { it.isNotBlank() },
            chatFont = fonts?.string("chat")?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun hex(argb: Long): String = "#%08X".format(java.util.Locale.ROOT, argb and 0xFFFFFFFFL)

    /** "#AARRGGBB", "#RRGGBB" (opaque), or the ARGB number; null for anything else. */
    private fun color(e: JsonElement?): Long? {
        val p = e as? JsonPrimitive ?: return null
        if (!p.isString) return p.longOrNull?.takeIf { it in 0L..0xFFFFFFFFL }
        val digits = p.content.trim().removePrefix("#")
        val value = digits.takeIf { it.length == 6 || it.length == 8 }?.toLongOrNull(16) ?: return null
        return if (digits.length == 6) value or 0xFF000000L else value
    }

    const val NAME_MAX = 40
}
