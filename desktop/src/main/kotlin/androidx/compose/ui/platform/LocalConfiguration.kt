package androidx.compose.ui.platform

import androidx.compose.runtime.compositionLocalOf

/** Set by the window (Main.kt) from its own size, so "screen width" means the window's. */
val LocalConfiguration = compositionLocalOf { android.content.res.Configuration(1000, 800) }
