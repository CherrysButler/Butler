package com.cherry.butler.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatFormatsTest {

    private val chat = TransferChat(
        characterId = "8b6bbfb6-9d1b-43b5-9744-6d980197f282",
        characterName = "Neglectful family",
        personaName = "Rain",
        lines = listOf(
            TransferLine(isBot = true, text = "*Karl looks up.* \"You're late.\"", time = 1_791_000_000_000),
            TransferLine(isBot = false, text = "nods", time = 1_791_000_060_000),
        ),
    )

    @Test
    fun butlerJsonRoundTrips() {
        val back = ChatFormats.parse(ChatFormats.toButler(chat, 1_791_000_100_000))
        assertEquals(chat, back)
    }

    @Test
    fun sillyTavernRoundTripsWithoutTheCharacterId() {
        val back = ChatFormats.parse(ChatFormats.toSillyTavern(chat, 1_791_000_100_000))
        assertEquals(chat.copy(characterId = null), back)
    }

    @Test
    fun sillyTavernSkipsSystemLines() {
        val file = """
            {"user_name":"Rain","character_name":"Karl","create_date":"x","chat_metadata":{}}
            {"name":"Karl","is_user":false,"is_system":false,"send_date":"x","mes":"Hello.","extra":{}}
            {"name":"System","is_user":false,"is_system":true,"send_date":"x","mes":"note","extra":{}}
            {"name":"Rain","is_user":true,"is_system":false,"send_date":"x","mes":"Hi.","extra":{}}
        """.trimIndent()
        val back = ChatFormats.parse(file)
        assertEquals(listOf(TransferLine(true, "Hello."), TransferLine(false, "Hi.")), back.lines)
        assertEquals("Karl", back.characterName)
    }
}
