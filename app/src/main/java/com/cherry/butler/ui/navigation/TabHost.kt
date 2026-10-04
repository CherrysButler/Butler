package com.cherry.butler.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId

/** Whether the tab a screen lives on is the one showing. Hidden tabs leave Back alone. */
val LocalTabActive = staticCompositionLocalOf { true }

/**
 * The five tabs, all composed, one measured. Switching tabs is then a cut with no work in
 * it: the other screens keep their composition, their lists, their scroll and their
 * view-models, so coming back is the same frame you left. (A screen that isn't measured
 * costs nothing per frame; its effects keep running, which is what keeps it current.)
 */
@Composable
fun TabHost(selected: TopLevelDestination, modifier: Modifier = Modifier, content: @Composable (TopLevelDestination) -> Unit) {
    Layout(
        modifier = modifier,
        content = {
            TopLevelDestination.entries.forEach { dest ->
                key(dest) {
                    androidx.compose.foundation.layout.Box(Modifier.layoutId(dest)) {
                        CompositionLocalProvider(LocalTabActive provides (dest == selected)) { content(dest) }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val shown = measurables.first { it.layoutId == selected }.measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) { shown.place(0, 0) }
    }
}
