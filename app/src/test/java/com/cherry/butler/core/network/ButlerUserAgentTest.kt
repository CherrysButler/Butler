package com.cherry.butler.core.network

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ButlerUserAgentTest {

    /** Answers each call with [answer] and keeps the User-Agent each one went out with. */
    private class FakeChain(
        private val request: Request,
        private val answer: (Request) -> Pair<Int, String>,
    ) : Interceptor.Chain {
        val sent = mutableListOf<String?>()
        override fun request() = request
        override fun proceed(request: Request): Response {
            sent += request.header("User-Agent")
            val (code, body) = answer(request)
            val type = if (body.startsWith("{")) "application/json" else "text/html"
            return Response.Builder()
                .request(request).protocol(Protocol.HTTP_2).code(code).message("")
                .header("Content-Type", type).header("Server", "cloudflare")
                .body(body.toResponseBody(type.toMediaType()))
                .build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun readTimeoutMillis() = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun writeTimeoutMillis() = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    private val send = Request.Builder().url("https://janitorai.com/generateAlpha").build()
    private val blockPage = "<html><title>Access Restricted</title></html>"
    private val firefox = BrowserUserAgent.pick(not = "Chrome/1 Safari/1", random = Random(1))
        .let { if (BrowserUserAgent.familyOf(it) == BrowserUserAgent.Family.Firefox) it else "Mozilla/5.0 (X11; Linux x86_64; rv:157.0) Gecko/20100101 Firefox/157.0" }

    @Before @After
    fun fresh() = ButlerUserAgent.reset(firefox)

    @Test
    fun `calls go out as the device's browser`() {
        val chain = FakeChain(send) { 200 to "{}" }
        ButlerUserAgent.intercept(chain)
        assertEquals(listOf(firefox), chain.sent)
        assertNotNull(BrowserUserAgent.familyOf(ButlerUserAgent.current))
    }

    @Test
    fun `a firewall block is retried as another browser, which then sticks`() {
        val chain = FakeChain(send) { r -> if (r.header("User-Agent") == firefox) 403 to blockPage else 200 to "{}" }
        assertEquals(200, ButlerUserAgent.intercept(chain).code)
        assertEquals(2, chain.sent.size)
        assertEquals(firefox, chain.sent[0])
        val other = chain.sent[1]!!
        assertNotEquals(BrowserUserAgent.Family.Firefox, BrowserUserAgent.familyOf(other))
        assertEquals(other, ButlerUserAgent.current)

        val next = FakeChain(send) { 200 to "{}" }
        ButlerUserAgent.intercept(next)
        assertEquals(listOf(other), next.sent)
    }

    @Test
    fun `a block on both browsers keeps the device's`() {
        val chain = FakeChain(send) { 403 to blockPage }
        assertEquals(403, ButlerUserAgent.intercept(chain).code)
        assertEquals(2, chain.sent.size)
        assertEquals(firefox, ButlerUserAgent.current)
    }

    @Test
    fun `janitor's own refusal is not retried`() {
        val chain = FakeChain(send) { 403 to """{"message":"no"}""" }
        assertEquals(403, ButlerUserAgent.intercept(chain).code)
        assertEquals(listOf(firefox), chain.sent)
        assertEquals(firefox, ButlerUserAgent.current)
    }

    @Test
    fun `other sites are never retried`() {
        val proxy = Request.Builder().url("https://openrouter.ai/api/v1/chat/completions").build()
        val chain = FakeChain(proxy) { 403 to blockPage }
        ButlerUserAgent.intercept(chain)
        assertEquals(listOf(firefox), chain.sent)
    }

    @Test
    fun `the pool is real browsers and a retry changes family`() {
        repeat(50) { seed ->
            val ua = BrowserUserAgent.pick(random = Random(seed))
            assertTrue(ua, ua.startsWith("Mozilla/5.0 ("))
            val family = BrowserUserAgent.familyOf(ua)
            assertNotNull(ua, family)
            assertNotEquals(family, BrowserUserAgent.familyOf(BrowserUserAgent.pick(not = ua, random = Random(seed + 1))))
        }
        assertEquals(BrowserUserAgent.Family.Edge, BrowserUserAgent.familyOf("Mozilla/5.0 (Windows NT 10.0) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0"))
        assertEquals(BrowserUserAgent.Family.Safari, BrowserUserAgent.familyOf("Mozilla/5.0 (Macintosh) Version/26.1 Safari/605.1.15"))
        assertEquals(null, BrowserUserAgent.familyOf("Butler/0.3.0"))
    }
}
