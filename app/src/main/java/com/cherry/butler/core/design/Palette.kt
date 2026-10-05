package com.cherry.butler.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

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

/**
 * What a Custom look is made from: two colours (ARGB), any of the derived colours set by
 * hand ([overrides], keyed by [CustomToken.key]), and how round corners are ([corners]:
 * 0 square, 1 Butler's own, 2 round).
 */
data class CustomColors(
    val accent: Long,
    val ground: Long,
    val overrides: Map<String, Long> = emptyMap(),
    val corners: Float = 1f,
) {
    companion object {
        val Default = CustomColors(accent = 0xFFEA5A4F, ground = 0xFF2B2E33)
    }
}

/**
 * Every colour of a look that Advanced lets the user set by hand. Each reads and writes one
 * [Tones] field; [behind] is what it is usually drawn on, for the "hard to read" warning
 * (null where that question does not apply).
 */
enum class CustomToken(
    val key: String,
    val label: String,
    val hint: String,
    val group: String,
    internal val get: (Tones) -> Color,
    internal val set: (Tones, Color) -> Tones,
    internal val behind: ((Tones) -> Color)? = null,
) {
    Chrome("chrome", "Nav bar", "The bottom bar and the frame around the screen", "Surfaces", { it.chrome }, { t, c -> t.copy(chrome = c) }),
    Cards("cards", "Cards", "Cards, sheets and menus", "Surfaces", { it.surfaceElevated }, { t, c -> t.copy(surfaceElevated = c) }),
    Raised("raised", "Raised", "Fields, chips and pressed rows", "Surfaces", { it.surfaceHigh }, { t, c -> t.copy(surfaceHigh = c) }),
    CardEdges("card_edges", "Card edges", "The hairline around every card", "Surfaces", { it.cardOutline }, { t, c -> t.copy(cardOutline = c) }),
    Lines("lines", "Lines", "Dividers and frames", "Surfaces", { it.rule }, { t, c -> t.copy(rule = c) }),
    FaintLines("faint_lines", "Faint lines", "The quiet line between rows", "Surfaces", { it.ruleFaint }, { t, c -> t.copy(ruleFaint = c) }),
    Text("text", "Text", "Titles and chat text", "Text", { it.textHigh }, { t, c -> t.copy(textHigh = c) }, { it.ground }),
    TextMed("text_med", "Secondary text", "Descriptions and labels", "Text", { it.textMed }, { t, c -> t.copy(textMed = c) }, { it.ground }),
    TextLow("text_low", "Faint text", "Hints, times and placeholders", "Text", { it.textLow }, { t, c -> t.copy(textLow = c) }, { it.ground }),
    OnAccent("on_accent", "Text on accent", "Words on accent keys", "Text", { it.onRed }, { t, c -> t.copy(onRed = c) }, { it.red }),
    AccentTint("accent_tint", "Accent tint", "Soft accent fills behind chips", "Accent", { it.redDim }, { t, c -> t.copy(redDim = c) }),
    OnAccentTint("on_accent_tint", "Text on accent tint", "Words on those fills", "Accent", { it.onRedDim }, { t, c -> t.copy(onRedDim = c) }, { it.redDim }),
    Danger("danger", "Danger", "Delete and errors", "Signals", { it.danger }, { t, c -> t.copy(danger = c) }, { it.ground }),
    Success("success", "Success", "Done and connected", "Signals", { it.success }, { t, c -> t.copy(success = c) }, { it.ground }),
    Warn("warn", "Warning", "Cautions and notes", "Signals", { it.warn }, { t, c -> t.copy(warn = c) }, { it.ground }),
    Speech("speech", "Dialogue", "Quoted speech, unless Customize text says otherwise", "Chat text", { it.speech }, { t, c -> t.copy(speech = c) }, { it.ground }),
    Thought("thought", "Thoughts", "Inner thoughts, unless Customize text says otherwise", "Chat text", { it.thought }, { t, c -> t.copy(thought = c) }, { it.ground }),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** The colour [token] has in this look, set by hand or worked out. */
fun CustomColors.colorOf(token: CustomToken): Color = token.get(customTones(this))

/** The colour [token] would have on Auto. */
fun CustomColors.autoColorOf(token: CustomToken): Color = token.get(customTones(copy(overrides = emptyMap())))

/** Whether [token], as it stands, is too faint against what it is drawn on (4.5:1, as for words). */
fun CustomColors.hardToRead(token: CustomToken): Boolean {
    val behind = token.behind ?: return false
    val t = customTones(this)
    return contrast(token.get(t), behind(t)) < 4.5f
}

/**
 * A whole look from two colours. Light or dark follows the ground; cards, rules and the nav
 * bar are the ground stepped lighter or darker; text is near-white or near-black, tinted a
 * little toward the ground; the accent is moved just far enough from the ground to stand out
 * ([readableAccent]). The markup tints come from the shipped light or dark look.
 */
internal fun customTones(custom: CustomColors): Tones {
    val ground = Color(custom.ground).copy(alpha = 1f)
    val light = isLightGround(ground)
    val base = if (light) Palette.Daylight else Palette.Classic
    val toward = if (light) Color.Black else Color.White
    fun step(f: Float) = lerp(ground, toward, f)
    // Butler's own inks, or pure black/white on a ground where those fall short of 4.5:1.
    val ink = if (light) InkDark else InkLight
    val textHigh = if (contrast(ink, ground) >= 4.6f) ink else if (light) Color.Black else Color.White
    val accent = readableAccent(Color(custom.accent).copy(alpha = 1f), ground)
    val onAccent = if (contrast(Color.White, accent) >= contrast(Color(0xFF17181B), accent)) Color.White else Color(0xFF17181B)
    return Tones(
        isLight = light,
        ground = ground,
        chrome = if (light) lerp(ground, Color.Black, 0.08f) else lerp(ground, Color.Black, 0.45f),
        surfaceElevated = if (light) lerp(ground, Color.White, 0.7f) else step(0.06f),
        surfaceHigh = step(if (light) 0.06f else 0.13f),
        rule = step(if (light) 0.18f else 0.2f),
        ruleFaint = step(if (light) 0.09f else 0.1f),
        textHigh = textHigh,
        textMed = lerp(textHigh, ground, 0.24f),
        textLow = lerp(textHigh, ground, 0.45f),
        red = accent,
        redDim = lerp(ground, accent, 0.22f),
        onRedDim = lerp(accent, textHigh, if (light) 0.15f else 0.45f),
        onRed = onAccent,
        danger = base.danger,
        speech = base.speech,
        thought = base.thought,
        success = base.success,
        warn = base.warn,
        cardOutline = step(if (light) 0.18f else 0.22f),
    ).let { auto ->
        // Hand-set colours win, exactly as picked: the readability guard is for Auto only.
        custom.overrides.entries.fold(auto) { t, (key, argb) ->
            CustomToken.of(key)?.set?.invoke(t, Color(argb).copy(alpha = 1f)) ?: t
        }
    }
}

/** Whether [readableAccent] had to move the user's accent; Settings says so when it did. */
fun CustomColors.accentAdjusted(): Boolean {
    val ground = Color(ground).copy(alpha = 1f)
    val raw = Color(accent).copy(alpha = 1f)
    return readableAccent(raw, ground) != raw
}

/**
 * The accent, moved toward white or toward black (whichever needs the smaller step) until
 * it reads on the ground (3:1). Unchanged when it already does.
 */
internal fun readableAccent(accent: Color, ground: Color): Color {
    if (contrast(accent, ground) >= MIN_ACCENT_CONTRAST) return accent
    fun search(toward: Color): Pair<Int, Color>? {
        for (step in 1..20) {
            val c = lerp(accent, toward, step / 20f)
            if (contrast(c, ground) >= MIN_ACCENT_CONTRAST) return step to c
        }
        return null
    }
    val lighter = search(Color.White)
    val darker = search(Color.Black)
    return listOfNotNull(lighter, darker).minByOrNull { it.first }?.second
        ?: if (contrast(Color.White, ground) > contrast(Color.Black, ground)) Color.White else Color.Black
}

private const val MIN_ACCENT_CONTRAST = 3f

private val InkDark = Color(0xFF17181B)
private val InkLight = Color(0xFFF3F3F1)

/** Light when dark text reads better on it than light text does (mid greys included). */
private fun isLightGround(ground: Color): Boolean = contrast(InkDark, ground) > contrast(InkLight, ground)

/** WCAG contrast ratio of two colours. */
internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}
