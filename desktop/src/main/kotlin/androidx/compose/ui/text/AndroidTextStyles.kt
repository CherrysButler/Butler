package androidx.compose.ui.text

/** Android's includeFontPadding switch; desktop text has no font padding to turn off. */
fun PlatformTextStyle(includeFontPadding: Boolean): PlatformTextStyle = PlatformTextStyle(null, null)
