package com.cherry.butler.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cherry.butler.core.config.JanitorConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Discord
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for authentication. Wraps supabase-kt's Auth so the rest
 * of the app never touches the SDK directly. Token refresh + persistence are
 * handled by the Auth plugin over [SecureSessionStore]; this exposes intent.
 *
 * OAuth (Google, Discord), e-mail with a password or a one-time code. Passkeys are off on
 * Janitor's project; Apple needs its own developer account.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val keeper: SessionKeeper,
) {
    private val auth get() = supabase.auth

    /** Reactive auth state the UI observes to route between login and the app. */
    val sessionStatus: StateFlow<SessionStatus> get() = auth.sessionStatus

    val currentSession: UserSession? get() = auth.currentSessionOrNull()

    /** Kept through a failing refresh, refreshed when about to run out (see [SessionKeeper]). */
    val currentAccessToken: String? get() = keeper.tokenForRequest()

    /**
     * Launches Google OAuth in a Custom Tab. The redirect returns through
     * [JanitorConfig.OAUTH_REDIRECT] — read the comment there before changing it; the
     * URL is constrained by Janitor's Supabase allow-list, not by our preference.
     * MainActivity hands the deep link back to the SDK, the session lands through
     * [SecureSessionStore], and [sessionStatus] flips to Authenticated.
     */
    suspend fun signInWithGoogle() = signInWith(Google)

    /** Discord OAuth — the provider whose redirect the official app itself uses. */
    suspend fun signInWithDiscord() = signInWith(Discord)

    /**
     * The sign-in page opens on whichever of Janitor's two sign-in hosts answers now ([AuthHosts]).
     * On the one the client was built with, supabase-kt opens it as always (a Custom Tab on the
     * phone); on the other, Butler builds the same link (PKCE kept by [Auth.getOAuthUrl]) and
     * opens it in the browser itself. Either way the code comes back to the same project.
     */
    private suspend fun signInWith(provider: OAuthProvider) {
        val host = AuthHosts.reachable()
        if (host == AuthHosts.configured) {
            auth.signInWith(provider, redirectUrl = JanitorConfig.OAUTH_REDIRECT)
        } else {
            val url = AuthHosts.onHost(auth.getOAuthUrl(provider, JanitorConfig.OAUTH_REDIRECT), host)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * E-mail and password. Janitor's GoTrue refuses this without a Cloudflare Turnstile
     * token (`captcha_failed`, verified 2026-10-04), so [captchaToken] comes from the
     * Turnstile widget the login screen shows first.
     */
    suspend fun signInWithEmail(email: String, password: String, captchaToken: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
            this.captchaToken = captchaToken
        }
    }

    /** Asks GoTrue to e-mail a one-time code. Needs a Turnstile token as well; never creates an account. */
    suspend fun requestEmailCode(email: String, captchaToken: String) {
        auth.signInWith(OTP) {
            this.email = email
            this.captchaToken = captchaToken
            createUser = false
        }
    }

    /** The code from the e-mail. No captcha on this step. */
    suspend fun verifyEmailCode(email: String, code: String) {
        auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)
    }

    suspend fun signOut() {
        keeper.markSigningOut()
        auth.signOut()
        com.cherry.butler.core.network.JanitorCookies.clear()
    }
}
