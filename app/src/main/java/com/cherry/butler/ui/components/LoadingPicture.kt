package com.cherry.butler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import coil.compose.AsyncImage
import com.cherry.butler.core.design.ButlerTheme
import java.util.concurrent.ConcurrentHashMap

/** Width over height of pictures already shown this session: a revisit lays out at once. */
private val knownAspects = ConcurrentHashMap<String, Float>()

/** Held for a picture whose shape isn't known yet; the commonest shape for a banner or art. */
private const val GUESS_ASPECT = 16f / 9f

/**
 * A picture from the web that keeps its place while it loads: a quiet pulsing block stands
 * in at the picture's shape (known from an earlier load, or a guess), so the text around it
 * doesn't jump when it lands. Sized by [modifier]'s width; its height follows the picture.
 *
 * Holds its bitmap only while within about a screen of the window. A description can carry
 * twenty full-width pictures in one block, and all of them held at once ran to 130 MB of
 * graphics memory; further away the picture lets go and keeps its place (its shape is
 * remembered), and coming back reads it from the image cache.
 */
@Composable
fun LoadingPicture(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    var aspect by remember(url) { mutableStateOf(knownAspects[url]) }
    var waiting by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    // Unknown until first laid out: a frame's wait, rather than loading every picture at once.
    var near by remember(url) { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .aspectRatio(aspect ?: GUESS_ASPECT)
            .onGloballyPositioned { coords ->
                val screen = view.height.toFloat()
                val bounds = coords.boundsInWindow()
                // A screen of margin each way: loaded before it scrolls into view.
                val now = bounds.bottom > -screen && bounds.top < screen * 2f
                if (now != near) {
                    near = now
                    if (!now && !failed) waiting = true
                }
            },
    ) {
        // Decoded at the width it's shown at, not the file's own: a 4000 px picture costs a
        // fraction of the decode, memory and upload. Height may run to four widths; a taller
        // picture is scaled to fit that, which keeps even a long banner far under the limit.
        val context = androidx.compose.ui.platform.LocalContext.current
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth.coerceAtLeast(1) else 1080
        val request = remember(url, w) {
            coil.request.ImageRequest.Builder(context)
                .data(url)
                .size(w, w * 4)
                .scale(coil.size.Scale.FIT)
                .precision(coil.size.Precision.INEXACT)
                .build()
        }
        if (waiting && near) {
            val alpha = rememberSkeletonAlpha()
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { this.alpha = alpha }
                    .background(ButlerTheme.colors.surfaceHigh),
            )
        } else if (failed || !near) {
            Box(Modifier.matchParentSize().background(ButlerTheme.colors.surfaceHigh))
        }
        if (near && !failed) AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.isSpecified && size.width > 0f && size.height > 0f) {
                    val a = size.width / size.height
                    knownAspects[url] = a
                    aspect = a
                }
                waiting = false
            },
            // A dead link keeps its quiet block rather than collapsing and shifting the page.
            onError = { waiting = false; failed = true },
        )
    }
}
