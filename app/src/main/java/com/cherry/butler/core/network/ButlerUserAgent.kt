package com.cherry.butler.core.network

import android.util.Log
import com.cherry.butler.BuildConfig
import com.cherry.butler.core.diagnostics.Diagnostics
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Every call says it is Butler: `Butler/<version>`. Janitor's firewall turned away the website's
 * generation route with OkHttp's own `okhttp/4.12.0` and let the same request through as
 * `Butler/0.2.3` (2026-10-07). Clients that drop Butler's interceptors on purpose (the user's
 * proxy, storage uploads) add this one back, so no call goes out as OkHttp.
 *
 * Should the firewall start turning `Butler/` away too (its "Access Restricted" page, nothing
 * Janitor said), the call is made once more as Firefox, the browser the website's request was
 * captured from, which passed the same firewall. Calls then stay on Firefox for [FALLBACK_MS]
 * before Butler's own name is tried again. Only janitorai.com is ever retried.
 */
object ButlerUserAgent : Interceptor {
    const val VALUE = "Butler/" + BuildConfig.VERSION_NAME

    /** The website's capture (2026-10-07). */
    const val FIREFOX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:157.0) Gecko/20100101 Firefox/157.0"

    const val FALLBACK_MS = 60 * 60_000L

    @Volatile private var fallbackUntil = 0L

    /** The User-Agent calls go out with right now (the sign-in WebView uses it too). */
    val current: String get() = if (System.currentTimeMillis() < fallbackUntil) FIREFOX else VALUE

    /** For tests: back to Butler's own name. */
    internal fun reset() {
        fallbackUntil = 0L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val ua = current
        val response = chain.proceed(request.newBuilder().header("User-Agent", ua).build())
        if (ua == FIREFOX || response.code != 403 || !isJanitor(request.url.host)) return response
        val body = runCatching { response.peekBody(PEEK).string() }.getOrDefault("")
        if (CloudflareGate.classify(response, body) != CloudflareGate.Kind.Block) return response

        response.close()
        fallbackUntil = System.currentTimeMillis() + FALLBACK_MS
        val path = request.url.encodedPath
        Diagnostics.record("ua", "$VALUE blocked by the firewall on ${request.method} $path; Firefox for an hour")
        runCatching { Log.w(TAG, "$VALUE blocked on $path; retrying as Firefox, and staying on it for an hour") }
        return chain.proceed(request.newBuilder().header("User-Agent", FIREFOX).build())
    }

    private fun isJanitor(host: String) = host == "janitorai.com" || host.endsWith(".janitorai.com")

    private const val TAG = "ButlerUserAgent"
    private const val PEEK = 64L * 1024
}
