package com.cherry.butler.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cherry.butler.BuildConfig
import com.cherry.butler.core.diagnostics.Diagnostics
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The User-Agent Butler's calls go out with: an ordinary browser's, drawn at random from
 * [BrowserUserAgent] when the app first runs and kept from then on, in every call and in the
 * sign-in WebView alike. Kept, because Cloudflare's clearance cookie from the sign-in Turnstile
 * is bound to the User-Agent it was earned under: change the name and the cookie is worthless.
 *
 * Why a browser's: Janitor's firewall judges by this header. It turned away OkHttp's own
 * `okhttp/4.12.0` (2026-10-07), then `Butler/0.3.0` by name on the mobile route (2026-10-08),
 * both while the website's browser went through. A browser's name is what the website's own
 * requests carry, so it is what the firewall is tuned to pass.
 *
 * Should the firewall turn the chosen browser away ("Access Restricted", nothing Janitor said),
 * the call is made once more as a different browser; if that one passes, it becomes the device's
 * from then on. Only janitorai.com is ever retried. Clients that drop Butler's interceptors on
 * purpose (the user's proxy, storage uploads) add this one back, so no call goes out as OkHttp.
 *
 * Butler's own name, [APP], goes only to GitHub's API (the update check), which asks who calls.
 */
object ButlerUserAgent : Interceptor {
    const val APP = "Butler/" + BuildConfig.VERSION_NAME

    private const val PREFS = "butler_prefs"
    private const val KEY = "user_agent"

    private var prefs: SharedPreferences? = null

    @Volatile private var chosen: String = BrowserUserAgent.pick()

    /** The User-Agent calls go out with right now (the sign-in WebView uses it too). */
    val current: String get() = chosen

    /** Reads the device's browser, choosing one the first time. */
    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(KEY, null)
        if (saved != null && BrowserUserAgent.familyOf(saved) != null) {
            chosen = saved
        } else {
            keep(BrowserUserAgent.pick())
        }
    }

    private fun keep(userAgent: String) {
        chosen = userAgent
        prefs?.edit()?.putString(KEY, userAgent)?.apply()
    }

    /** For tests: a known browser, nothing saved. */
    internal fun reset(userAgent: String = BrowserUserAgent.pick()) {
        prefs = null
        chosen = userAgent
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val ua = chosen
        val response = chain.proceed(request.newBuilder().header("User-Agent", ua).build())
        if (response.code != 403 || !isJanitor(request.url.host)) return response
        if (!blocked(response)) return response

        response.close()
        val other = BrowserUserAgent.pick(not = ua)
        val path = request.url.encodedPath
        Diagnostics.record("ua", "${name(ua)} blocked by the firewall on ${request.method} $path; trying ${name(other)}")
        runCatching { Log.w(TAG, "${name(ua)} blocked on $path; retrying as ${name(other)}") }
        val retry = proceedFresh(chain, request.newBuilder().header("User-Agent", other).build())
        if (retry.code == 403 && blocked(retry)) {
            // Not the name, then: the firewall is after something else. The device keeps its browser.
            Diagnostics.record("ua", "${name(other)} blocked too on ${request.method} $path; keeping ${name(ua)}")
            return retry
        }
        keep(other)
        Diagnostics.record("ua", "${name(other)} passed; the device is ${name(other)} from now on")
        return retry
    }

    /**
     * The retry, on a connection the firewall hasn't hung up on. Cloudflare closes the
     * connection behind a block while saying keep-alive, so the first try at reusing it ends
     * in "unexpected end of stream"; OkHttp then drops it, and the next try opens a new one.
     */
    private fun proceedFresh(chain: Interceptor.Chain, request: okhttp3.Request): Response =
        try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            runCatching { Log.w(TAG, "retry's connection was gone (${e.message}); once more on a new one") }
            chain.proceed(request)
        }

    private fun blocked(response: Response): Boolean {
        val body = runCatching { response.peekBody(PEEK).string() }.getOrDefault("")
        val kind = CloudflareGate.classify(response, body) ?: return false
        runCatching { CloudflareGate.record(kind, response, body) }
        return kind == CloudflareGate.Kind.Block
    }

    private fun name(ua: String) = BrowserUserAgent.familyOf(ua)?.name ?: ua

    private fun isJanitor(host: String) = host == "janitorai.com" || host.endsWith(".janitorai.com")

    private const val TAG = "ButlerUserAgent"
    private const val PEEK = 64L * 1024
}
