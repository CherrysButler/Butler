package com.cherry.butler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill

/** Every bottom sheet's grip: a short pill. */
@Composable
fun SheetHandle() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).background(ButlerTheme.colors.textLow.copy(alpha = 0.6f), Pill))
    }
}

/** The dim behind a sheet or dialog. */
val SheetScrim = Color.Black.copy(alpha = 0.6f)
