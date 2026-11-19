package com.cherry.butler.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthHostsTest {
    @Test
    fun `cloudflare's wall and a gateway down move to the other host`() {
        assertTrue(AuthHosts.refused(403, "text/html; charset=UTF-8"))
        assertTrue(AuthHosts.refused(502, "application/json"))
        assertTrue(AuthHosts.refused(522, null))
        assertTrue(AuthHosts.refused(404, "text/plain"))
    }

    @Test
    fun `gotrue's own answers are passed on`() {
        assertFalse(AuthHosts.refused(200, "application/json"))
        assertFalse(AuthHosts.refused(204, null))
        assertFalse(AuthHosts.refused(400, "application/json; charset=utf-8"))
        assertFalse(AuthHosts.refused(422, "application/json"))
        assertFalse(AuthHosts.refused(500, "application/json"))
    }

    @Test
    fun `a link moves to the other host and keeps everything else`() {
        val url = "https://${AuthHosts.JANITOR}/auth/v1/authorize?provider=google&redirect_to=janitor%3A%2F%2Fauth%2Fdiscord&code_challenge=abc"
        assertEquals(
            "https://${AuthHosts.SUPABASE}/auth/v1/authorize?provider=google&redirect_to=janitor%3A%2F%2Fauth%2Fdiscord&code_challenge=abc",
            AuthHosts.onHost(url, AuthHosts.SUPABASE),
        )
        assertEquals(AuthHosts.JANITOR, AuthHosts.other(AuthHosts.SUPABASE))
    }
}
