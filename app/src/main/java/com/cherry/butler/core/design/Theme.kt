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
    Custom("custom", "Custom", "Your accent and background"),
}

/** The look's tones; [custom] is what the Custom look is made from, ignored by the others. */
internal fun AppTheme.tones(custom: CustomColors = CustomColors.Default): Tones = when (this) {
    AppTheme.JanitorClassic -> Palette.Classic
    AppTheme.LightsOut -> Palette.LightsOut
    AppTheme.Daylight -> Palette.Daylight
    AppTheme.Custom -> customTones(custom)
}

internal val AppTheme.tones: Tones get() = tones()

/** Whether the look is light, for system bar icons. */
fun AppTheme.isLight(custom: CustomColors = CustomColors.Default): Boolean = tones(custom).isLight

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
    custom: CustomColors = CustomColors.Default,
    content: @Composable () -> Unit,
) {
    // The shipped looks are built once; the Custom one whenever its two colours change.
    val customTones = androidx.compose.runtime.remember(custom) { customTones(custom) }
    val colorScheme = if (theme == AppTheme.Custom) androidx.compose.runtime.remember(customTones) { scheme(customTones) } else Schemes.getValue(theme)
    val extended = if (theme == AppTheme.Custom) androidx.compose.runtime.remember(customTones) { extendedColors(customTones) } else Extended.getValue(theme)
    val shapes = when (theme) {
        AppTheme.LightsOut -> LightsOutShapes
        AppTheme.Custom -> androidx.compose.runtime.remember(custom.corners) { scaledShapes(custom.corners) }
        else -> ButlerShapes
    }

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
