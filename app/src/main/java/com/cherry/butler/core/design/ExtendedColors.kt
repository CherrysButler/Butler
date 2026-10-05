package com.cherry.butler.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand colours that don't map cleanly onto a Material [androidx.compose.material3.ColorScheme]
 * slot — the roleplay-markup tints and the hairline rules the world is built from.
 * Provided through [LocalButlerExtendedColors] so composables read them theme-aware via
 * `ButlerTheme.colors`.
 */
@Immutable
data class ButlerExtendedColors(
    val speech: Color,   // "quoted speech"
    val thought: Color,  // inner thought
    val success: Color,
    val warn: Color,
    val danger: Color,
    /** The hairline that frames rows, plates and windows. */
    val rule: Color,
    /** A quieter hairline, for the rule between two rows of the same list. */
    val outlineFaint: Color,
    // surface ramp shortcuts used by custom components
    val surfaceElevated: Color,
    val surfaceHigh: Color,
    val textMed: Color,
    val textLow: Color,
    /** The nav bar and the dark frame around the rounded ground. */
    val chrome: Color,
    /** Text on a red-tinted chip (`primaryContainer`). */
    val onAccentSoft: Color,
    /** The hairline drawn around every card and along the nav bar's top edge. */
    val cardOutline: Color,
    /** Highlights' romantic and erotic washes. */
    val romance: Color,
    val desire: Color,
)

internal fun extendedColors(t: Tones) = ButlerExtendedColors(
    speech = t.speech,
    thought = t.thought,
    success = t.success,
    warn = t.warn,
    danger = t.danger,
    rule = t.rule,
    outlineFaint = t.ruleFaint,
    surfaceElevated = t.surfaceElevated,
    surfaceHigh = t.surfaceHigh,
    textMed = t.textMed,
    textLow = t.textLow,
    chrome = t.chrome,
    onAccentSoft = t.onRedDim,
    cardOutline = t.cardOutline,
    romance = t.romance,
    desire = t.desire,
)

internal val LocalButlerExtendedColors = staticCompositionLocalOf { extendedColors(Palette.Classic) }
