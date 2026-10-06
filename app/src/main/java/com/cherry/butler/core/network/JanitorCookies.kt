package com.cherry.butler.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cloudflare's cookies for janitorai.com, kept and sent back as a browser does. Nothing else:
 * no other cookie is stored, and no other host is sent any.
 *
 * - `cf_clearance`: Cloudflare's "this browser passed a check". Janitor's sign-in Turnstile
 *   hands it out (pre-clearance: after a pass the page POSTs to
 *   `/cdn-cgi/challenge-platform/h/g/c/<ray>`, which sets it for a year; HAR from the website,
 *   2026-10-07). Butler's sign-in runs that same Turnstile in a WebView, so the cookie lands in
 *   the WebView and [importFromWebView] brings it over. It is tied to the User-Agent, which is
 *   why the sign-in WebView says `Butler/<version>` as every other call does.
 * - `__cf_bm`: Cloudflare's bot-scoring cookie, 30 minutes, refreshed on answers.
 * - `__cfwaitingroom_…`: the waiting room's place in the queue. Sending it back is what lets a
 *   queued request move up instead of starting over.
 *
 * Janitor does not require any of them today (Butler's sends go through without). They are
 * kept so it keeps working if it starts to. Cleared on sign-out.
 */
object JanitorCookies : CookieJar {
    private const val TAG = "JanitorCookies"
    private const val PREFS = "butler_cf_cookies"
    private const val SITE = "janitorai.com"

    private var prefs: SharedPreferences? = null
    private val store = ConcurrentHashMap<String, Cookie>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            val url = "https://$SITE/".toHttpUrl()
            for ((key, raw) in p.all) {
                val cookie = (raw as? String)?.let { Cookie.parse(url, it) } ?: continue
                if (cookie.expiresAt > System.currentTimeMillis()) store[key] = cookie
            }
        }
    }

    private fun isCloudflare(name: String) =
        name == "cf_clearance" || name == "__cf_bm" || name.startsWith("__cfwaitingroom")

    private fun isJanitor(host: String) = host == SITE || host.endsWith(".$SITE")

    private fun key(c: Cookie) = "${c.name}@${c.domain}"

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isJanitor(url.host)) return
        val kept = cookies.filter { isCloudflare(it.name) }
        if (kept.isEmpty()) return
        val edit = prefs?.edit()
        for (c in kept) {
            store[key(c)] = c
            edit?.putString(key(c), c.toString())
        }
        edit?.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isJanitor(url.host)) return emptyList()
        val now = System.currentTimeMillis()
        store.entries.removeIf { it.value.expiresAt <= now }
        return store.values.filter { it.matches(url) }
    }

    /**
     * Brings Cloudflare's cookies over from the sign-in WebView, once Turnstile has passed.
     * The WebView keeps no expiry it will tell us, so the lifetimes are the ones Cloudflare set
     * on the website: a year for the clearance, 30 minutes for the rest. Cloudflare's next
     * answers refresh them anyway.
     */
    fun importFromWebView() {
        val raw = runCatching { CookieManager.getInstance().getCookie("https://$SITE/") }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val brought = mutableListOf<String>()
        val edit = prefs?.edit()
        for (pair in raw.split(';')) {
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=', "").trim()
            if (!isCloudflare(name) || value.isEmpty()) continue
            val life = if (name == "cf_clearance") TimeUnit.DAYS.toMillis(365) else TimeUnit.MINUTES.toMillis(30)
            val cookie = Cookie.Builder()
                .name(name).value(value)
                .domain(SITE).path("/")
                .expiresAt(now + life)
                .secure().httpOnly()
                .build()
            store[key(cookie)] = cookie
            edit?.putString(key(cookie), cookie.toString())
            brought += name
        }
        edit?.apply()
        // Names only: the values are credentials.
        Log.i(TAG, "from the sign-in WebView: $brought (WebView had ${raw.split(';').map { it.substringBefore('=').trim() }})")
    }

    /** Forgets every Cloudflare cookie, here and in the WebView. On sign-out. */
    fun clear() {
        store.clear()
        prefs?.edit()?.clear()?.apply()
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    /** The names held now, for the diagnostics report. */
    fun names(): List<String> = store.values.map { it.name }.distinct()
}
