package com.cherry.butler.core.design

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How surfaces show what sits above what (Settings › Look › Depth). Flat is Butler as it was:
 * depth from tone steps and a hairline only. Lifted adds light from above: a rim of light
 * along the top edge of what is raised, a soft contact shadow under it, and the nav bar
 * floating as an island. Content stays opaque and as legible either way.
 */
enum class Depth(val key: String, val label: String, val blurb: String) {
    Flat("flat", "Flat", "Tones and hairlines only, as before"),
    Lifted("lifted", "Lifted", "Light from above: soft shadows and a floating bar"),
}

val LocalDepth = staticCompositionLocalOf { Depth.Flat }

/** The depth a look wears when none was picked: Lifted on every look (true black draws it in light, not shadow). */
@Suppress("UNUSED_PARAMETER")
fun Depth?.orDefaultFor(theme: AppTheme): Depth = this ?: Depth.Lifted
