package com.cherry.butler.core.design

import androidx.compose.ui.graphics.Color

/**
 * One look's raw tones. Butler ships three: [Palette.Classic], close to Janitor's own app;
 * [Palette.LightsOut], true black with hairline frames; and [Palette.Daylight], the light
 * one. These are the ONLY hard-coded colours in the app; everything else goes through
 * [ButlerExtendedColors] or the Material scheme.
 */
internal data class Tones(
    val isLight: Boolean,
    val ground: Color,
    /** The nav bar and the frame the rounded ground sits in. */
    val chrome: Color,
    val surfaceElevated: Color,
    val surfaceHigh: Color,
    val rule: Color,
    val ruleFaint: Color,
    val textHigh: Color,
    val textMed: Color,
    val textLow: Color,
    val red: Color,
    /** A tinted chip's fill. */
    val redDim: Color,
    /** Text on a tinted chip. */
    val onRedDim: Color,
    val onRed: Color,
    val danger: Color,
    // RP markup tints.
    val speech: Color,
    val thought: Color,
    val success: Color,
    val warn: Color,
    /** Drawn around every card: a hairline so a surface has an edge, not only a tone. */
    val cardOutline: Color,
)

internal object Palette {

    /** Night slate on a black nav bar, lifted cards with a hairline edge, Butler red where Janitor uses purple. */
    val Classic = Tones(
        isLight = false,
        ground = Color(0xFF2B2E33),
        chrome = Color(0xFF17181B),
        surfaceElevated = Color(0xFF363A40),
        surfaceHigh = Color(0xFF43474E),
        rule = Color(0xFF50555C),
        ruleFaint = Color(0xFF3E4248),
        textHigh = Color(0xFFF3F3F1),
        textMed = Color(0xFFC3C6CB),
        textLow = Color(0xFF90949B),
        red = Color(0xFFEA5A4F),
        redDim = Color(0xFF4C3538),
        onRedDim = Color(0xFFF7B9B2),
        onRed = Color(0xFFFFFFFF),
        danger = Color(0xFFF06B60),
        speech = Color(0xFF9CC3F0),
        thought = Color(0xFFC3A8EE),
        success = Color(0xFF6FCF97),
        warn = Color(0xFFF2B35E),
        cardOutline = Color(0xFF555A63),
    )

    /** True black, one warm ink, one hard red; surfaces are framed by hairlines, not lifted. */
    val LightsOut = Tones(
        isLight = false,
        ground = Color(0xFF000000),
        chrome = Color(0xFF000000),
        surfaceElevated = Color(0xFF0A0A0A),
        surfaceHigh = Color(0xFF1C1C1C),
        rule = Color(0xFF4A4A48),
        ruleFaint = Color(0xFF2A2A29),
        textHigh = Color(0xFFF2EFE6),
        textMed = Color(0xFFBDBAB2),
        textLow = Color(0xFF8A877F),
        red = Color(0xFFE0453A),
        redDim = Color(0xFF3A100C),
        onRedDim = Color(0xFFF2B4AC),
        onRed = Color(0xFFF2EFE6),
        danger = Color(0xFFE0453A),
        speech = Color(0xFF9CC3F0),
        thought = Color(0xFFC3A8EE),
        success = Color(0xFF6FCF97),
        warn = Color(0xFFF2B35E),
        cardOutline = Color(0xFF3A3A38),
    )

    /** Light: warm-grey ground, white cards, ink text; the same red, deepened to read on white. */
    val Daylight = Tones(
        isLight = true,
        ground = Color(0xFFF2F1EE),
        chrome = Color(0xFFE3E2DE),
        surfaceElevated = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFE7E6E2),
        rule = Color(0xFFC9C8C3),
        ruleFaint = Color(0xFFE2E1DC),
        textHigh = Color(0xFF17181B),
        textMed = Color(0xFF4A4D53),
        textLow = Color(0xFF7D8087),
        red = Color(0xFFCF3A30),
        redDim = Color(0xFFF7DCD8),
        onRedDim = Color(0xFF9A2118),
        onRed = Color(0xFFFFFFFF),
        danger = Color(0xFFC0281E),
        speech = Color(0xFF2B63B5),
        thought = Color(0xFF6A47AC),
        success = Color(0xFF2E8E62),
        warn = Color(0xFFA86C0C),
        cardOutline = Color(0xFFC9C8C3),
    )
}
