package com.cherry.butler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A screen's refresh, held where a keyboard shortcut can reach it (Ctrl+R or F5 on the
 * desktop). On the phone nothing provides one, and a pull still refreshes as before.
 */
class RefreshHandle {
    var action: (() -> Unit)? = null
}

val LocalRefreshHandle = staticCompositionLocalOf<RefreshHandle?> { null }

/** Offers [action] as this screen's refresh while it is composed. */
@Composable
fun RegisterRefresh(action: () -> Unit) {
    val handle = LocalRefreshHandle.current ?: return
    val latest = rememberUpdatedState(action)
    DisposableEffect(handle) {
        val registered: () -> Unit = { latest.value() }
        handle.action = registered
        onDispose { if (handle.action === registered) handle.action = null }
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
        handle.action = registered
        onDispose { if (handle.action === registered) handle.action = null }
    }
}
