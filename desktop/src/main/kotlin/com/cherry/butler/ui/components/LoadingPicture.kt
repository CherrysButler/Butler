package com.cherry.butler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * The desktop's LoadingPicture: the picture across the width it's given, as before. The
 * phone's version holds the picture's place while it loads and lets go of it off screen.
 */
@Composable
fun LoadingPicture(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.FillWidth,
        // Across the width it's given, whatever the caller left free.
        modifier = modifier.fillMaxWidth(),
    )
}
