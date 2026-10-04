package com.cherry.butler.core.security

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Butler behind the phone's own fingerprint, face or screen lock. Locks when Butler starts
 * and when it comes back after more than [graceMs] away; nothing is stored but the switch
 * and the grace. The phone does the checking (BiometricPrompt), Butler never sees a secret.
 */
@Singleton
class AppLock @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _graceMs = MutableStateFlow(prefs.getLong(KEY_GRACE, 60_000L))
    val graceMs: StateFlow<Long> = _graceMs.asStateFlow()

    /** Starts locked if the lock is on: a fresh process is a fresh visit. */
    private val _locked = MutableStateFlow(_enabled.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var leftAt = 0L
    @Volatile private var away = false

    /**
     * Whether Butler counts as locked right now, for what shows outside it (the notification
     * shade): locked, or away longer than the grace. [locked] itself only flips on return.
     */
    fun wouldBeLocked(): Boolean =
        _enabled.value && (_locked.value || (away && SystemClock.elapsedRealtime() - leftAt > _graceMs.value))

    /** Whether this phone has anything to unlock with. Without a screen lock there's nothing to check. */
    fun canLock(): Boolean = BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) _locked.value = false
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

    /** Asks the phone. [onDone] gets true once the user is through. */
    fun authenticate(activity: FragmentActivity, title: String = "Unlock Butler", onDone: (Boolean) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _locked.value = false
                    onDone(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onDone(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setConfirmationRequired(false)
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(authenticators) else @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)
            }
            .build()
        prompt.authenticate(info)
    }

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    private companion object {
        const val KEY_ENABLED = "app_lock"
        const val KEY_GRACE = "app_lock_grace_ms"
    }
}
