package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Android's LocalContext; on the desktop it is always the one DesktopContext. */
val LocalContext = staticCompositionLocalOf<android.content.Context> { com.cherry.butler.desktop.DesktopContext }
