package com.cherry.butler.core.data

import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.design.CustomColors
import com.cherry.butler.core.design.RpLook
import com.cherry.butler.core.design.ThinkingLook
import com.cherry.butler.core.design.ThoughtLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class LookProfileFormatTest {

    private val profile = LookProfile(
        id = "p1",
        name = "Night shift",
        theme = AppTheme.Custom,
        chatStyle = ChatStyle.Bubbles,
        thinkingLook = ThinkingLook.Field,
        thoughtLook = ThoughtLook.Button,
        custom = CustomColors(accent = 0xFF3D8BFDL, ground = 0xFF101418L, overrides = mapOf("cards" to 0xFF1C2229L, "text" to 0xFFE8E8E8L), corners = 0.5f),
        text = RpLook(speech = 0xFFFFC857L, thought = 0x80AABBCCL, italicActions = false, showQuotes = false, textSize = 19),
        appFont = "lexend",
        chatFont = "family:Inter",
    )

    @Test
    fun aSharedFileRoundTripsWithoutTheId() {
        assertEquals(profile.copy(id = ""), LookProfileFormat.parse(LookProfileFormat.toJson(profile)))
    }

    @Test
    fun storageRoundTripsEveryProfile() {
        val two = listOf(profile, LookProfile(id = "p2", name = "Plain"))
        assertEquals(two, LookProfileFormat.decodeAll(LookProfileFormat.encodeAll(two)))
    }

    @Test
    fun missingFieldsAreButlersDefaults() {
        val back = LookProfileFormat.parse("""{"format":"butler.look"}""")
        assertEquals(LookProfile(id = "", name = "Imported look"), back)
    }

    @Test
    fun unknownFieldsAndValuesAreTolerated() {
        val text = """
            {
              "format": "butler.look", "version": 7, "name": "  From the future  ", "sparkles": true,
              "theme": "neon", "chat_style": "janitor", "thinking_look": "dither",
              "custom": {"accent": "#FF00AA", "ground": 4278190080, "corners": 9, "overrides": {"cards": "#FF222222", "glow": "#FFFFFFFF", "text": "not a colour"}},
              "text": {"speech": "#80FF0000", "size": 99, "italic_actions": "maybe"},
              "fonts": {"app": "", "chat": "lora"}
            }
        """.trimIndent()
        val back = LookProfileFormat.parse(text)
        assertEquals("From the future", back.name)
        assertEquals(AppTheme.JanitorClassic, back.theme)
        assertEquals(ChatStyle.Janitor, back.chatStyle)
        assertEquals(ThinkingLook.Dither, back.thinkingLook)
        assertEquals(CustomColors(accent = 0xFFFF00AAL, ground = 0xFF000000L, overrides = mapOf("cards" to 0xFF222222L), corners = 2f), back.custom)
        assertEquals(RpLook(speech = 0x80FF0000L, textSize = com.cherry.butler.core.design.MAX_TEXT_SIZE), back.text)
        assertEquals(null, back.appFont)
        assertEquals("lora", back.chatFont)
    }

    @Test
    fun whatIsNotALookIsRefused() {
        assertFailsWith<IllegalArgumentException> { LookProfileFormat.parse("") }
        assertFailsWith<IllegalArgumentException> { LookProfileFormat.parse("{not json") }
        assertFailsWith<IllegalArgumentException> { LookProfileFormat.parse("[1, 2]") }
        assertFailsWith<IllegalArgumentException> { LookProfileFormat.parse("""{"format":"butler.chat","messages":[]}""") }
        assertFailsWith<IllegalArgumentException> { LookProfileFormat.parse("""{"name":"No format"}""") }
    }

    @Test
    fun damagedStorageKeepsWhatItCan() {
        assertEquals(emptyList<LookProfile>(), LookProfileFormat.decodeAll(null))
        assertEquals(emptyList<LookProfile>(), LookProfileFormat.decodeAll("not json"))
        val kept = LookProfileFormat.decodeAll("""[{"id":"a","name":"Kept"}, 3, {"name":"No id"}]""")
        assertEquals(listOf(LookProfile(id = "a", name = "Kept")), kept)
    }

    @Test
    fun sameLookIgnoresTheNameAndFontsTheProfileLeavesAlone() {
        val now = profile.copy(id = "", name = "", appFont = "system", chatFont = "atkinson")
        assertFalse(profile.sameLookAs(now))
        assertTrue(profile.copy(appFont = null, chatFont = null).sameLookAs(now))
        assertFalse(profile.copy(appFont = null, chatFont = null).sameLookAs(now.copy(theme = AppTheme.Daylight)))
    }
}
