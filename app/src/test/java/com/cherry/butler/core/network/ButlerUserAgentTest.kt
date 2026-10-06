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
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

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

    @Before @After
    fun fresh() = ButlerUserAgent.reset()

    @Test
    fun `calls say they are butler`() {
        val chain = FakeChain(send) { 200 to "{}" }
        ButlerUserAgent.intercept(chain)
        assertEquals(listOf(ButlerUserAgent.VALUE), chain.sent)
    }

    @Test
    fun `a firewall block on butler's name is retried as firefox, and firefox sticks`() {
        val chain = FakeChain(send) { r -> if (r.header("User-Agent") == ButlerUserAgent.VALUE) 403 to blockPage else 200 to "{}" }
        assertEquals(200, ButlerUserAgent.intercept(chain).code)
        assertEquals(listOf(ButlerUserAgent.VALUE, ButlerUserAgent.FIREFOX), chain.sent)
        assertEquals(ButlerUserAgent.FIREFOX, ButlerUserAgent.current)

        val next = FakeChain(send) { 200 to "{}" }
        ButlerUserAgent.intercept(next)
        assertEquals(listOf(ButlerUserAgent.FIREFOX), next.sent)
    }

    @Test
    fun `janitor's own refusal is not retried`() {
        val chain = FakeChain(send) { 403 to """{"message":"no"}""" }
        assertEquals(403, ButlerUserAgent.intercept(chain).code)
        assertEquals(listOf(ButlerUserAgent.VALUE), chain.sent)
        assertEquals(ButlerUserAgent.VALUE, ButlerUserAgent.current)
    }

    @Test
    fun `other sites are never retried`() {
        val proxy = Request.Builder().url("https://openrouter.ai/api/v1/chat/completions").build()
        val chain = FakeChain(proxy) { 403 to blockPage }
        ButlerUserAgent.intercept(chain)
        assertEquals(listOf(ButlerUserAgent.VALUE), chain.sent)
    }
}
