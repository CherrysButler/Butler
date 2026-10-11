package com.cherry.butler.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The phone's toasts, shown at the foot of the window for a moment. */
object DesktopToasts {
    data class Shown(val text: String, val at: Long = System.nanoTime())
    private val _current = MutableStateFlow<Shown?>(null)
    val current: StateFlow<Shown?> = _current
    fun show(text: String) { _current.value = Shown(text) }
    fun clear(shown: Shown) { if (_current.value == shown) _current.value = null }
}
