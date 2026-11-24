package com.cherry.butler.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cherry.butler.core.design.Motion

/**
 * What a switch or a mark opens under itself: it rolls down from its top edge and back up
 * into it, on Motion's own beat. For rows a setting brings with it when it is turned on, in
 * place of a bare `if`, which stood them there in one frame, and of AnimatedVisibility's own
 * default, which grows from the corner and so seems to slide in from the side.
 */
@Composable
fun Unfold(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(Motion.enter(), expandFrom = Alignment.Top) + fadeIn(Motion.enter()),
        exit = shrinkVertically(Motion.exit(), shrinkTowards = Alignment.Top) + fadeOut(Motion.exit()),
    ) {
        Column { content() }
    }
}
