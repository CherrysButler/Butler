package com.cherry.butler.core.network

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PlainProxiesTest {

    private val secure = OkHttpClient.Builder().connectionSpecs(PlainProxies.SECURE).build()

    @Before
    fun nothingAllowed() = PlainProxies.reset()

    @Test
    fun `an address without HTTPS has to be allowed first, HTTPS and this machine never`() {
        assertTrue(PlainProxies.needsAnswer("http://192.168.1.50:1234/v1/chat/completions"))
        assertTrue(PlainProxies.needsAnswer(" http://my-pc:5000/v1 "))
        assertTrue(PlainProxies.needsAnswer("http://203.0.113.9/v1"))
        assertFalse(PlainProxies.needsAnswer("https://openrouter.ai/api/v1/chat/completions"))
        assertFalse(PlainProxies.needsAnswer("https://192.168.1.50:1234/v1"))
        assertFalse(PlainProxies.needsAnswer("http://localhost:1234/v1"))
        assertFalse(PlainProxies.needsAnswer("http://127.0.0.1:5001/v1"))
        assertFalse(PlainProxies.needsAnswer("not an address"))
        assertEquals("192.168.1.50:1234", PlainProxies.shown("http://192.168.1.50:1234/v1/chat/completions"))
    }

    @Test
    fun `yes is for one host and port`() {
        PlainProxies.allow("http://192.168.1.50:1234/v1/chat/completions")
        assertFalse(PlainProxies.needsAnswer("http://192.168.1.50:1234/other/path"))
        assertTrue(PlainProxies.needsAnswer("http://192.168.1.50:8080/v1"))
        assertTrue(PlainProxies.needsAnswer("http://192.168.1.51:1234/v1"))
    }

    @Test
    fun `nothing is sent plainly to an address that was not allowed`() {
        try {
            PlainProxies.client(secure, "http://192.168.1.50:1234/v1")
            fail("an address nobody allowed")
        } catch (e: ApiError.Api) {
            assertEquals("PROXY_NOT_ENCRYPTED", e.janitorCode)
        }
        // HTTPS is the client as it was: no way to send plainly.
        assertSame(secure, PlainProxies.client(secure, "https://openrouter.ai/api/v1/chat/completions"))
        assertFalse(ConnectionSpec.CLEARTEXT in secure.connectionSpecs)

        PlainProxies.allow("http://192.168.1.50:1234/v1")
        assertEquals(listOf(ConnectionSpec.CLEARTEXT), PlainProxies.client(secure, "http://192.168.1.50:1234/v1").connectionSpecs)
        assertEquals(listOf(ConnectionSpec.CLEARTEXT), PlainProxies.client(secure, "http://localhost:1234/v1").connectionSpecs)
    }
}
