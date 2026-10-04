package com.cherry.butler.core.data.remote

import com.cherry.butler.core.data.remote.dto.CharacterDetailDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CharacterDetailDtoTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Janitor sends empty intro slots as `null` (Dr. Evelyn Reed, 2026-10-03). A strict
     * list of strings failed the whole page and left it on the browse list's markdown.
     */
    @Test
    fun `null intro slots decode`() {
        val dto = json.decodeFromString(
            CharacterDetailDto.serializer(),
            """{"id":"x","name":"Dr. Reed","description":"<p>Hi</p>","first_messages":[null,"*A knock*"]}""",
        )
        assertEquals(listOf(null, "*A knock*"), dto.firstMessages)
    }
}
