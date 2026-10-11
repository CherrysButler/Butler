package com.cherry.butler.desktop

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Butler's icon in Windows' tray. Closing the window puts Butler here instead of quitting,
 * so a reply being written still finishes; a click on the icon brings the window back, and
 * its menu quits for real. It also carries the notifications for replies that land while
 * the window isn't the one in use; clicking one opens that chat.
 */
object DesktopTray {
    @Volatile var windowFocused: Boolean = true
    @Volatile var openChat: ((Long) -> Unit)? = null
    private var icon: TrayIcon? = null
    @Volatile private var lastChat: Long? = null

    private const val HINT_KEY = "tray_hint_shown"

    fun install(image: java.awt.Image, onOpen: () -> Unit, onQuit: () -> Unit) {
        if (!SystemTray.isSupported() || icon != null) return
        val menu = PopupMenu().apply {
            add(MenuItem("Open Butler").apply { addActionListener { onOpen() } })
            addSeparator()
            add(MenuItem("Quit Butler").apply { addActionListener { onQuit() } })
        }
        icon = TrayIcon(image, "Butler", menu).apply {
            isImageAutoSize = true
            // A notification clicked (or the icon double-clicked): the window, at the chat it named.
            addActionListener {
                onOpen()
                lastChat?.let { id -> openChat?.invoke(id) }
                lastChat = null
            }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) onOpen()
                }
            })
        }
        runCatching { SystemTray.getSystemTray().add(icon) }
        Runtime.getRuntime().addShutdownHook(Thread { remove() })
    }

    /** Whether there is a tray to close into; without one the window's X quits as before. */
    val available: Boolean get() = icon != null

    /** The window just went to the tray: said once, the first time, so it isn't taken for a quit. */
    fun hidden() {
        val prefs = DesktopPrefs.open("butler_desktop")
        if (prefs.getBoolean(HINT_KEY, false)) return
        prefs.edit().putBoolean(HINT_KEY, true).apply()
        icon?.displayMessage(
            "Butler is still here",
            "Replies keep going while the window is closed. Click the icon to open it; right-click to quit.",
            TrayIcon.MessageType.NONE,
        )
    }

    fun notify(chatId: Long, title: String, text: String) {
        if (windowFocused) return
        lastChat = chatId
        icon?.displayMessage(title, text, TrayIcon.MessageType.NONE)
    }

    fun remove() {
        icon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        icon = null
    }
}
