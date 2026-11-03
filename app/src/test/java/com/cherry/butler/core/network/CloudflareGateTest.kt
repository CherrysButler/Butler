package com.cherry.butler.core.network

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudflareGateTest {

    private fun response(code: Int, type: String, vararg headers: Pair<String, String>, cookie: String? = null): Response =
        Response.Builder()
            .request(Request.Builder().url("https://janitorai.com/hampter/chats/list").apply { cookie?.let { header("Cookie", it) } }.build())
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

    @Test
    fun `a queued call's page is the waiting room even without its cookie set again`() {
        // 2026-11-03: the second answer to a queued send came with no Set-Cookie, and was read as a broken reply.
        val r = response(200, "text/html; charset=UTF-8", cookie = "__cfwaitingroom_catdogxxx=abc; __cf_bm=x")
        assertEquals(CloudflareGate.Kind.WaitingRoom, CloudflareGate.classify(r, "<!doctype html><html lang=\"en\"><title>Janitor</title></html>"))
    }

    @Test
    fun `the waiting room's own words give it away`() {
        val r = response(200, "text/html")
        assertEquals(CloudflareGate.Kind.WaitingRoom, CloudflareGate.classify(r, "<html><div id=\"cf-waiting-room\">You are in line</div></html>"))
    }

    @Test
    fun `any other page where janitor's data belongs is never taken for it`() {
        val r = response(200, "text/html")
        assertEquals(CloudflareGate.Kind.Page, CloudflareGate.classify(r, "<!doctype html><html><body>Hi</body></html>"))
    }

    @Test
    fun `janitor's own queue page is the queue, with its estimate`() {
        // 2026-11-03: "Please wait \u2014 janitor", Refresh: 20, no queue cookie, no "waiting room" words.
        val r = response(200, "text/html; charset=utf-8", "Refresh" to "20")
        val page = "<title>Please wait \u2014 janitor</title><h1>You\u2019re in line</h1><p class=\"wait\">\n  Estimated wait: 1 minute\n\n</p>"
        assertEquals(CloudflareGate.Kind.WaitingRoom, CloudflareGate.classify(r, page))
        assertEquals("1 minute", CloudflareGate.estimate(page))
        assertEquals(20_000L, CloudflareGate.refreshMillis(r, page))
    }

    @Test
    fun `the queue is waited at its own pace`() {
        val page = response(200, "text/html")
        assertEquals(30_000L, CloudflareGate.refreshMillis(page, "<meta http-equiv=\"refresh\" content=\"30\">"))
        assertEquals(20_000L, CloudflareGate.refreshMillis(page, "<html>no refresh here</html>"))
        assertEquals(15_000L, CloudflareGate.refreshMillis(response(200, "application/json"), """{"cfWaitingRoom":{"refreshIntervalSeconds":15}}"""))
        assertEquals(60_000L, CloudflareGate.refreshMillis(response(200, "text/html", "Refresh" to "600"), ""))
    }
}