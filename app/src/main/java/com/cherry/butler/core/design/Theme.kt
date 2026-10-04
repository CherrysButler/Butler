package com.cherry.butler.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** The looks a user can pick in Settings. */
enum class AppTheme(val key: String, val label: String, val blurb: String) {
    JanitorClassic("janitor_classic", "Janitor Classic", "Slate and soft cards, close to Janitor"),
    LightsOut("lights_out", "Lights out", "True black with hairline frames"),
    Daylight("daylight", "Daylight", "Light ground and white cards"),
}

internal val AppTheme.tones: Tones
    get() = when (this) {
        AppTheme.JanitorClassic -> Palette.Classic
        AppTheme.LightsOut -> Palette.LightsOut
        AppTheme.Daylight -> Palette.Daylight
    }

/** Whether the look is light, for system bar icons. */
val AppTheme.isLight: Boolean get() = tones.isLight

private fun scheme(t: Tones): ColorScheme = if (t.isLight) lightColorScheme(
    primary = t.red,
    onPrimary = t.onRed,
    primaryContainer = t.redDim,
    onPrimaryContainer = t.onRedDim,
    secondary = t.textHigh,
    onSecondary = t.ground,
    tertiary = t.thought,
    onTertiary = t.ground,
    background = t.ground,
    onBackground = t.textHigh,
    surface = t.ground,
    onSurface = t.textHigh,
    surfaceVariant = t.surfaceHigh,
    onSurfaceVariant = t.textMed,
    surfaceContainerLowest = t.chrome,
    surfaceContainerLow = t.surfaceElevated,
    surfaceContainer = t.surfaceElevated,
    surfaceContainerHigh = t.surfaceHigh,
    surfaceContainerHighest = t.surfaceHigh,
    outline = t.rule,
    outlineVariant = t.ruleFaint,
    error = t.danger,
    onError = t.onRed,
) else darkColorScheme(
    primary = t.red,
    onPrimary = t.onRed,
    primaryContainer = t.redDim,
    onPrimaryContainer = t.onRedDim,
    secondary = t.textHigh,
    onSecondary = t.ground,
    tertiary = t.thought,
    onTertiary = t.ground,
    background = t.ground,
    onBackground = t.textHigh,
    surface = t.ground,
    onSurface = t.textHigh,
    surfaceVariant = t.surfaceHigh,
    onSurfaceVariant = t.textMed,
    surfaceContainerLowest = t.chrome,
    surfaceContainerLow = t.surfaceElevated,
    surfaceContainer = t.surfaceElevated,
    surfaceContainerHigh = t.surfaceHigh,
    surfaceContainerHighest = t.surfaceHigh,
    outline = t.rule,
    outlineVariant = t.ruleFaint,
    error = t.danger,
    onError = t.onRed,
)

private val Schemes = AppTheme.entries.associateWith { scheme(it.tones) }
private val Extended = AppTheme.entries.associateWith { extendedColors(it.tones) }

/** The picked look decides the colours, extended colours and corner shapes. */
/** How chat lines are laid out (a Settings choice). */
enum class ChatStyle(val key: String, val label: String, val blurb: String) {
    Story("story", "Story", "Replies run as prose; your lines sit in an outlined box"),
    Bubbles("bubbles", "Bubbles", "Your lines as filled chat bubbles"),
    Janitor("janitor", "Janitor", "Avatar and name beside every line, as in Janitor"),
}

val LocalChatStyle = androidx.compose.runtime.staticCompositionLocalOf { ChatStyle.Story }

@Composable
fun ButlerTheme(
    theme: AppTheme = AppTheme.JanitorClassic,
    chatStyle: ChatStyle = ChatStyle.Story,
    look: RpLook = RpLook(),
    content: @Composable () -> Unit,
) {
    val colorScheme = Schemes.getValue(theme)
    val extended = Extended.getValue(theme)
    val shapes = if (theme == AppTheme.LightsOut) LightsOutShapes else ButlerShapes

    CompositionLocalProvider(LocalButlerExtendedColors provides extended, LocalChatStyle provides chatStyle, LocalRpLook provides look) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ButlerTypography,
            shapes = shapes,
            content = content,
        )
    }
}

/** Accessor for Butler's non-Material tokens: `ButlerTheme.colors.speech`, etc. */
object ButlerTheme {
    val colors: ButlerExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalButlerExtendedColors.current
}
