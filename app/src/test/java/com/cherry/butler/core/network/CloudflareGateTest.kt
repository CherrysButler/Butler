package com.cherry.butler.core.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudflareGateTest {

    private fun response(code: Int, type: String, vararg headers: Pair<String, String>): Response =
        Response.Builder()
            .request(Request.Builder().url("https://janitorai.com/mb/chats/list").build())
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
            .header("Content-Type", type)
            .header("Server", "cloudflare")
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()

    @Test
    fun `a challenge is the page cloudflare marks as one`() {
        val r = response(403, "text/html; charset=UTF-8", "cf-mitigated" to "challenge")
        assertEquals(CloudflareGate.Kind.Challenge, CloudflareGate.classify(r, "<!DOCTYPE html><title>Just a moment...</title>"))
    }

    @Test
    fun `the waiting room is caught even as a 200 page`() {
        val r = response(200, "text/html", "Set-Cookie" to "__cfwaitingroom_catdogxxx=abc; Path=/; HttpOnly")
        assertEquals(CloudflareGate.Kind.WaitingRoom, CloudflareGate.classify(r, "<html>You are now in line</html>"))
    }

    @Test
    fun `the waiting room's json answer is caught`() {
        val r = response(200, "application/json")
        assertEquals(CloudflareGate.Kind.WaitingRoom, CloudflareGate.classify(r, """{"cfWaitingRoom":{"inWaitingRoom":true}}"""))
    }

    @Test
    fun `a firewall rule's page is a block`() {
        val r = response(403, "text/html; charset=UTF-8")
        assertEquals(CloudflareGate.Kind.Block, CloudflareGate.classify(r, "<html><title>Access Restricted</title></html>"))
    }

    @Test
    fun `janitor's own refusal is left to janitor`() {
        assertNull(CloudflareGate.classify(response(403, "application/json"), """{"code":"FORBIDDEN","message":"no"}"""))
    }

    @Test
    fun `an ordinary answer with cloudflare's usual cookies is left alone`() {
        val r = response(
            200, "application/json",
            "Set-Cookie" to "__cf_bm=x; Path=/; HttpOnly",
            "Set-Cookie" to "__cfwaitingroom_catdogxxx=y; Path=/",
        )
        assertNull(CloudflareGate.classify(r, """{"items":[]}"""))
    }
}
