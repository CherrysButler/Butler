package com.cherry.butler.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * The phone's Back, on the desktop: Esc and the mouse's back button. The newest enabled
 * handler gets it, like Android's back dispatcher; with none, the window's navigation pops.
 */
object DesktopBack {
    private class Entry(var enabled: Boolean, var onBack: () -> Unit)
    private val stack = mutableListOf<Entry>()
    var fallback: (() -> Boolean)? = null

    /** True when something took the back press. */
    fun press(): Boolean {
        val top = synchronized(stack) { stack.lastOrNull { it.enabled } }
        if (top != null) { top.onBack(); return true }
        return fallback?.invoke() ?: false
    }

    @Composable
    fun Handler(enabled: Boolean, onBack: () -> Unit) {
        val current = rememberUpdatedState(onBack)
        val entry = androidx.compose.runtime.remember { Entry(enabled) { current.value() } }
        entry.enabled = enabled
        DisposableEffect(entry) {
            synchronized(stack) { stack += entry }
            onDispose { synchronized(stack) { stack -= entry } }
        }
    }
}
