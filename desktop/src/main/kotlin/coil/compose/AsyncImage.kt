package coil.compose

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.BitmapImage
import coil3.compose.AsyncImagePainter
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/**
 * Coil 2's AsyncImage, drawn by Coil 3, with one change that matters on the desktop.
 *
 * Compose Desktop draws an ImageBitmap by wrapping it in a new Skia image on every frame.
 * Here each loaded picture becomes one immutable Skia image, made once and drawn as itself.
 * A picture fresh off the network settles in over 180 ms; one already seen just appears.
 */
@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
) {
    // The fade is set where the new picture is handed over, before it is drawn even once, so
    // its first frame is already transparent. (Starting it a frame later drew the picture
    // whole for one frame, then blanked it: the flicker.) Only a picture that just came over
    // the network fades; one from memory or disk was seen before and is simply there.
    val shown = remember { mutableFloatStateOf(1f) }
    val fading by remember { androidx.compose.runtime.derivedStateOf { shown.floatValue < 1f } }
    var fades by remember { mutableIntStateOf(0) }
    LaunchedEffect(fades) {
        if (fades == 0) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (shown.floatValue < 1f) {
            withFrameNanos { now -> shown.floatValue = FadeEasing.transform(((now - start) / FADE_NANOS).coerceIn(0f, 1f)) }
        }
    }
    coil3.compose.AsyncImage(
        model = coil.request.ImageRequest.unwrap(model),
        contentDescription = contentDescription,
        // A layer only while fading in: at rest the picture draws straight into its card.
        modifier = if (fading) modifier.graphicsLayer { this.alpha = shown.floatValue } else modifier,
        transform = { state ->
            val success = state as? AsyncImagePainter.State.Success
            val image = success?.result?.image as? BitmapImage
            if (success != null && success.result.dataSource == coil3.decode.DataSource.NETWORK) {
                shown.floatValue = 0f
                fades++
            }
            if (image != null) state.copy(painter = SkiaImagePainter(image, filterQuality)) else state
        },
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
    )
}

private const val FADE_NANOS = 180_000_000f
private val FadeEasing = LinearOutSlowInEasing

/** One decoded picture as one immutable Skia image, drawn as itself every frame. */
private class SkiaImagePainter(source: BitmapImage, quality: FilterQuality) : Painter() {
    private val image: org.jetbrains.skia.Image = SkiaImages.of(source.bitmap)
    private val sampling: SamplingMode = when (quality) {
        FilterQuality.None -> SamplingMode.DEFAULT
        FilterQuality.Low -> FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        else -> CubicResampler(1f / 3f, 1f / 3f)
    }
    private val paint = Paint()
    private val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())

    override val intrinsicSize: Size = Size(image.width.toFloat(), image.height.toFloat())

    override fun applyAlpha(alpha: Float): Boolean {
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        paint.colorFilter = colorFilter?.asSkiaColorFilter()
        return true
    }

    private var dst = Rect.makeWH(0f, 0f)

    override fun DrawScope.onDraw() {
        if (dst.width != size.width || dst.height != size.height) dst = Rect.makeWH(size.width, size.height)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawImageRect(image, src, dst, sampling, paint, true)
        }
    }
}

/**
 * One Skia image per decoded bitmap, for as long as the bitmap lives: a card scrolled away
 * and back (or the same face in two places) draws the image it had, so the GPU texture made
 * from it is reused rather than a new wrapper made for the same pixels each time.
 */
private object SkiaImages {
    private val images = java.util.WeakHashMap<org.jetbrains.skia.Bitmap, org.jetbrains.skia.Image>()

    fun of(bitmap: org.jetbrains.skia.Bitmap): org.jetbrains.skia.Image = synchronized(images) {
        images.getOrPut(bitmap) {
            if (!bitmap.isImmutable) bitmap.setImmutable()
            org.jetbrains.skia.Image.makeFromBitmap(bitmap)
        }
    }
}

/** Coil 2's painter for a picture inside other drawing (the rich editor's), by Coil 3. */
@Composable
fun rememberAsyncImagePainter(model: Any?): Painter = coil3.compose.rememberAsyncImagePainter(coil.request.ImageRequest.unwrap(model))
