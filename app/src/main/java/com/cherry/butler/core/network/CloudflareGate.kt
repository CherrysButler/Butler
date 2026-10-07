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
 *   the header Cloudflare puts on challenge pages). Not seen on an API call yet.
 * - The **waiting room**: Janitor's queue for busy times (the website carries a
 *   `__cfwaitingroom_…` cookie). It can arrive as a 200 page, so successes are checked too.
 * - A **block**: a firewall rule's 403 page ("Access Restricted"), with nothing to solve.
 *   Seen 2026-10-06/07 for OkHttp's default User-Agent on the website's `/generateAlpha`.
 *
 * Every one goes into the diagnostics report (status, path, Ray ID, the cookie *names*).
 * Debug builds also keep the whole answer, cookie values left out, in
 * `files/cloudflare/` (pull with `adb exec-out run-as com.cherry.butler.debug ...`).
 */
object CloudflareGate {
    enum class Kind { Challenge, WaitingRoom, Block }

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
                serverMessage = "Cloudflare (Janitor's firewall) asked for a human check Butler can't answer yet. Try again in a few minutes.",
                retryable = false,
            )
            Kind.WaitingRoom -> ApiError.Api(
                code = r.code,
                janitorCode = "CF_WAITING_ROOM",
                serverMessage = "Janitor is busy and has a queue right now. Try again in a minute.",
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
        return when {
            r.header("cf-mitigated").equals("challenge", ignoreCase = true) -> Kind.Challenge
            body.contains("\"cfWaitingRoom\"") -> Kind.WaitingRoom
            cookies.any { it.startsWith("__cfwaitingroom") } && !json -> Kind.WaitingRoom
            r.code == 403 && !json && (html || r.header("Server").equals("cloudflare", ignoreCase = true)) -> Kind.Block
            else -> null
        }
    }

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
