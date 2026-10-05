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

/**
 * A variable font file at the usual weights. Lora and Nunito ship as one variable file per
 * style; each weight is that file at a point on its weight axis.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(upright: Int, italic: Int?): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).flatMap { w ->
        val axis = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w.weight))
        listOfNotNull(
            Font(upright, w, FontStyle.Normal, variationSettings = axis),
            italic?.let { Font(it, w, FontStyle.Italic, variationSettings = axis) },
        )
    },
)

/** A book serif with a true italic. */
val LoraFamily: FontFamily by lazy { variable(R.font.lora, R.font.lora_italic) }

/** Soft and rounded, with a true italic. */
val NunitoFamily: FontFamily by lazy { variable(R.font.nunito, R.font.nunito_italic) }

/** Built for easy reading; its italic is slanted by the system. */
val LexendFamily: FontFamily by lazy { variable(R.font.lexend, null) }

/** Casual, hand-lettered feel, with a true italic and bold. */
val ComicNeueFamily: FontFamily by lazy {
    FontFamily(
        Font(R.font.comicneue_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.comicneue_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.comicneue_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.comicneue_bolditalic, FontWeight.Bold, FontStyle.Italic),
    )
}

/** The phone's own sans, as Janitor uses: screens, lists, settings, numbers. */
private val Ui = FontFamily.Default

/** The bundled reading face, the chat font by default. */
val ReadingFamily: FontFamily get() = Reading

/** The chat font (replies, your lines, descriptions) and the app font (everything else), as chosen. */
val LocalChatFont = androidx.compose.runtime.staticCompositionLocalOf<FontFamily> { Reading }
val LocalAppFont = androidx.compose.runtime.staticCompositionLocalOf<FontFamily> { Ui }

/** The reading face in use, for roleplay prose and character descriptions. */
val ProseFamily: FontFamily
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalChatFont.current

/** How much larger the app's own text is drawn (Settings › Look › App text size); 1 = as designed. */
val LocalAppTextScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/**
 * [ButlerTypography] in the chosen fonts: [chat] for prose (bodyLarge, sized by the chat's own
 * setting), [app] for every other role, scaled by [appScale].
 */
fun butlerTypography(app: FontFamily, chat: FontFamily, appScale: Float = 1f): Typography = ButlerTypography.run {
    fun TextStyle.ui() = copy(fontFamily = app, fontSize = fontSize * appScale, lineHeight = lineHeight * appScale)
    Typography(
        displaySmall = displaySmall.ui(),
        headlineMedium = headlineMedium.ui(),
        headlineSmall = headlineSmall.ui(),
        titleLarge = titleLarge.ui(),
        titleMedium = titleMedium.ui(),
        titleSmall = titleSmall.ui(),
        bodyLarge = bodyLarge.copy(fontFamily = chat),
        bodyMedium = bodyMedium.ui(),
        bodySmall = bodySmall.ui(),
        labelLarge = labelLarge.ui(),
        labelMedium = labelMedium.ui(),
        labelSmall = labelSmall.ui(),
    )
}

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
    } * LocalAppTextScale.current
    return TextStyle(
        fontFamily = LocalAppFont.current,
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
