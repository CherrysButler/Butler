package com.cherry.butler.core.design

import androidx.compose.runtime.staticCompositionLocalOf

/** What the thinking line shows while a thought is being written: a Settings choice. */
enum class ThinkingLook(val key: String, val label: String, val blurb: String) {
    Butler("butler", "Butler", "A little pixel butler walks the line, the reply on his tray"),
    Dither("dither", "Dither", "Bands of dots drift along the line and thicken as the thought grows"),
    Field("field", "Dot field", "A field of dots, as on Browse, drifting in two slow waves"),
}

/** What a finished thought folds to above the reply: a Settings choice. */
enum class ThoughtLook(val key: String, val label: String, val blurb: String) {
    Stripe("stripe", "Stripe", "A line with the thought's first words"),
    Box("box", "Small box", "The animation goes on in a small square, an arrow beside it"),
    Button("button", "Button", "Just \"Thought for 12s\"; tap to read it"),
}

val LocalThinkingLook = staticCompositionLocalOf { ThinkingLook.Butler }
val LocalThoughtLook = staticCompositionLocalOf { ThoughtLook.Stripe }
