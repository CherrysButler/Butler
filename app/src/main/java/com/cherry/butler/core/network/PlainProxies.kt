package com.cherry.butler.core.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * The proxies Butler may talk to without HTTPS, and the only way a request leaves unencrypted.
 *
 * A model on the person's own network is reached at an address like `http://192.168.1.50:1234`:
 * an IP has no certificate, so there is no HTTPS to ask for. What goes to it (the API key, the
 * whole chat) then travels as plain text, readable by anything on the network between. Butler
 * sends that way only where the person said so, for that address, on this device:
 *
 * - every network client Butler has is HTTPS-only ([SECURE], set where the clients are built),
 *   so nothing else in the app can send plainly, whatever address it is handed;
 * - a call to the person's proxy takes its client from [client], which hands back one that
 *   may send plainly only for an address that was allowed ([allow], from the proxy editor's
 *   warning), and refuses any other `http://` address with words that say what to do;
 * - the answer is kept here, on the device: a proxy saved from the website, or on another
 *   device, has not been allowed on this one.
 *
 * This machine itself (`localhost`, `127.0.0.1`) needs no answer: nothing leaves it.
 */
object PlainProxies {

    /** What every client is built with: TLS, and no plain HTTP. */
    val SECURE: List<ConnectionSpec> = listOf(ConnectionSpec.MODERN_TLS)

    private const val PREFS = "butler_prefs"
    private const val KEY = "plain_proxies"

    private var prefs: SharedPreferences? = null

    @Volatile
    private var allowed: Set<String> = emptySet()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        allowed = p.getStringSet(KEY, null)?.toSet().orEmpty()
    }

    /** Whether [url] is an address without HTTPS. */
    fun isPlain(url: String): Boolean = url.trim().toHttpUrlOrNull()?.let { !it.isHttps } == true

    /** Whether [url] has to be allowed before anything is sent to it: plain, and not this machine. */
    fun needsAnswer(url: String): Boolean {
        val u = url.trim().toHttpUrlOrNull() ?: return false
        return !u.isHttps && !isThisMachine(u) && place(u) !in allowed
    }

    /** The person read the warning and said yes, for [url]'s host and port. */
    fun allow(url: String) {
        val u = url.trim().toHttpUrlOrNull()?.takeIf { !it.isHttps } ?: return
        allowed = allowed + place(u)
        prefs?.edit()?.putStringSet(KEY, allowed)?.apply()
    }

    /** Where [url] points, as the warning names it: `192.168.1.50:1234`. */
    fun shown(url: String): String = url.trim().toHttpUrlOrNull()?.let(::place) ?: url.trim()

    /**
     * The client a call to the proxy at [url] is made with: [secure] itself for HTTPS, one
     * that may send plainly for an address that was allowed, and an error for any other.
     */
    fun client(secure: OkHttpClient, url: String): OkHttpClient {
        val u = url.trim().toHttpUrlOrNull() ?: return secure
        if (u.isHttps) return secure
        if (!isThisMachine(u) && place(u) !in allowed) {
            throw ApiError.Api(
                0,
                janitorCode = "PROXY_NOT_ENCRYPTED",
                serverMessage = "This proxy's address has no HTTPS, so nothing was sent. Open the proxy in Settings and save it to allow it.",
                retryable = false,
            )
        }
        return secure.newBuilder().connectionSpecs(listOf(ConnectionSpec.CLEARTEXT)).build()
    }

    private fun place(u: HttpUrl): String = "${u.host}:${u.port}"

    internal fun isThisMachine(u: HttpUrl): Boolean =
        u.host == "localhost" || u.host.endsWith(".localhost") || u.host == "::1" || u.host == "0:0:0:0:0:0:0:1" ||
            Regex("""127\.\d{1,3}\.\d{1,3}\.\d{1,3}""").matches(u.host)

    /** For tests: start from nothing allowed. */
    internal fun reset() { allowed = emptySet() }
}
