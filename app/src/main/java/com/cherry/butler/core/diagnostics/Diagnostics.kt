package com.cherry.butler.core.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * What Butler can tell you about itself when something goes wrong, assembled on request
 * and handed to the user to send on. Butler never calls home (TRUST.md), so this is the
 * whole reporting story: a page of text the user reads first and shares if they choose.
 *
 * It holds the last [KEEP] failures (HTTP status, method and path, Janitor's error code,
 * a send job's step), the last crash, and Butler's own recent log lines. Nothing in it is
 * a message, a token or a key: paths carry no bodies, and the log is scrubbed of anything
 * shaped like a credential before it leaves.
 */
object Diagnostics {
    private const val KEEP = 120
    private const val LOG_LINES = 400
    private const val CRASH_FILE = "last-crash.txt"

    private data class Event(val at: Long, val kind: String, val detail: String)

    private val events = ArrayDeque<Event>(KEEP)

    /** A failure worth remembering: [kind] is a short class ("http", "send"), [detail] one line. */
    fun record(kind: String, detail: String) {
        synchronized(events) {
            if (events.size >= KEEP) events.removeFirst()
            events.addLast(Event(System.currentTimeMillis(), kind, detail.take(300)))
        }
    }

    /**
     * Keeps the last crash on disk, then lets the system handle it as before. Installed
     * once at start; the file is read into the next report.
     */
    fun installCrashKeeper(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                File(context.filesDir, CRASH_FILE).writeText(
                    "${stamp(System.currentTimeMillis())} on ${thread.name}\n${scrub(e.stackTraceToString())}",
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The report, as text. Built off the main thread by the caller; reading logcat takes a moment. */
    fun report(context: Context): String = buildString {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
        appendLine("Butler ${info?.versionName ?: "?"} (${info?.longVersionCode ?: "?"}) · ${context.packageName}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Made ${stamp(System.currentTimeMillis())}")
        appendLine()

        val crash = File(context.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText()
        if (crash != null) {
            appendLine("== Last crash")
            appendLine(crash.trim())
            appendLine()
        }

        appendLine("== Recent failures (newest last)")
        val copy = synchronized(events) { events.toList() }
        if (copy.isEmpty()) appendLine("none this session")
        copy.forEach { appendLine("${stamp(it.at)}  ${it.kind.padEnd(5)} ${it.detail}") }
        appendLine()

        appendLine("== Butler's log (last $LOG_LINES lines, scrubbed)")
        appendLine(scrub(ownLog()))
    }

    /** Only this process's lines: Android lets an app read its own logcat without a permission. */
    private fun ownLog(): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "time", "-t", LOG_LINES.toString(), "--pid=${android.os.Process.myPid()}")
            .redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { it.readText() }.also { p.waitFor() }
    }.getOrElse { "(log unavailable: ${it.javaClass.simpleName})" }

    private val bearer = Regex("""Bearer\s+[A-Za-z0-9._\-]+""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}""")
    private val keyish = Regex("""(?i)(sk-|key[=:]\s*|token[=:]\s*|apikey[=:]\s*)[A-Za-z0-9._\-]{8,}""")
    private val email = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    /** Anything shaped like a credential or an address is replaced before the text leaves. */
    fun scrub(text: String): String = text
        .replace(bearer, "Bearer <redacted>")
        .replace(jwt, "<jwt>")
        .replace(keyish) { m -> m.groupValues[1] + "<redacted>" }
        .replace(email, "<email>")

    private fun stamp(at: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(at))
}
