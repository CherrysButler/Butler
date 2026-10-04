package com.cherry.butler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.cherry.butler.core.design.ButlerTheme

/**
 * Every thumb in the app goes through here, for one reason: scroll performance.
 *
 * Janitor serves avatars at full upload resolution. Decoding a 1500px image into a 56dp
 * slot on every scroll is what drops frames, so the request is sized to the slot in
 * pixels and Coil decodes at that size, caches it at that size, and reuses it.
 *
 * NSFW obscuring uses the same mechanism instead of a render-effect blur: the image is
 * requested at a handful of pixels and upscaled with bilinear filtering, which reads as
 * a soft blur and costs nothing per frame.
 *
 * The thumb is a soft rounded square, as in Janitor.
 */
@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    obscured: Boolean = false,
    initialStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val request = remember(url, px, obscured) {
        ImageRequest.Builder(context)
            .data(url)
            .size(if (obscured) Size(OBSCURED_PX, OBSCURED_PX) else Size(px, px))
            .memoryCacheKey(if (obscured) "$url@obscured" else "$url@$px")
            .allowHardware(true)
            .build()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(ButlerTheme.colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            style = initialStyle,
            color = ButlerTheme.colors.textLow,
        )
        if (url != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val OBSCURED_PX = 10
