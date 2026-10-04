package com.cherry.butler.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounded, but with an edge: marks at 4, chips and fields at 8, cards and keys at 12,
 * grouped sections at 14, the composer at 18. Janitor's anatomy, drawn a notch harder.
 * Pills are [Pill], kept for what is a tag or a count, never for a key or a field.
 */
val ButlerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

val Pill = RoundedCornerShape(50)

/** A bottom sheet's top edge. */
val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** Lights out: near-square, as the true-black world was drawn. Sheets and pills keep their own shapes. */
val LightsOutShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp),
)
