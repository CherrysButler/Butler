package com.cherry.butler.desktop

import java.awt.SystemTray
import java.awt.TrayIcon

/** Butler's tray icon, which carries Windows notifications for replies that land elsewhere. */
object DesktopTray {
    @Volatile var windowFocused: Boolean = true
    @Volatile var openChat: ((Long) -> Unit)? = null
    private var icon: TrayIcon? = null
    @Volatile private var lastChat: Long? = null

    fun install(image: java.awt.Image, onOpen: () -> Unit) {
        if (!SystemTray.isSupported() || icon != null) return
        icon = TrayIcon(image, "Butler").apply {
            isImageAutoSize = true
            addActionListener {
                onOpen()
                lastChat?.let { id -> openChat?.invoke(id) }
            }
        }
        runCatching { SystemTray.getSystemTray().add(icon) }
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
