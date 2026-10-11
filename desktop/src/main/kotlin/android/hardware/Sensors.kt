package android.hardware

/** A PC has no motion sensor: getDefaultSensor answers null and the backdrop stays still. */
class Sensor private constructor() {
    companion object { const val TYPE_GAME_ROTATION_VECTOR = 15 }
}

class SensorEvent(@JvmField val values: FloatArray, @JvmField val timestamp: Long = 0L)

interface SensorEventListener {
    fun onSensorChanged(event: SensorEvent)
    fun onAccuracyChanged(sensor: Sensor, accuracy: Int)
}

class SensorManager {
    fun getDefaultSensor(type: Int): Sensor? = null
    fun registerListener(listener: SensorEventListener, sensor: Sensor?, rate: Int): Boolean = false
    fun unregisterListener(listener: SensorEventListener) = Unit

    companion object {
        const val SENSOR_DELAY_GAME = 1
        const val SENSOR_DELAY_UI = 2
        fun getRotationMatrixFromVector(matrix: FloatArray, vector: FloatArray) = Unit
        fun getOrientation(matrix: FloatArray, values: FloatArray): FloatArray = values
    }
}
