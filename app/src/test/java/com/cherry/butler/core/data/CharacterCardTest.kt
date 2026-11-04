package com.cherry.butler.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater

class CharacterCardTest {

    private val v1 = """{"name":"Ash","description":"{{char}} is the night clerk.","personality":"dry, kind","scenario":"{{user}} arrives late.",
        "first_mes":"\"You're late.\"","mes_example":"<START>\n{{user}}: Hi"}"""

    private val v2 = """{"spec":"chara_card_v2","spec_version":"2.0","data":{"name":"Ash","description":"{{char}} is the night clerk.",
        "personality":"","scenario":"Midnight.","first_mes":"Hello.","mes_example":"","creator_notes":"Slow burn.\n\nWorks best with a proxy.",
        "system_prompt":"Be terse.","post_history_instructions":"","alternate_greetings":["Hi again.",""],
        "character_book":{"entries":[{"keys":["ledger"],"content":"Old guests."},{"keys":["bell"],"content":"It rings."}]},
        "tags":["Male","mystery","NSFW"],"creator":"cherry","character_version":"1.0","extensions":{}}}"""

    /** The smallest PNG: one black pixel. */
    private val pixel = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg==")

    @Test
    fun `a v1 card's fields come through`() {
        val c = CharacterCard.read(v1.toByteArray())
        assertEquals("Ash", c.name)
        assertEquals("{{user}} arrives late.", c.scenario)
        assertEquals("\"You're late.\"", c.firstMessage)
        val d = c.toDraft(emptyMap())
        // Janitor has one box for who the character is: the definition, then its personality.
        assertEquals("{{char}} is the night clerk.\n\ndry, kind", d.personality)
        assertEquals(listOf("\"You're late.\""), d.firstMessages)
    }

    @Test
    fun `a v2 card becomes a draft with its intros, tags and notes`() {
        val c = CharacterCard.read(v2.toByteArray())
        val d = c.toDraft(mapOf("male" to 7))
        assertEquals(listOf("Hello.", "Hi again."), d.firstMessages)
        assertEquals(listOf(7), d.tagIds)
        assertEquals(listOf("mystery"), d.customTags)
        assertTrue(d.nsfw)
        assertEquals("<p>Slow burn.</p><p>Works best with a proxy.</p>", d.description)
        assertEquals(listOf("its lorebook (2 entries)", "its system prompt"), c.leftOut)
    }

    @Test
    fun `tavernai's old names are read`() {
        val c = CharacterCard.read("""{"char_name":"Mona","char_persona":"A witch.","world_scenario":"A forest.","char_greeting":"Hm?","example_dialogue":"x"}""".toByteArray())
        assertEquals("Mona", c.name)
        assertEquals("A witch.", c.description)
        assertEquals("A forest.", c.scenario)
        assertEquals("Hm?", c.firstMessage)
    }

    @Test
    fun `a card written into a png reads back, and replaces the card it held`() {
        val card = CharacterCard.read(v2.toByteArray())
        val once = CharacterCard.writePng(pixel, card)
        val twice = CharacterCard.writePng(once, card.copy(name = "Ash II"))
        assertEquals("Ash", CharacterCard.read(once).name)
        assertEquals("Ash II", CharacterCard.read(twice).name)
        assertEquals(1, Regex("chara").findAll(String(twice, Charsets.ISO_8859_1)).count())
        assertEquals(listOf("Hello.", "Hi again."), CharacterCard.read(twice).let { listOf(it.firstMessage) + it.alternateGreetings })
    }

    @Test
    fun `a v3 chunk wins over v2, and compressed chunks are read`() {
        val v3 = """{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"Ash v3","nickname":"Ashy","description":"d"}}"""
        val withV2 = CharacterCard.writePng(pixel, CharacterCard.read(v2.toByteArray()))
        val png = insert(withV2, itxt("ccv3", Base64.getEncoder().encodeToString(v3.toByteArray()), compressed = true))
        val c = CharacterCard.read(png)
        assertEquals("Ash v3", c.name)
        assertEquals("Ashy", c.toDraft(emptyMap()).chatName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a picture without a card says so`() {
        CharacterCard.read(pixel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `json that isn't a card says so`() {
        CharacterCard.read("""{"hello":"world"}""".toByteArray())
    }

    @Test
    fun `html notes go back to plain text`() {
        assertEquals("Slow burn.\n\nA <b>bold</b> & proud\nline.", CharacterCard.plainText("<p>Slow burn.</p><p>A &lt;b&gt;bold&lt;/b&gt; &amp; proud<br>line.</p>"))
    }

    private fun itxt(key: String, text: String, compressed: Boolean): ByteArray {
        val body = if (compressed) Deflater().run { setInput(text.toByteArray()); finish(); val b = ByteArray(65536); val n = deflate(b); b.copyOf(n) } else text.toByteArray()
        val data = key.toByteArray() + byteArrayOf(0, if (compressed) 1 else 0, 0, 0, 0) + body
        val t = "iTXt".toByteArray()
        val out = ByteArrayOutputStream()
        val n = data.size
        out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        out.write(t); out.write(data)
        val crc = CRC32().apply { update(t); update(data) }.value
        out.write(byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte()))
        return out.toByteArray()
    }

    /** [chunk] right after the header chunk. */
    private fun insert(png: ByteArray, chunk: ByteArray): ByteArray {
        val ihdrEnd = 8 + 12 + 13
        return png.copyOfRange(0, ihdrEnd) + chunk + png.copyOfRange(ihdrEnd, png.size)
    }
}
