package com.cherry.butler.desktop

import com.cherry.butler.ui.components.RefreshHandle

/** Window-wide shortcuts that reach into whichever screen is showing. */
object DesktopKeys {
    /** The Chats list's refresh, registered by the list itself. */
    val chats = RefreshHandle()

    /** Set by the shell: refreshes the screen on show, if it can. True when something refreshed. */
    @Volatile var refresh: (() -> Boolean)? = null
}
