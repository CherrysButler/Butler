package com.cherry.butler.desktop

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/** Butler's one Context on the desktop. Its files live in %APPDATA%\Butler. */
object DesktopContext : Context() {
    val home: File = File(
        System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "/.config"),
        if (com.cherry.butler.BuildConfig.DEBUG) "Butler (dev)" else "Butler",
    ).apply { mkdirs() }

    override val filesDir: File = File(home, "files").apply { mkdirs() }
    override val cacheDir: File = File(
        System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { File(it, home.name) } ?: File(home, "cache-root"),
        "cache",
    ).apply { mkdirs() }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = DesktopPrefs.open(name)

    override fun getSystemService(name: String): Any? = when (name) {
        POWER_SERVICE -> android.os.PowerManager()
        SENSOR_SERVICE -> android.hardware.SensorManager()
        else -> null
    }

    /**
     * What the phone would hand to another app: a link opens in the browser; shared text goes
     * to the clipboard, with a toast saying so.
     */
    override fun startActivity(intent: Intent) {
        val target = if (intent.action == Intent.ACTION_CHOOSER) intent.intentExtra(Intent.EXTRA_INTENT) ?: intent else intent
        when (target.action) {
            Intent.ACTION_VIEW -> target.data?.toString()?.let(::browse)
            Intent.ACTION_SEND -> {
                val text = target.getStringExtra(Intent.EXTRA_TEXT) ?: return
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                DesktopToasts.show("Copied")
            }
        }
    }

    fun browse(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
}
