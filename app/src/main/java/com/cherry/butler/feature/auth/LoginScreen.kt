package com.cherry.butler.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cherry.butler.R
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.feature.settings.FieldBlock
import com.cherry.butler.ui.components.HairlineRule

/**
 * The door. Google and Discord go out through the browser and come back by deep link;
 * e-mail stays here, with a password or a code, after Cloudflare's check in a sheet.
 */
@Composable
fun LoginScreen(
    signingIn: Boolean,
    error: String?,
    /** Why the user is here when they didn't sign out. */
    notice: String? = null,
    email: EmailSignIn = EmailSignIn(),
    challenge: Boolean = false,
    onContinueWithGoogle: () -> Unit,
    onContinueWithDiscord: () -> Unit = {},
    onEmail: (String) -> Unit = {},
    onPassword: (String) -> Unit = {},
    onCode: (String) -> Unit = {},
    onSignInWithEmail: () -> Unit = {},
    onRequestCode: () -> Unit = {},
    onVerifyCode: () -> Unit = {},
    onUsePassword: () -> Unit = {},
    onCaptcha: (String) -> Unit = {},
    onCancelCaptcha: () -> Unit = {},
    onDismissError: () -> Unit,
) {
    if (challenge) TurnstileSheet(onToken = onCaptcha, onDismiss = onCancelCaptcha)
    var emailOpen by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                // 8 here plus 16 on each block below = the 24 edge; the e-mail fields bring
                // their own 16 (FieldBlock), so they line up with the keys without arithmetic.
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val edge = Modifier.padding(horizontal = 16.dp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))
            Surface(shape = MaterialTheme.shapes.large, color = ButlerTheme.colors.surfaceHigh, modifier = Modifier.size(84.dp)) {
                Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Butler", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "JanitorAI, and it respects your time.",
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textMed,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))

            if (notice != null) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = edge
                        .fillMaxWidth()
                        .background(ButlerTheme.colors.surfaceHigh, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Spacer(Modifier.height(16.dp))
            }

            DoorKey(label = "Continue with Google", busy = signingIn && !emailOpen, enabled = !signingIn, primary = true, onClick = onContinueWithGoogle, modifier = edge)
            Spacer(Modifier.height(10.dp))
            DoorKey(label = "Continue with Discord", busy = false, enabled = !signingIn, primary = false, onClick = onContinueWithDiscord, modifier = edge)

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = edge) {
                HairlineRule(modifier = Modifier.weight(1f), color = ButlerTheme.colors.rule)
                Text("or", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow, modifier = Modifier.padding(horizontal = 12.dp))
                HairlineRule(modifier = Modifier.weight(1f), color = ButlerTheme.colors.rule)
            }
            Spacer(Modifier.height(14.dp))

            if (!emailOpen) {
                Text(
                    text = "Sign in with e-mail",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = !signingIn) { emailOpen = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                EmailDoor(
                    state = email,
                    edge = edge,
                    busy = signingIn,
                    onEmail = onEmail,
                    onPassword = onPassword,
                    onCode = onCode,
                    onSignIn = onSignInWithEmail,
                    onRequestCode = onRequestCode,
                    onVerify = onVerifyCode,
                    onUsePassword = onUsePassword,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = edge.fillMaxWidth().clickable(onClick = onDismissError),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** E-mail and password, or a code: the same two or three fields, as the step asks. */
@Composable
private fun EmailDoor(
    state: EmailSignIn,
    edge: Modifier,
    busy: Boolean,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onCode: (String) -> Unit,
    onSignIn: () -> Unit,
    onRequestCode: () -> Unit,
    onVerify: () -> Unit,
    onUsePassword: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // FieldBlock brings its own 16 on each side, the same as [edge].
        FieldBlock(label = "E-mail", value = state.email, onChange = onEmail, placeholder = "you@example.com", keyboardType = KeyboardType.Email)
        if (!state.codeSent) {
            FieldBlock(label = "Password", value = state.password, onChange = onPassword, placeholder = "Your Janitor password", secret = true)
        } else {
            FieldBlock(label = "Code from the e-mail", value = state.code, onChange = onCode, placeholder = "6 digits", keyboardType = KeyboardType.Number)
        }
        Spacer(Modifier.height(6.dp))
        if (!state.codeSent) {
            DoorKey(label = "Sign in", busy = busy, enabled = !busy, primary = true, onClick = onSignIn, modifier = edge)
            Spacer(Modifier.height(8.dp))
            QuietLink(text = "E-mail me a code instead", enabled = !busy, onClick = onRequestCode)
        } else {
            Text(
                text = "Janitor sent a code to ${state.email}.",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textMed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            DoorKey(label = "Verify", busy = busy, enabled = !busy, primary = true, onClick = onVerify, modifier = edge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuietLink(text = "Send it again", enabled = !busy, onClick = onRequestCode)
                QuietLink(text = "Use a password", enabled = !busy, onClick = onUsePassword)
            }
        }
    }
}

@Composable
private fun DoorKey(label: String, busy: Boolean, enabled: Boolean, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else ButlerTheme.colors.surfaceHigh,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else ButlerTheme.colors.surfaceHigh,
            disabledContentColor = if (primary) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuietLink(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
