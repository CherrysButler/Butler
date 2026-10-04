package com.cherry.butler.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.feature.auth.AuthViewModel
import com.cherry.butler.feature.auth.LoginScreen
import io.github.jan.supabase.auth.status.SessionStatus

/**
 * Top-level router: watches the Supabase session and shows the login screen, a
 * brief loading state while the stored session is restored, or the app itself.
 */
@Composable
fun AuthGate(viewModel: AuthViewModel = hiltViewModel()) {
    val status by viewModel.sessionStatus.collectAsStateWithLifecycle()

    when (status) {
        is SessionStatus.Authenticated -> ButlerRoot()
        // A refresh that failed for want of network still has a session behind it; the app
        // carries on with it (SessionKeeper) instead of throwing the user out.
        is SessionStatus.RefreshFailure -> ButlerRoot()
        is SessionStatus.Initializing -> LoadingScreen()
        else -> {
            val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            val ended by viewModel.sessionEnded.collectAsStateWithLifecycle()
            val email by viewModel.email.collectAsStateWithLifecycle()
            val challenge by viewModel.challenge.collectAsStateWithLifecycle()
            LoginScreen(
                signingIn = signingIn,
                error = error,
                notice = if (ended) "Janitor ended your session. Sign in again to carry on; your chats are where you left them." else null,
                email = email,
                challenge = challenge,
                onContinueWithGoogle = viewModel::signInWithGoogle,
                onContinueWithDiscord = viewModel::signInWithDiscord,
                onEmail = viewModel::onEmail,
                onPassword = viewModel::onPassword,
                onCode = viewModel::onCode,
                onSignInWithEmail = viewModel::signInWithEmail,
                onRequestCode = viewModel::requestEmailCode,
                onVerifyCode = viewModel::verifyEmailCode,
                onUsePassword = viewModel::usePassword,
                onCaptcha = viewModel::onCaptcha,
                onCancelCaptcha = viewModel::cancelCaptcha,
                onDismissError = viewModel::dismissError,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // The stored session is read from disk in a few milliseconds; a spinner here would
        // only ever flash. The bare ground keeps the hand-off from the splash seamless.
        Box(modifier = Modifier.fillMaxSize())
    }
}
