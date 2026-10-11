package com.cherry.butler.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * One Butler at a time. The first one listens on a local port; a second launch (the browser
 * handing back a `janitor:` sign-in link, or a double-click) passes its arguments over and
 * exits, the way the phone's deep link reaches the running app.
 */
object DesktopInstance {
    private const val SHCNE_ASSOCCHANGED = 0x08000000
    private const val SHCNF_IDLIST = 0

    private val portFile = File(DesktopContext.home, "instance.port")

    /** False when another Butler took [args]; this process should then exit. */
    fun claim(args: Array<String>, onArgs: (List<String>) -> Unit): Boolean {
        portFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()?.let { port ->
            val handed = runCatching {
                Socket(InetAddress.getLoopbackAddress(), port).use { s ->
                    s.getOutputStream().bufferedWriter().apply {
                        write("butler\n")
                        args.forEach { write(it.replace("\n", " ") + "\n") }
                        flush()
                    }
                }
            }.isSuccess
            if (handed) return false
        }
        val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        portFile.writeText(server.localPort.toString())
        Runtime.getRuntime().addShutdownHook(Thread { portFile.delete() })
        thread(isDaemon = true, name = "butler-instance") {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use { s ->
                        val lines = s.getInputStream().bufferedReader().readLines()
                        if (lines.firstOrNull() == "butler") onArgs(lines.drop(1))
                    }
                }
            }
        }
        return true
    }

    /**
     * Makes `janitor:` links open this Butler, for this Windows user only (HKCU), as an
     * installed app does. Only for the installed build: a run from the build tools has no
     * Butler.exe to point at.
     */
    fun registerLinks() {
        val exe = installedExe() ?: return
        val key = "Software\\Classes\\janitor"
        val progId = "Software\\Classes\\Butler.janitor"
        val capabilities = "Software\\Butler\\Capabilities"
        val command = "\"$exe\" \"%1\""
        runCatching {
            val hk = WinReg.HKEY_CURRENT_USER
            fun set(path: String, name: String, value: String) {
                if (!Advapi32Util.registryKeyExists(hk, path)) Advapi32Util.registryCreateKey(hk, path)
                Advapi32Util.registrySetStringValue(hk, path, name, value)
            }
            if (Advapi32Util.registryKeyExists(hk, "$progId\\shell\\open\\command") &&
                Advapi32Util.registryGetStringValue(hk, "$progId\\shell\\open\\command", "") == command
            ) return
            // The classic scheme key, which older Windows and most callers read.
            set(key, "", "URL:Butler")
            set(key, "URL Protocol", "")
            set("$key\\DefaultIcon", "", "\"$exe\",0")
            set("$key\\shell\\open\\command", "", command)
            // Windows 11 routes links through registered apps: a ProgID, the app's capabilities
            // naming janitor: as one of its links, and the app in RegisteredApplications. This is
            // what puts Butler in Settings › Default apps.
            set(progId, "", "Butler sign-in link")
            set(progId, "URL Protocol", "")
            set("$progId\\DefaultIcon", "", "\"$exe\",0")
            set("$progId\\shell\\open\\command", "", command)
            set("$capabilities\\URLAssociations", "janitor", "Butler.janitor")
            set(capabilities, "ApplicationName", "Butler")
            set(capabilities, "ApplicationDescription", "JanitorAI, and it respects your time.")
            set(capabilities, "ApplicationIcon", "\"$exe\",0")
            set("Software\\RegisteredApplications", "Butler", capabilities)
            // Explorer caches associations, and browsers ask it to open links: tell it.
            ShellNotify.INSTANCE.SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, null, null)
        }.onFailure { android.util.Log.w("DesktopInstance", "couldn't register janitor: links", it) }
    }

    /** Butler.exe when this is the installed app; null for a run from the build tools. */
    private fun installedExe(): String? =
        (System.getProperty("jpackage.app-path") ?: ProcessHandle.current().info().command().orElse(null))
            ?.takeIf { it.endsWith("Butler.exe", ignoreCase = true) && File(it).isFile }
}

/** shell32's SHChangeNotify, which JNA's platform bindings leave out. */
private interface ShellNotify : StdCallLibrary {
    fun SHChangeNotify(eventId: Int, flags: Int, item1: Pointer?, item2: Pointer?)

    companion object {
        val INSTANCE: ShellNotify = Native.load("shell32", ShellNotify::class.java)
    }
}
