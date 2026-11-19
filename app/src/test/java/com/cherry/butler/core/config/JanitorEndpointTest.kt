package com.cherry.butler.core.config

import com.cherry.butler.core.network.AuthInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JanitorEndpointTest {
    @Test fun originalEndpointKeepsTheExistingSessionStore() {
        val original = JanitorEndpoint.fromHost(null)
        val replacement = JanitorEndpoint.fromHost("jani.com")

        assertEquals(JanitorEndpoint.JanitorAI, original)
        assertEquals(JanitorEndpoint.Jani, replacement)
        assertEquals("butler_session", original.scopedName("butler_session"))
        assertEquals("butler_session.jani.com", replacement.scopedName("butler_session"))
    }

    @Test fun anEndpointOnlyAuthenticatesRequestsToItsOwnSecureOrigin() {
        for (endpoint in JanitorEndpoint.entries) {
            val other = JanitorEndpoint.entries.single { it != endpoint }
            fun signs(url: String) = AuthInterceptor.isJanitorApi(url.toHttpUrl(), endpoint)
            assertTrue(signs("${endpoint.origin}/hampter/characters"))
            assertTrue(signs("${endpoint.origin}/generateAlpha"))
            assertFalse(signs("${other.origin}/hampter/characters"))
            assertFalse(signs("http://${endpoint.host}/generateAlpha"))
            assertFalse(signs("https://${endpoint.host}:8443/generateAlpha"))
            assertFalse(signs("https://www.${endpoint.host}/generateAlpha"))
            assertFalse(signs("https://${endpoint.host}.example.com/generateAlpha"))
            assertFalse(signs("${endpoint.mediaUrl}/bot-avatars/avatar.webp"))
        }
    }
}
