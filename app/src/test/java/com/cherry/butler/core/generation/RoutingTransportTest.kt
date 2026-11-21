package com.cherry.butler.core.generation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RoutingTransportTest {

    private class Spy : GenerationTransport {
        val received = mutableListOf<JsonObject>()

        override fun generate(envelope: JsonObject, proxy: ProxyTarget?): Flow<GenerationEvent> {
            received += envelope
            return emptyFlow()
        }
    }

    private fun envelope(api: String?) = buildJsonObject {
        put("generateMode", "NEW")
        put("userConfig", buildJsonObject { if (api != null) put("api", api) })
    }

    @Test
    fun `janitor and a never-chosen provider go over the socket, a proxy over http`() {
        val ws = Spy()
        val http = Spy()
        val router = RoutingTransport(proxyPath = http, jllm = ws)
        val janitor = envelope("janitor")
        val openai = envelope("openai")
        val fresh = envelope(null)
        router.generate(janitor, null)
        router.generate(openai, null)
        router.generate(fresh, null)
        assertEquals(2, ws.received.size)
        assertSame(janitor, ws.received.first())
        assertSame(fresh, ws.received.last())
        assertSame(openai, http.received.single())
    }
}
