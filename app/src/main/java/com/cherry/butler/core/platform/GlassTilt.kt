package com.cherry.butler.core.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How the phone is held to the light, for the glass (LocalGlassLight): x and y from -1 to 1,
 * (0, 0) held as a phone usually is. Read off gravity, eased, and in small steps, so the glass
 * is redrawn a few times a second while the hand moves and not at all while it is still.
 * Listens only while [on] and the app is in front.
 */
@Composable
fun rememberGlassTilt(on: Boolean): State<Offset> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(on, owner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!on || manager == null || sensor == null) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val listener = object : SensorEventListener {
            private var x = 0f
            private var y = 0f
            private var primed = false

            override fun onSensorChanged(event: SensorEvent) {
                // Left edge down reads +x: the right edge is then the one turned up to the light.
                val nowX = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                // Upright reads +1, flat 0; a phone in the hand rests at about two thirds.
                val nowY = (REST - event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                if (!primed) { x = nowX; y = nowY; primed = true } else {
                    x += (nowX - x) * EASE
                    y += (nowY - y) * EASE
                }
                val stepped = Offset((x / STEP).roundToInt() * STEP, (y / STEP).roundToInt() * STEP)
                val was = tilt.value
                if (abs(stepped.x - was.x) > STEP / 2 || abs(stepped.y - was.y) > STEP / 2) tilt.value = stepped
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var listening = false
        fun listen(now: Boolean) {
            if (now == listening) return
            listening = now
            if (now) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) else manager.unregisterListener(listener)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> listen(true)
                Lifecycle.Event.ON_PAUSE -> listen(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) listen(true)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            listen(false)
        }
    }
    return tilt
}

/** Where gravity's y sits with the phone held as usual. */
private const val REST = 0.66f
/** How much of the way to the new reading each one moves. */
private const val EASE = 0.18f
/** The smallest change passed on. */
private const val STEP = 0.03f
