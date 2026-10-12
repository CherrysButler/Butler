package com.cherry.butler

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.cherry.butler.core.design.isLight
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import com.cherry.butler.core.background.OpenChatRequests
import com.cherry.butler.core.data.LastPlace
import com.cherry.butler.core.generation.SendPipeline
import com.cherry.butler.core.data.LocalLastPlace
import com.cherry.butler.core.data.ThemePrefs
import com.cherry.butler.core.data.TextLookPrefs
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.ui.AuthGate
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import com.cherry.butler.core.security.AppLock
import com.cherry.butler.ui.LockScreen
import androidx.compose.ui.semantics.clearAndSetSemantics
import javax.inject.Inject

/** Where the sign-in swap runs: not the activity's, so a rotation mid-swap doesn't cancel it. */
private val signInScope = kotlinx.coroutines.MainScope()

@AndroidEntryPoint
/** A FragmentActivity because the phone's fingerprint/screen-lock prompt (BiometricPrompt) needs one. */
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var supabase: SupabaseClient

    @Inject
    lateinit var updates: com.cherry.butler.core.update.UpdateChecker

    @Inject
    lateinit var fontPrefs: com.cherry.butler.core.data.FontPrefs
    @Inject
    lateinit var themePrefs: ThemePrefs

    @Inject
    lateinit var lastPlace: LastPlace

    @Inject
    lateinit var textLook: TextLookPrefs

    @Inject
    lateinit var openChats: OpenChatRequests

    @Inject
    lateinit var pipeline: SendPipeline

    @Inject
    lateinit var appLock: AppLock

    @Inject
    lateinit var sessionKeeper: com.cherry.butler.core.auth.SessionKeeper

    override fun onStart() {
        super.onStart()
        appLock.onReturn()
        // A session that ran out while the phone slept is renewed before anything uses it.
        sessionKeeper.onForeground()
        // Back on screen: pick up any reply the phone froze while Butler was away.
        pipeline.resumeStalled()
    }

    override fun onStop() {
        super.onStop()
        appLock.onLeave()
    }

    /** A tapped reply notification names the chat to open. */
    private fun takeOpenChat(intent: Intent?) {
        val id = intent?.getLongExtra(OpenChatRequests.EXTRA_CHAT_ID, -1L) ?: -1L
        if (id < 0) return
        intent?.removeExtra(OpenChatRequests.EXTRA_CHAT_ID)
        openChats.post(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Cold start via the OAuth redirect; not again when the activity is only being recreated.
        if (savedInstanceState == null) takeSignIn(intent)
        // With no saved state of its own, the app was stopped outright: reopen where the user was.
        lastPlace.markColdStart(savedInstanceState == null)
        if (savedInstanceState == null) takeOpenChat(intent)
        setContent {
            val theme by themePrefs.theme.collectAsState()
            val chatStyle by themePrefs.chatStyle.collectAsState()
            val thinkingLook by themePrefs.thinkingLook.collectAsState()
            val thoughtLook by themePrefs.thoughtLook.collectAsState()
            val look by textLook.look.collectAsState()
            val custom by themePrefs.custom.collectAsState()
            val chatFontKey by fontPrefs.chat.collectAsState()
            val appFontKey by fontPrefs.app.collectAsState()
            val appTextScale by fontPrefs.appScale.collectAsState()
            val fontSpacing by fontPrefs.spacing.collectAsState()
            // Keyed on the added fonts too: a face added to the family in use joins it at once.
            val addedFonts by fontPrefs.added.collectAsState()
            val chatFont = androidx.compose.runtime.remember(chatFontKey, addedFonts) { com.cherry.butler.core.design.fontFamilyOf(fontPrefs, chatFontKey) }
            val appFont = androidx.compose.runtime.remember(appFontKey, addedFonts) { com.cherry.butler.core.design.fontFamilyOf(fontPrefs, appFontKey) }
            // Status and nav bar icons follow the picked look, not the phone's dark mode.
            val light = theme.isLight(custom)
            LaunchedEffect(light) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (light) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val lockOn by appLock.enabled.collectAsState()
            val locked by appLock.locked.collectAsState()
            // With the lock on, Recents shows a blank card instead of the last screen (Android 13+);
            // screenshots inside Butler still work.
            LaunchedEffect(lockOn) {
                if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!lockOn)
            }
            ButlerTheme(theme = theme, chatStyle = chatStyle, look = look, custom = custom, appFont = appFont, chatFont = chatFont, appTextScale = appTextScale, appLetterSpacing = fontSpacing[appFontKey] ?: 0, chatLetterSpacing = fontSpacing[chatFontKey] ?: 0, thinkingLook = thinkingLook, thoughtLook = thoughtLook) {
                androidx.compose.foundation.layout.Box {
                    // Kept composed under the lock so the user returns exactly where they were,
                    // but hidden from accessibility while covered.
                    androidx.compose.foundation.layout.Box(
                        modifier = if (locked) androidx.compose.ui.Modifier.clearAndSetSemantics { } else androidx.compose.ui.Modifier,
                    ) {
                        CompositionLocalProvider(LocalLastPlace provides lastPlace) {
                            AuthGate()
                        }
                    }
                    if (locked) LockScreen(appLock)
                    // The automatic update check (off unless switched on); never over the lock.
                    else com.cherry.butler.feature.settings.UpdatePrompt(updates)
                }
            }
        }
    }

    /**
     * The OAuth redirect (`janitor://auth/...?code=`): its code swapped for a session, once.
     * Done here rather than by supabase-kt's handleDeeplinks, whose own job let a failed swap
     * take the app down ("both auth code and code verifier should be non-empty", when a
     * redirect came round again with its verifier already used). The link is taken off the
     * intent so nothing replays it; a failure says so instead of crashing.
     */
    private fun takeSignIn(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != com.cherry.butler.core.config.JanitorConfig.OAUTH_SCHEME ||
            data.host != com.cherry.butler.core.config.JanitorConfig.OAUTH_HOST
        ) return
        intent.data = null
        val code = data.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return
        signInScope.launch {
            try {
                supabase.auth.exchangeCodeForSession(code)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.cherry.butler.core.diagnostics.Diagnostics.record("auth", "sign-in exchange failed: ${e.javaClass.simpleName}")
                android.widget.Toast.makeText(applicationContext, "Sign-in didn't finish. Please try again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start: the janitor://auth/google redirect returning from the browser.
        takeSignIn(intent)
        takeOpenChat(intent)
    }
}
