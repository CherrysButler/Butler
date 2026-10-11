package com.cherry.butler.core.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Type.kt's Font(R.font.x, ...) on the desktop: the build turns those calls into this one
 * (see sharedSources in desktop/build.gradle.kts), which loads the bundled file. Android's
 * resource fonts can't be loaded here. A variable font's weight axis isn't applied; Skia
 * picks the nearest face.
 */
internal fun ButlerFont(
    resId: Int,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings? = null,
): Font = androidx.compose.ui.text.platform.Font(com.cherry.butler.R.path(resId), weight, style)
