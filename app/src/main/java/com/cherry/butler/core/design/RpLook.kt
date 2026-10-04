package com.cherry.butler.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * How the reader wants roleplay text drawn: Janitor's "Customize" page, Butler's way.
 * A null colour means "the theme's own"; colours are ARGB.
 */
@Immutable
data class RpLook(
    val narration: Long? = null,
    val speech: Long? = null,
    val action: Long? = null,
    val thought: Long? = null,
    val strong: Long? = null,
    /** `*action*` in italics (on) or upright in its colour only (off). */
    val italicActions: Boolean = true,
    /** Keep the quote marks around speech. Janitor shows them. */
    val showQuotes: Boolean = true,
    /** Chat prose size in sp. */
    val textSize: Int = 16,
)

val LocalRpLook = staticCompositionLocalOf { RpLook() }

/** The reading style for chat prose, at the reader's chosen size. */
@Composable
@ReadOnlyComposable
fun proseStyle(): TextStyle {
    val size = LocalRpLook.current.textSize
    return MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, lineHeight = (size * 1.56f).sp)
}
