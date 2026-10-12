package com.cherry.butler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A screen's refresh, held where a keyboard shortcut can reach it (Ctrl+R or F5 on the
 * desktop). On the phone nothing provides one, and a pull still refreshes as before.
 *
 * Screens offer theirs as they come into view and take it back as they go; the newest one on
 * offer is the screen on top (a page pushed over another, the character panel beside a list),
 * and when it goes, the one under it answers again.
 */
class RefreshHandle {
    private val offered = mutableListOf<() -> Unit>()

    /** What the shortcut runs now: the screen most recently come into view. */
    val action: (() -> Unit)? get() = synchronized(offered) { offered.lastOrNull() }

    fun offer(action: () -> Unit) = synchronized(offered) { offered += action }

    fun withdraw(action: () -> Unit) = synchronized(offered) { offered.remove(action); Unit }
}

val LocalRefreshHandle = staticCompositionLocalOf<RefreshHandle?> { null }

/**
 * Offers [action] as this screen's refresh while it is on show: while it is composed, and, for
 * a tab (the tabs stay composed behind each other), while it is the tab selected.
 */
@Composable
fun RegisterRefresh(action: () -> Unit) {
    val handle = LocalRefreshHandle.current ?: return
    val active = com.cherry.butler.ui.navigation.LocalTabActive.current
    val latest = rememberUpdatedState(action)
    DisposableEffect(handle, active) {
        if (!active) return@DisposableEffect onDispose { }
        val registered: () -> Unit = { latest.value() }
        handle.offer(registered)
        onDispose { handle.withdraw(registered) }
    }
}

/** The Chats archive, opened and closed from a keyboard shortcut (Ctrl+H on the desktop). */
val LocalArchiveHandle = staticCompositionLocalOf<RefreshHandle?> { null }

/** Offers [action] as the screen's archive toggle while it is composed. */
@Composable
fun RegisterArchive(action: () -> Unit) {
    val handle = LocalArchiveHandle.current ?: return
    val latest = rememberUpdatedState(action)
    DisposableEffect(handle) {
        val registered: () -> Unit = { latest.value() }
        handle.offer(registered)
        onDispose { handle.withdraw(registered) }
    }
}
