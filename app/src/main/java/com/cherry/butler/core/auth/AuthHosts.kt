package com.cherry.butler.core.auth

import android.content.Context
import android.content.SharedPreferences
import com.cherry.butler.core.config.JanitorConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where Janitor's sign-in service (Supabase's GoTrue) answers.
 *
 * Janitor put it behind its own name, `auth.janitorai.com`, until 2026-11-19. That day the name
 * began answering every client, browsers included, with Cloudflare's "Access Restricted", and the
 * website moved to the project's own Supabase address (HAR of the website's password and Google
 * sign-ins). Both names reach the same project with the same public key, so accounts, sessions and
 * refresh tokens work on either.
 *
 * So Butler keeps both. A call goes to the one that last answered; if that one is walled off, down
 * or out of reach, the same call goes straight to the other, and that one is remembered, across
 * launches too. Janitor moving it back, or away again, needs no new release.
 */
object AuthHosts {
    const val SUPABASE = "mcmzxtzommpnxkynddbo.supabase.co"
    const val JANITOR = "auth.janitorai.com"

    /** The website's current choice first: what a fresh install tries before anything is learned. */
    val all = listOf(SUPABASE, JANITOR)

    private const val PREFS = "butler_auth_host"
    private const val KEY = "host"
    private var prefs: SharedPreferences? = null

    /** The host that last answered. */
    @Volatile var current: String = all.first()
        private set

    /** The host the Supabase client was built with: its own links (OAuth) point there. */
    @Volatile var configured: String = all.first()
        private set

    /** Loads what was learned last time and returns the base URL to build the Supabase client with. */
    fun startUrl(context: Context): String {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            p.getString(KEY, null)?.takeIf { it in all }?.let { current = it }
        }
        configured = current
        return "https://$configured"
    }

    fun other(host: String): String = all.first { it != host }

    fun remember(host: String) {
        if (host == current || host !in all) return
        current = host
        prefs?.edit()?.putString(KEY, host)?.apply()
    }

    /**
     * An answer GoTrue itself didn't give: Cloudflare's wall or error page, or a gateway that is down.
     * GoTrue answers its errors in JSON (a wrong password is a JSON 400), and those are real answers
     * that the other host would only repeat.
     */
    fun refused(status: Int, contentType: String?): Boolean =
        status in 502..504 || status in 520..530 || (status >= 400 && contentType?.contains("json", ignoreCase = true) != true)

    /**
     * Which host answers right now, for a link Butler opens in the browser (Google, Discord), where no
     * second try is possible once the page is open. Asks the current host's health check, then the other's.
     */
    suspend fun reachable(): String = withContext(Dispatchers.IO) {
        val first = current
        when {
            healthy(first) -> first
            healthy(other(first)) -> other(first).also(::remember)
            else -> first
        }
    }

    private fun healthy(host: String): Boolean = runCatching {
        val c = URL("https://$host/auth/v1/health").openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 6_000
        c.setRequestProperty("apikey", JanitorConfig.SUPABASE_ANON_KEY)
        try {
            c.responseCode == 200 && c.contentType?.contains("json", ignoreCase = true) == true
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)

    /** [url] with its host swapped for [host]. */
    fun onHost(url: String, host: String): String =
        all.fold(url) { u, h -> u.replaceFirst("://$h", "://$host") }

    /**
     * Installed in the Supabase client: every call to either host goes to [current] first, and to the
     * other when the first is [refused] or can't be reached. GoTrue's own answers, errors included,
     * are passed on as they are.
     */
    val Fallback = createClientPlugin("AuthHostFallback") {
        on(Send) { request ->
            if (request.url.host !in all) return@on proceed(request)
            val first = current
            request.url.host = first
            val firstCall = try {
                proceed(request)
            } catch (e: IOException) {
                null
            }
            if (firstCall != null && !refused(firstCall.response.status.value, firstCall.response.headers[HttpHeaders.ContentType])) {
                return@on firstCall
            }
            val second = other(first)
            request.url.host = second
            val secondCall = try {
                proceed(request)
            } catch (e: IOException) {
                firstCall ?: throw e
                return@on firstCall
            }
            if (refused(secondCall.response.status.value, secondCall.response.headers[HttpHeaders.ContentType])) {
                firstCall ?: secondCall
            } else {
                remember(second)
                secondCall
            }
        }
    }
}
