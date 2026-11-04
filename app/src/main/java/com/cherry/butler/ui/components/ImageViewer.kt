package com.cherry.butler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * A picture on its own, full screen, over black: the character's portrait as uploaded.
 * Pinch to look closer, drag while zoomed, tap anywhere (or the cross) to put it away.
 */
@Composable
fun ImageViewer(url: String, name: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale > 1f) offset + pan else Offset.Zero
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .transformable(transform),
        ) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/** Opens a picture full screen ([ImageViewer]); null outside a [PictureViewerHost]. */
val LocalPictureViewer = androidx.compose.runtime.staticCompositionLocalOf<((url: String, name: String) -> Unit)?> { null }

/**
 * Where any picture under it opens full screen: a description's pictures, a chat's, your
 * persona's face, a character's portrait. One viewer for the whole app.
 */
@Composable
fun PictureViewerHost(content: @Composable () -> Unit) {
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    val show = remember { { url: String, name: String -> open = url to name } }
    androidx.compose.runtime.CompositionLocalProvider(LocalPictureViewer provides show, content = content)
    open?.let { (url, name) -> ImageViewer(url = url, name = name, onDismiss = { open = null }) }
}

/** Taps open [url] full screen, where a [PictureViewerHost] is there to show it. */
@Composable
fun Modifier.opensPicture(url: String?, name: String = ""): Modifier {
    val viewer = LocalPictureViewer.current
    if (viewer == null || url.isNullOrBlank()) return this
    return clickable(onClickLabel = "Show picture") { viewer(url, name) }
}
