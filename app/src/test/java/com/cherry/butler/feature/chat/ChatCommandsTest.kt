package com.cherry.butler.feature.chat

import com.cherry.butler.core.data.OpenRouterOptions
import com.cherry.butler.core.generation.ReplyLength
import com.cherry.butler.feature.chat.ChatCommands.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatCommandsTest {
    @Test
    fun `commands and their words are read`() {
        assertEquals(Command.Note("make her hesitate"), ChatCommands.parse("/note make her hesitate"))
        assertEquals(Command.Retry(""), ChatCommands.parse("/retry"))
        assertEquals(Command.Retry("shorter, more dialogue"), ChatCommands.parse("/RETRY shorter, more dialogue"))
        assertEquals(Command.Thinking(OpenRouterOptions.Thinking.Low), ChatCommands.parse("/thinking low"))
        assertEquals(Command.Thinking(OpenRouterOptions.Thinking.Off), ChatCommands.parse("/thinking none"))
        assertEquals(Command.Thinking(OpenRouterOptions.Thinking.XHigh), ChatCommands.parse("/thinking extra high"))
        assertEquals(Command.Thinking(null), ChatCommands.parse("/thinking loud"))
        assertEquals(Command.Length(ReplyLength.Short), ChatCommands.parse("/length short"))
        assertEquals(Command.Learn("pacing"), ChatCommands.parse("/learn pacing"))
        assertEquals(Command.Remember("No time skips."), ChatCommands.parse("/remember No time skips."))
    }

    @Test
    fun `anything else is a message`() {
        assertNull(ChatCommands.parse("/shrug"))
        assertNull(ChatCommands.parse("*She waves.* /note"))
        assertNull(ChatCommands.parse("hello"))
    }

    @Test
    fun `names are offered while typed, then their options`() {
        val all = ChatCommands.available(lessons = true, openRouter = false)
        assertEquals(listOf("/thinking"), ChatCommands.offers("/th", all).map { it.label })
        assertEquals(listOf("low"), ChatCommands.offers("/thinking l", all).map { it.label })
        assertEquals("/thinking low", ChatCommands.offers("/thinking l", all).single().fill)
        assertEquals(6, ChatCommands.offers("/thinking ", all).size)
        assertEquals(emptyList<String>(), ChatCommands.offers("/note make her", all).map { it.label })
        assertEquals(listOf("/length", "/learn"), ChatCommands.offers("/le", all).map { it.label })
        assertEquals(emptyList<String>(), ChatCommands.offers("/lea", ChatCommands.available(lessons = false, openRouter = false)).map { it.label })
    }
}
