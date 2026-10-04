package com.cherry.butler.core.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingTransportTest {

    private class Spy(val name: String, val calls: MutableList<String>) : GenerationTransport {
        override fun generate(envelope: JsonObject, proxy: ProxyTarget?): Flow<GenerationEvent> {
            calls += name
            return emptyFlow()
        }
    }

    private fun envelope(api: String?) = buildJsonObject {
        put("generateMode", "NEW")
        put("userConfig", buildJsonObject { if (api != null) put("api", api) })
    }

    @Test
    fun `janitor goes over the socket, everything else over http`() {
        val calls = mutableListOf<String>()
        val router = RoutingTransport(proxyPath = Spy("http", calls), jllm = Spy("ws", calls))
        router.generate(envelope("janitor"), null)
        router.generate(envelope("openai"), null)
        router.generate(envelope(null), null)
        assertEquals(listOf("ws", "http", "http"), calls)
    }
}
