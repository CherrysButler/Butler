package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.dto.CharacterChatsDto
import com.cherry.butler.core.data.remote.dto.ChatListPageDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The chat lists as Janitor sent them on 2026-11-25 (their text replaced): who each chat is
 * played as, and the name a renamed chat carries.
 */
class ChatListDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the main list says who a chat is played as and what a renamed one is called`() {
        val page = json.decodeFromString(
            ChatListPageDto.serializer(),
            """{"hasMore":true,"items":[
               {"character":{"avatar":"a.webp","id":"c1","is_deleted":false,"is_force_removed":false,"is_public":true,"name":"A long title"},
                "folder_ids":[],"id":3298077819,"is_public":false,"last_message_at":"2026-11-25T00:21:13.527Z","last_message_preview":"a line",
                "message_count":1,"personas":[{"avatar":"p.webp","id":"p1","name":"Ghost"}],"title":"rename"},
               {"character":{"avatar":"a.webp","id":"c1","is_deleted":false,"is_force_removed":false,"is_public":true,"name":"A long title"},
                "folder_ids":[],"id":3298074246,"is_public":false,"last_message_at":"2026-11-25T00:20:14.705Z","last_message_preview":"another",
                "message_count":1,"personas":[]}],"page":1,"pageSize":3}""",
        )
        val (renamed, plain) = page.items
        assertEquals("rename", renamed.title)
        assertEquals("Ghost", renamed.personas?.single()?.name)
        assertEquals("p.webp", renamed.personas?.single()?.avatar)
        // No title until it is renamed, and an empty list for a chat played as the profile.
        assertNull(plain.title)
        assertEquals(emptyList(), plain.personas)
    }

    @Test
    fun `a list that says nothing of personas is told apart from one that says none`() {
        val page = json.decodeFromString(
            ChatListPageDto.serializer(),
            """{"items":[{"character":{"id":"c1","name":"A"},"id":1}]}""",
        )
        assertNull(page.items.single().personas)
    }

    @Test
    fun `a character's chats say who each is played as`() {
        val chats = json.decodeFromString(
            CharacterChatsDto.serializer(),
            """{"chats":[
               {"id":3298077819,"is_public":false,"summary":"","chat_count":1,"character_id":"c1","character_name":"A long title",
                "character_avatar":"a.webp","character_is_public":true,"last_message_preview":"a line",
                "persona_id":"p1","personas":[{"avatar":"p.webp","id":"p1","name":"Ghost"}],"updated_at":"2026-11-25T00:21:13.527Z"},
               {"id":3298074246,"is_public":false,"summary":"","chat_count":1,"character_id":"c1","character_name":"A long title",
                "character_avatar":"a.webp","character_is_public":true,"last_message_preview":"another",
                "persona_id":null,"personas":[],"updated_at":"2026-11-25T00:20:14.705Z"}]}""",
        ).chats
        assertEquals("p1", chats[0].personaId)
        assertEquals("Ghost", chats[0].personas.single().name)
        assertNull(chats[1].personaId)
        assertEquals(emptyList(), chats[1].personas)
        assertNull(chats[0].title)
    }
}
