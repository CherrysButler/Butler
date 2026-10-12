package com.cherry.butler.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.network.CloudflareCheck
import com.cherry.butler.core.network.JanitorCookies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cloudflare's check, over the app, whenever one of Butler's calls is stopped by it
 * (CloudflareCheck); while this is composed the app says it can show one.
 *
 * Not the stopped page in a WebView: Cloudflare challenges Butler's own connection, and the
 * WebView, a real browser, goes through unchallenged and is handed nothing (2026-10-13). What
 * clears the calls is Janitor's Turnstile, the sign-in box: Janitor has it pre-clear, so passing
 * it hands over a clearance whether or not anything was challenged, and with the calls under the
 * WebView's own name (ButlerUserAgent.adopt) they go through. The token itself isn't used.
 */
@Composable
fun CloudflareCheckHost() {
    DisposableEffect(Unit) {
        CloudflareCheck.hosted = true
        onDispose { CloudflareCheck.hosted = false }
    }
    val asking by CloudflareCheck.asking.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (asking != null) {
        TurnstileSheet(
            onToken = {
                scope.launch {
                    // The clearance lands a moment after the token.
                    delay(CLEARANCE_LAG_MS)
                    JanitorCookies.importFromWebView()
                    CloudflareCheck.finish(true)
                }
            },
            onDismiss = { CloudflareCheck.finish(false) },
            check = TurnstileCheck.Firewall,
        )
    }
}

private const val CLEARANCE_LAG_MS = 1_500L
