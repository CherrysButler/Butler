package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.dto.PronounsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaFormatsTest {

    private val rain = TransferPersona(
        name = "Rain",
        appearance = "Tall, quiet, always carrying an umbrella.\nNever says why.",
        pronouns = PronounsDto("she", "her", "her", "hers", "herself"),
        avatarUrl = "https://ella.janitorai.com/avatars/3f1c2b.webp",
        group = TransferGroup("Fantasy", "#6FCF97"),
    )
    private val profile = TransferPersona(name = "Cherry", appearance = "Just me.", isDefault = true)

    @Test
    fun onePersonaRoundTrips() {
        val back = PersonaFormats.parse(PersonaFormats.toButler(listOf(rain), 1_791_000_100_000))
        assertEquals(listOf(rain), back)
    }

    @Test
    fun severalRoundTripInOrder() {
        val file = PersonaFormats.toButler(listOf(profile, rain), 1_791_000_100_000)
        assertTrue("\"personas\"" in file)
        assertEquals(listOf(profile, rain), PersonaFormats.parse(file))
    }

    @Test
    fun textIsACardPerPersona() {
        val text = PersonaFormats.toText(listOf(profile, rain))
        assertEquals(
            """
            Cherry (default)

            Just me.

            * * *

            Rain
            Pronouns: she/her
            Group: Fantasy
            Picture: https://ella.janitorai.com/avatars/3f1c2b.webp

            Tall, quiet, always carrying an umbrella.
            Never says why.

            """.trimIndent(),
            text,
        )
    }

    @Test
    fun markdownHeadsOnePersonaWithItsName() {
        val md = PersonaFormats.toMarkdown(listOf(rain))
        assertEquals(
            """
            # Rain

            - **Pronouns:** she/her
            - **Group:** Fantasy

            ![Rain](https://ella.janitorai.com/avatars/3f1c2b.webp)

            Tall, quiet, always carrying an umbrella.
            Never says why.

            """.trimIndent(),
            md,
        )
    }

    @Test
    fun markdownPutsSeveralUnderOneHeading() {
        val md = PersonaFormats.toMarkdown(listOf(profile, rain))
        assertTrue(md.startsWith("# Personas\n\n## Cherry\n\n- **Default persona**\n\nJust me.\n\n## Rain\n"))
    }

    @Test
    fun importRefusesTextAndMarkdown() {
        val text = assertThrows(IllegalArgumentException::class.java) { PersonaFormats.parse(PersonaFormats.toText(listOf(rain))) }
        assertTrue(text.message!!.contains("JSON"))
        assertThrows(IllegalArgumentException::class.java) { PersonaFormats.parse(PersonaFormats.toMarkdown(listOf(rain))) }
    }

    @Test
    fun importRefusesOtherJson() {
        assertThrows(IllegalArgumentException::class.java) { PersonaFormats.parse("""{"format":"butler.chat","messages":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { PersonaFormats.parse("""{"format":"butler.persona","personas":[]}""") }
    }

    @Test
    fun aPersonaWithoutANameIsSkipped() {
        val back = PersonaFormats.parse("""{"format":"butler.persona","personas":[{"name":" "},{"name":"Rain","appearance":"x"}]}""")
        assertEquals(listOf(TransferPersona("Rain", "x")), back)
    }

    @Test
    fun onlyJanitorPersonaPicturesKeepTheirFile() {
        assertEquals("3f1c2b.webp", PersonaFormats.janitorPictureFile("https://ella.janitorai.com/avatars/3f1c2b.webp"))
        assertEquals("3f1c2b.webp", PersonaFormats.janitorPictureFile("https://ella.janitorai.com/avatars/3f1c2b.webp?width=128"))
        assertNull(PersonaFormats.janitorPictureFile("https://ella.janitorai.com/bot-avatars/3f1c2b.webp"))
        assertNull(PersonaFormats.janitorPictureFile("https://example.com/avatars/3f1c2b.webp"))
        assertNull(PersonaFormats.janitorPictureFile(null))
    }
}
