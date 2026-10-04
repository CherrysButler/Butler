package com.cherry.butler.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The e-mail part of the login screen: what was typed and which step it is on. */
data class EmailSignIn(
    val email: String = "",
    val password: String = "",
    val code: String = "",
    /** A code was e-mailed; the code field shows. */
    val codeSent: Boolean = false,
) {
    val emailLooksRight: Boolean get() = email.contains('@') && email.substringAfter('@').contains('.')
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val keeper: com.cherry.butler.core.auth.SessionKeeper,
) : ViewModel() {

    /** The session was lost rather than signed out of. */
    val sessionEnded: StateFlow<Boolean> = keeper.ended

    /** Source of truth for routing between login and the app. */
    val sessionStatus: StateFlow<SessionStatus> = authRepository.sessionStatus

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _email = MutableStateFlow(EmailSignIn())
    val email: StateFlow<EmailSignIn> = _email.asStateFlow()

    /** True while the Turnstile sheet is up; the step that needs its token waits in [pending]. */
    private val _challenge = MutableStateFlow(false)
    val challenge: StateFlow<Boolean> = _challenge.asStateFlow()
    private var pending: (suspend (String) -> Unit)? = null

    fun signInWithGoogle() = run { authRepository.signInWithGoogle() }

    fun signInWithDiscord() = run { authRepository.signInWithDiscord() }

    fun onEmail(text: String) = _email.update { it.copy(email = text.trim()) }.also { _error.value = null }
    fun onPassword(text: String) = _email.update { it.copy(password = text) }.also { _error.value = null }
    fun onCode(text: String) = _email.update { it.copy(code = text.filter { c -> c.isDigit() }.take(8)) }.also { _error.value = null }

    /** Password sign-in. Turnstile first; the token goes out with the credentials. */
    fun signInWithEmail() {
        val s = _email.value
        if (!s.emailLooksRight) { _error.value = "That doesn't look like an e-mail address."; return }
        if (s.password.isEmpty()) { _error.value = "Enter your password, or ask for a code instead."; return }
        challenge { token -> authRepository.signInWithEmail(s.email, s.password, token) }
    }

    /** Asks for a one-time code by e-mail. Turnstile first. */
    fun requestEmailCode() {
        val s = _email.value
        if (!s.emailLooksRight) { _error.value = "That doesn't look like an e-mail address."; return }
        challenge { token ->
            authRepository.requestEmailCode(s.email, token)
            _email.update { it.copy(codeSent = true, code = "") }
        }
    }

    /** The code from the e-mail; no Turnstile on this step. */
    fun verifyEmailCode() {
        val s = _email.value
        if (s.code.length < 6) { _error.value = "The code is six digits."; return }
        run { authRepository.verifyEmailCode(s.email, s.code) }
    }

    /** Back from the code step to the password step. */
    fun usePassword() = _email.update { it.copy(codeSent = false, code = "") }.also { _error.value = null }

    private fun challenge(action: suspend (String) -> Unit) {
        _error.value = null
        pending = action
        _challenge.value = true
    }

    /** Turnstile answered: the waiting step runs with its token. */
    fun onCaptcha(token: String) {
        _challenge.value = false
        val action = pending ?: return
        pending = null
        run { action(token) }
    }

    fun cancelCaptcha() {
        _challenge.value = false
        pending = null
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _signingIn.value = true
            _error.value = null
            keeper.acknowledgeEnded()
            runCatching { block() }.onFailure { _error.value = it.userWords() }
            _signingIn.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}

/** GoTrue's error codes, in words. Anything unknown keeps its own message. */
private fun Throwable.userWords(): String {
    val text = (this as? RestException)?.error ?: message ?: return "Sign-in failed."
    return when {
        "invalid_credentials" in text || "Invalid login credentials" in text -> "Wrong e-mail or password."
        "email_not_confirmed" in text -> "This e-mail isn't confirmed yet. Check your inbox for Janitor's confirmation first."
        "otp_expired" in text || "Token has expired" in text -> "That code has expired. Ask for a new one."
        "otp_disabled" in text || "Signups not allowed for otp" in text -> "No Janitor account uses this e-mail."
        "over_email_send_rate_limit" in text || "rate limit" in text.lowercase() -> "Too many e-mails in a row. Give it a minute."
        "captcha_failed" in text -> "The Cloudflare check didn't pass. Try again."
        "invalid" in text && "token" in text.lowercase() -> "That code isn't right."
        else -> text.take(160)
    }
}
