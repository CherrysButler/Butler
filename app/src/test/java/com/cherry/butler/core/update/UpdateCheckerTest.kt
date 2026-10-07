package com.cherry.butler.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `versions compare by number, not by text`() {
        assertTrue(UpdateChecker.isNewer("0.2.10", "0.2.9"))
        assertTrue(UpdateChecker.isNewer("v0.3.0", "0.2.9"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.2.3", "0.2.3"))
        assertFalse(UpdateChecker.isNewer("0.2.3", "0.2.4"))
        assertFalse(UpdateChecker.isNewer("0.2", "0.2.0"))
    }

    @Test
    fun `release notes become short plain lines`() {
        val body = """
            ## Fixed

            - **Sending works again.** Janitor's firewall turned proxy sends away.
            - Tags have colours, see [the README](https://x/y) for more
            - `{{user}}` is filled in

            ## Install

            Grab **Butler-0.2.4.apk** below.
        """.trimIndent()
        org.junit.Assert.assertEquals(
            listOf("Sending works again", "Tags have colours, see the README for more", "{{user}} is filled in"),
            UpdateChecker.summarize(body),
        )
    }
}
