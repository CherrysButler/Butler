package com.cherry.butler.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Butler's drop-down menu: every menu in the app is this one, so they share a shape and a
 * surface. With Depth Lifted it is a pane of glass (Glass.kt); otherwise the raised surface.
 */
@Composable
fun ButlerMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val glass = glassAvailable()
    // On a touch screen the menu does not take the window's focus, so a touch outside it
    // shuts it and still lands where it was aimed (a scroll scrolls). Holding the focus, the
    // first touch only shut the menu and the thing wanted had to be touched again. Without
    // the focus it does not hear Back, so that is heard here. With a pointer it is a menu as
    // a PC's are.
    val pointer = LocalPointerMotion.current
    // The touch that shuts the menu now lands, and when it lands on the menu's own key that
    // key opens the menu again: tapped to shut, it sprang back. A menu asked to open within a
    // moment of being shut by a touch is that same touch, and stays shut.
    val shutAt = androidx.compose.runtime.remember { longArrayOf(0L) }
    val sameTouch = expanded && !pointer && System.nanoTime() / 1_000_000 - shutAt[0] < 450
    if (sameTouch) androidx.compose.runtime.LaunchedEffect(Unit) { onDismissRequest() }
    androidx.activity.compose.BackHandler(enabled = expanded && !pointer) { onDismissRequest() }
    DropdownMenu(
        expanded = expanded && !sameTouch,
        onDismissRequest = {
            shutAt[0] = System.nanoTime() / 1_000_000
            onDismissRequest()
        },
        // The menu's own surface cuts the pane to its shape and casts its shadow.
        modifier = if (glass) Modifier.glassPane(shape, lift = null, clip = false).then(modifier) else modifier,
        offset = offset,
        properties = androidx.compose.ui.window.PopupProperties(focusable = pointer),
        shape = shape,
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}
