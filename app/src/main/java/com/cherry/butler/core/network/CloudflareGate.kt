package com.cherry.butler.core.network

import android.content.Context
import android.util.Log
import com.cherry.butler.BuildConfig
import com.cherry.butler.core.diagnostics.Diagnostics
import okhttp3.Response
import java.io.File

/**
 * Cloudflare's own answers in front of Janitor, told apart from Janitor's, so the user is
 * told what actually happened and the answer is kept for building the response to it.
 *
 * - A **challenge**: Cloudflare wants a browser to prove itself (`cf-mitigated: challenge`,
 *   the header Cloudflare puts on challenge pages). Seen on the website's API since 2026-10-13;
 *   the app passes it in a WebView and the call goes again (CloudflareCheck).
 * - The **waiting room**: Janitor's queue for busy times (the website carries a
 *   `__cfwaitingroom_…` cookie). It can arrive as a 200 page, so successes are checked too.
 * - A **block**: a firewall rule's 403 page ("Access Restricted"), with nothing to solve.
 *   Seen 2026-10-06/07 for OkHttp's default User-Agent on the website's `/generateAlpha`.
 * - A **page**: any other web page where Janitor's data belongs. The waiting room doesn't
 *   always set its cookie again (2026-11-03: a queued send's second answer came without it
 *   and was read as a broken reply), so a page is never taken for Janitor's answer.
 *
 * Every one goes into the diagnostics report (status, path, Ray ID, the cookie *names*).
 * Debug builds also keep the whole answer, cookie values left out, in
 * `files/cloudflare/` (pull with `adb exec-out run-as com.cherry.butler.debug ...`).
 */
object CloudflareGate {
    enum class Kind { Challenge, WaitingRoom, Block, Page }

    private const val TAG = "CloudflareGate"
    private const val KEEP = 20

    private var captures: File? = null

    fun init(context: Context) {
        if (BuildConfig.DEBUG) captures = File(context.filesDir, "cloudflare")
    }

    /**
     * Cloudflare's answer as an [ApiError], or null when [r] is Janitor's own. [body] is the
     * response body as read (or the start of it).
     */
    fun check(r: Response, body: String): ApiError? {
        val kind = classify(r, body) ?: return null
        record(kind, r, body)
        return when (kind) {
            Kind.Challenge -> ApiError.Api(
                code = r.code,
                janitorCode = "CF_CHALLENGE",
                serverMessage = "Cloudflare (Janitor's firewall) asked for a quick check that wasn't finished. Try again.",
                retryable = false,
            )
            Kind.WaitingRoom -> ApiError.Queued(retryAfterMillis = refreshMillis(r, body))
            Kind.Page -> ApiError.Api(
                code = r.code,
                janitorCode = "CF_PAGE",
                serverMessage = "Janitor answered with a web page instead of its data. Try again in a moment.",
                retryable = true,
            )
            Kind.Block -> ApiError.Api(
                code = r.code,
                janitorCode = "FIREWALL",
                serverMessage = "Janitor's firewall (Cloudflare) turned this away, not your account. Try again in a minute.",
                retryable = false,
            )
        }
    }

    fun classify(r: Response, body: String): Kind? {
        val cookies = r.headers("Set-Cookie").map { it.substringBefore('=').trim() }
        val start = body.trimStart()
        val json = start.startsWith("{") || start.startsWith("[")
        val html = r.header("Content-Type").orEmpty().startsWith("text/html") || start.startsWith("<")
        // The queue's cookie went out with the call: an answer that isn't Janitor's is the queue's.
        val queued = r.request.header("Cookie").orEmpty().contains("__cfwaitingroom")
        val queueWords = html && QUEUE_WORDS.any { body.contains(it, ignoreCase = true) }
        return when {
            r.header("cf-mitigated").equals("challenge", ignoreCase = true) -> Kind.Challenge
            body.contains("\"cfWaitingRoom\"") -> Kind.WaitingRoom
            cookies.any { it.startsWith("__cfwaitingroom") } && !json -> Kind.WaitingRoom
            (queued || queueWords) && !json && (html || r.isSuccessful) -> Kind.WaitingRoom
            r.code == 403 && !json && (html || r.header("Server").equals("cloudflare", ignoreCase = true)) -> Kind.Block
            r.isSuccessful && html && !json -> Kind.Page
            else -> null
        }
    }

    /** What the waiting room's own page says on it. */
    private val QUEUE_WORDS = listOf("waiting room", "waitingroom", "cf-waiting-room", "cf_waiting_room")

    /**
     * How long the waiting room asks a browser to wait before checking back: its refresh (the
     * `Refresh` header, a `<meta http-equiv="refresh">`, or its JSON's `refreshIntervalSeconds`),
     * else 20 seconds, Cloudflare's default.
     */
    internal fun refreshMillis(r: Response, body: String): Long {
        val seconds = r.header("Refresh")?.substringBefore(';')?.trim()?.toIntOrNull()
            ?: META_REFRESH.find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: JSON_REFRESH.find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: DEFAULT_REFRESH_S
        return seconds.coerceIn(MIN_REFRESH_S, MAX_REFRESH_S) * 1_000L
    }

    private val META_REFRESH = Regex("""http-equiv=["']?refresh["']?[^>]*content=["']?(\d+)""", RegexOption.IGNORE_CASE)
    private val JSON_REFRESH = Regex(""""refreshIntervalSeconds"\s*:\s*(\d+)""")
    private const val DEFAULT_REFRESH_S = 20
    private const val MIN_REFRESH_S = 5
    private const val MAX_REFRESH_S = 60

    /** Notes [kind] in Diagnostics and, in debug builds, keeps the whole answer under files/cloudflare. */
    internal fun record(kind: Kind, r: Response, body: String) {
        val path = r.request.url.encodedPath
        val ray = r.header("cf-ray")
        val cookieNames = r.headers("Set-Cookie").map { it.substringBefore('=').trim() }
        Diagnostics.record("cloudflare", "$kind ${r.code} ${r.request.method} $path ray=$ray mitigated=${r.header("cf-mitigated")} cookies=$cookieNames")
        Log.w(TAG, "$kind ${r.code} ${r.request.method} $path ray=$ray")
        val dir = captures ?: return
        runCatching {
            dir.mkdirs()
            val text = buildString {
                appendLine("$kind  ${r.request.method} ${r.request.url}")
                appendLine("${r.protocol} ${r.code} ${r.message}")
                for ((name, value) in r.headers) {
                    // Cookie values are credentials; their names are what tell the story.
                    appendLine(if (name.equals("Set-Cookie", ignoreCase = true)) "$name: ${value.substringBefore('=')}=<value> ${value.substringAfter(';', "").let { if (it.isEmpty()) "" else ";$it" }}" else "$name: $value")
                }
                appendLine()
                append(body.take(64_000))
            }
            File(dir, "${System.currentTimeMillis()}-$kind.txt").writeText(text)
            dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        }
    }
}
