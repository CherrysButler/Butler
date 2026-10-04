package com.cherry.butler.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalFocusManager

/**
 * A search box is done with once the keyboard goes away (back, or the keyboard's own hide
 * key) or the list under it is moved by a finger. Returns the connection to put on that
 * list with `nestedScroll`; the keyboard half works by being called at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberLetGoOfField(focusManager: FocusManager = LocalFocusManager.current): NestedScrollConnection {
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible) focusManager.clearFocus() }
    return remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }
}
