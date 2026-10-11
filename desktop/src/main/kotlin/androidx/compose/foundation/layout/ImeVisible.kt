package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable

/** A PC keyboard isn't on screen, so it's never "up". */
@ExperimentalLayoutApi
val WindowInsets.Companion.isImeVisible: Boolean
    @Composable get() = false
