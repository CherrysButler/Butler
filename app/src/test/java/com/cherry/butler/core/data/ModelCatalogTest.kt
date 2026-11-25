package com.cherry.butler.core.data

import com.cherry.butler.feature.settings.dollars
import com.cherry.butler.feature.settings.priceOf
import com.cherry.butler.feature.settings.sizeOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    private fun model(text: String, roleplay: Set<String> = emptySet()) =
        ModelCatalog.parse(Json.parseToJsonElement(text).jsonObject, roleplay)

    @Test
    fun `a model as OpenRouter lists it`() {
        val m = model(
            """{"id":"anthropic/claude-sonnet-5.5","name":"Anthropic: Claude Sonnet 5.5","context_length":1000000,
            "pricing":{"prompt":"0.000003","completion":"0.000015"},
            "architecture":{"input_modalities":["text","image"]},"supported_parameters":["tools","reasoning"],"created":1790000000}""",
            roleplay = setOf("anthropic/claude-sonnet-5.5"),
        )!!
        assertEquals("anthropic/claude-sonnet-5.5", m.id)
        assertEquals("Claude Sonnet 5.5", m.name)
        assertEquals("Anthropic", m.maker)
        assertEquals(1_000_000, m.context)
        assertEquals(3.0, m.priceIn!!, 1e-9)
        assertEquals(15.0, m.priceOut!!, 1e-9)
        assertTrue(m.images)
        assertTrue(m.reasoning)
        assertTrue(m.roleplay)
        assertFalse(m.free)
    }

    @Test
    fun `a free model, and one whose price varies`() {
        val free = model("""{"id":"meta/llama:free","name":"Meta: Llama","context_length":8192,"pricing":{"prompt":"0","completion":"0"}}""")!!
        assertTrue(free.free)
        assertEquals("Free", priceOf(free))
        // The auto router prices by the model it lands on: -1 on the wire.
        val varies = model("""{"id":"openrouter/auto","name":"Auto Router","context_length":2000000,"pricing":{"prompt":"-1","completion":"-1"}}""")!!
        assertNull(varies.priceIn)
        assertFalse(varies.free)
        assertEquals("", priceOf(varies))
        // No "Maker: " in the name: the id says who.
        assertEquals("Openrouter", varies.maker)
        assertEquals("Auto Router", varies.name)
    }

    @Test
    fun `a model without an id is left out`() {
        assertNull(model("""{"name":"Nobody: Nothing"}"""))
    }

    private fun m(id: String, name: String, maker: String) = CatalogModel(id = id, name = name, maker = maker, context = 1)

    private val list = listOf(
        m("anthropic/claude-opus-5.5", "Claude Opus 5.5", "Anthropic"),
        m("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", "Anthropic"),
        m("someone/sonnetish-claude-mix", "Mythomax Sonnetish", "Someone"),
        m("google/gemini-3-pro", "Gemini 3 Pro", "Google"),
    )

    @Test
    fun `nothing typed keeps the router's order`() {
        assertEquals(list, ModelCatalog.search(list, "  "))
    }

    @Test
    fun `every word has to be found`() {
        assertEquals(listOf("anthropic/claude-sonnet-5.5"), ModelCatalog.search(list, "claude sonnet 5.5").map { it.id })
        assertTrue(ModelCatalog.search(list, "claude gemini").isEmpty())
    }

    @Test
    fun `a word that starts the name comes before one inside it or only in the id`() {
        val found = ModelCatalog.search(list, "sonnet").map { it.id }
        // "Claude Sonnet": a word of the name. "Mythomax Sonnetish": a word of the name too, but
        // later in the router's order. Both before anything matching by id alone.
        assertEquals(listOf("anthropic/claude-sonnet-5.5", "someone/sonnetish-claude-mix"), found)
        // "claude" starts two names, and is only in the third one's id: that one comes last.
        assertEquals("someone/sonnetish-claude-mix", ModelCatalog.search(list, "claude").map { it.id }.last())
    }

    @Test
    fun `a maker or an id finds its models`() {
        assertEquals(2, ModelCatalog.search(list, "anthropic").size)
        assertEquals(listOf("google/gemini-3-pro"), ModelCatalog.search(list, "google/gemini").map { it.id })
    }

    @Test
    fun `prices as short as says them`() {
        assertEquals("$0.075", dollars(0.075))
        assertEquals("$0.15", dollars(0.15))
        assertEquals("$0.30", dollars(0.3))
        assertEquals("$3", dollars(3.0))
        assertEquals("$12.50", dollars(12.5))
        assertEquals("$0", dollars(0.0))
        assertEquals("$3 / $15", priceOf(CatalogModel("a/b", "B", "A", 1, priceIn = 3.0, priceOut = 15.0)))
    }

    @Test
    fun `a routing shortcut is a word on the model's end`() {
        assertEquals("moonshotai/kimi-k2.6:nitro", ModelVariant.with("moonshotai/kimi-k2.6", ModelVariant.Nitro))
        assertEquals("moonshotai/kimi-k2.6:floor", ModelVariant.with("moonshotai/kimi-k2.6:nitro", ModelVariant.Floor))
        assertEquals("moonshotai/kimi-k2.6", ModelVariant.with("moonshotai/kimi-k2.6:floor", ModelVariant.Standard))
        assertEquals(ModelVariant.Nitro, ModelVariant.of("moonshotai/kimi-k2.6:nitro"))
        assertEquals(ModelVariant.Standard, ModelVariant.of("moonshotai/kimi-k2.6"))
        assertEquals("moonshotai/kimi-k2.6", ModelVariant.base(" moonshotai/kimi-k2.6:NITRO "))
        // A model whose id ends in a word of its own is a fixed offer: no shortcut goes on it.
        assertEquals(ModelVariant.Standard, ModelVariant.of("meta/llama:free"))
        assertFalse(ModelVariant.takes("meta/llama:free"))
        assertTrue(ModelVariant.takes("meta/llama"))
        assertFalse(ModelVariant.takes(""))
    }

    @Test
    fun `sizes in k and M`() {
        assertEquals("8k", sizeOf(8192))
        assertEquals("128k", sizeOf(128_000))
        assertEquals("200k", sizeOf(200_000))
        assertEquals("1M", sizeOf(1_000_000))
        assertEquals("1.05M", sizeOf(1_048_576))
        assertEquals("512", sizeOf(512))
    }
}
