package com.cherry.butler.core.security

import android.content.Context
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android's app lock with the same prefs and timing. Windows Hello isn't wired yet, so
 * [canLock] says no and the switch stays off on the desktop.
 */
@Singleton
class AppLock @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false) && canLock())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _graceMs = MutableStateFlow(prefs.getLong(KEY_GRACE, 60_000L))
    val graceMs: StateFlow<Long> = _graceMs.asStateFlow()

    private val _locked = MutableStateFlow(_enabled.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var leftAt = 0L
    @Volatile private var away = false

    fun wouldBeLocked(): Boolean =
        _enabled.value && (_locked.value || (away && SystemClock.elapsedRealtime() - leftAt > _graceMs.value))

    fun canLock(): Boolean = false

    fun setEnabled(on: Boolean) {
        _enabled.value = on && canLock()
        prefs.edit().putBoolean(KEY_ENABLED, _enabled.value).apply()
        if (!_enabled.value) _locked.value = false
    }

    fun setGrace(ms: Long) {
        _graceMs.value = ms
        prefs.edit().putLong(KEY_GRACE, ms).apply()
    }

    fun onLeave() {
        leftAt = SystemClock.elapsedRealtime()
        away = true
    }

    fun onReturn() {
        away = false
        if (_enabled.value && leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > _graceMs.value) _locked.value = true
    }

    fun authenticate(activity: FragmentActivity?, title: String = "Unlock Butler", onDone: (Boolean) -> Unit = {}) {
        _locked.value = false
        onDone(true)
    }

    private companion object {
        const val KEY_ENABLED = "app_lock"
        const val KEY_GRACE = "app_lock_grace_ms"
    }
}
