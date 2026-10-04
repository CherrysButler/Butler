package com.cherry.butler.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cherry.butler.R
import kotlin.math.roundToInt

/**
 * Two voices. Everything but prose is the phone's own sans, as in Janitor. Prose (the chat
 * transcript, descriptions) is Atkinson Hyperlegible, bundled: a humanist sans built for
 * legibility, with a true italic. It is bundled because OEM faces vary and some ship no
 * italic at all, which silently flattens every `*action*` in a roleplay. Prose reads at
 * 16/25, with the smaller roles for captions and margins.
 */
private val Reading = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.atkinson_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.atkinson_bolditalic, FontWeight.Bold, FontStyle.Italic),
)

/** The phone's own sans, as Janitor uses: screens, lists, settings, numbers. */
private val Ui = FontFamily.Default

/** The reading face, for roleplay prose and character descriptions. */
val ProseFamily: FontFamily get() = Reading

private val readableLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val ButlerTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Reading, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 25.sp, lineHeightStyle = readableLineHeight,
    ),
    bodyMedium = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, lineHeightStyle = readableLineHeight,
    ),
    bodySmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Ui, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp,
    ),
)

/** Where a plate (a small bold label or heading) sits in the hierarchy. */
enum class PlateLevel {
    /** Margin labels, counters. */
    Small,
    /** Names in rows and sheets. */
    Name,
    /** Screen heads. */
    Head,
}

/** A plate's style: the UI sans, bold, sized by its level. */
@Composable
fun plateStyle(level: PlateLevel): TextStyle {
    val size = when (level) {
        PlateLevel.Small -> 12.sp
        PlateLevel.Name -> 18.sp
        PlateLevel.Head -> 24.sp
    }
    return TextStyle(
        fontFamily = Ui,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * 1.3f,
        letterSpacing = if (level == PlateLevel.Small) 0.2.sp else 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

/** A name plate or a small heading, in the UI sans, bold. */
@Composable
fun PlateText(
    text: String,
    level: PlateLevel,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = plateStyle(level),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
