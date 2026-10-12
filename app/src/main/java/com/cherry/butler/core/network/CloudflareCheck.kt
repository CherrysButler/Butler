package com.cherry.butler.core.network

import com.cherry.butler.core.diagnostics.Diagnostics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Passing Cloudflare's browser check ("Security Verification") when it stops one of Butler's
 * calls. Since the move to the website's API the calls meet the same firewall the website does,
 * and for some people, some of the time, it asks for a check (`cf-mitigated: challenge`,
 * 2026-10-13). A call that meets one waits here while the app shows the check in a WebView
 * (Cloudflare's page usually passes itself in a moment, at most it wants a tap); the clearance
 * it earns is brought over with the WebView's own User-Agent (JanitorCookies, ButlerUserAgent),
 * and the call goes again. Calls stopped together share one check.
 *
 * Only where something shows the check ([hosted]); elsewhere a challenge is reported as before.
 */
object CloudflareCheck {
    /** The page to pass while a check is wanted; null otherwise. The app shows it over everything. */
    private val _asking = MutableStateFlow<String?>(null)
    val asking: StateFlow<String?> = _asking.asStateFlow()

    /** Whether the app has a place to show the check right now. */
    @Volatile var hosted: Boolean = false

    private var pending: CompletableDeferred<Boolean>? = null
    @Volatile private var passedAt = 0L

    /**
     * Waits for the check to be passed for [url] (the page the call was stopped on). True when
     * it was, so the call may go again; false when it was closed, timed out, or can't be shown.
     */
    suspend fun pass(url: String): Boolean {
        if (!hosted) return false
        val wait = synchronized(this) {
            // A check passed a moment ago: this call raced it, and may simply go again.
            if (System.currentTimeMillis() - passedAt < RECENT_MS) return true
            pending ?: CompletableDeferred<Boolean>().also {
                pending = it
                _asking.value = url
                Diagnostics.record("cloudflare", "check shown for ${url.substringAfter("janitorai.com").substringBefore('?')}")
            }
        }
        return withTimeoutOrNull(WAIT_MS) { wait.await() } ?: run { finish(false); false }
    }

    /** The check's outcome, from the sheet: every call waiting on it goes again, or gives up. */
    fun finish(passed: Boolean) {
        synchronized(this) {
            if (passed) passedAt = System.currentTimeMillis()
            pending?.complete(passed)
            pending = null
            _asking.value = null
        }
        Diagnostics.record("cloudflare", if (passed) "check passed" else "check not passed")
    }

    private const val RECENT_MS = 10_000L
    /** Long enough to open the app and tap, not so long a call hangs forever. */
    private const val WAIT_MS = 120_000L
}
