package com.cherry.butler.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.io.File

/**
 * A chat background, filling its box: the picture (a stored [file], or an [image] still being
 * edited), the theme's ground laid over it at [dim] so words on top stay readable, and, with
 * [parallax], a small drift as the phone tilts. The chat and the background editor both draw
 * through here, so the preview is the real thing.
 */
@Composable
fun Backdrop(
    dim: Float,
    parallax: Boolean,
    modifier: Modifier = Modifier,
    file: File? = null,
    fileKey: String? = null,
    image: ImageBitmap? = null,
) {
    val context = LocalContext.current
    val moves = parallax && remember { animationsOn(context) }
    val tilt = rememberTilt(enabled = moves)
    // Read in the draw phase only: a tilt redraws this layer, nothing recomposes.
    val drift = Modifier.fillMaxSize().graphicsLayer {
        if (moves) {
            scaleX = PARALLAX_SCALE
            scaleY = PARALLAX_SCALE
            val t = tilt.value
            translationX = t.x * size.width * (PARALLAX_SCALE - 1f) / 2f
            translationY = t.y * size.height * (PARALLAX_SCALE - 1f) / 2f
        }
    }
    Box(modifier.fillMaxSize()) {
        when {
            image != null -> Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = drift)
            file != null -> {
                val request = remember(fileKey ?: file.path) {
                    ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey(fileKey ?: file.path)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = drift)
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = dim)))
    }
}

/** How much larger the picture is drawn, which is also how far it can drift: 2% each way. */
private const val PARALLAX_SCALE = 1.04f

/** Radians of tilt that take the picture all the way to its edge. */
private const val MAX_TILT = 0.5f

/** Off when the phone's animations are off (Settings › Accessibility): no drifting then either. */
private fun animationsOn(context: Context): Boolean =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

/**
 * How far the phone is tilted from where it is being held, each axis -1..1, smoothed.
 * The game rotation vector (no compass, so no jumps near metal) is read only while the
 * screen is resumed. "Where it is held" follows slowly, so lying down or changing grip
 * re-centres the picture instead of leaving it pinned to one side.
 */
@Composable
private fun rememberTilt(enabled: Boolean): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, lifecycle) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var baseX = Float.NaN
        var baseY = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                val pitch = angles[1]
                val roll = angles[2]
                if (baseX.isNaN()) { baseX = roll; baseY = pitch }
                // The resting angle catches up over several seconds.
                baseX += (roll - baseX) * 0.008f
                baseY += (pitch - baseY) * 0.008f
                val tx = ((roll - baseX) / MAX_TILT).coerceIn(-1f, 1f)
                val ty = ((pitch - baseY) / MAX_TILT).coerceIn(-1f, 1f)
                val now = tilt.value
                tilt.value = Offset(now.x + (tx - now.x) * 0.1f, now.y + (ty - now.y) * 0.1f)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    baseX = Float.NaN
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                }
                Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    return tilt
}
