package android.content

import android.content.pm.PackageManager
import android.content.res.Resources
import java.io.File

/**
 * The slice of Android's Context that Butler's shared code uses, backed by the desktop:
 * files under %APPDATA%\Butler, prefs as files there, URIs as local files, intents as the
 * browser and the clipboard. There is one, [com.cherry.butler.desktop.DesktopContext].
 */
abstract class Context {
    abstract val filesDir: File
    abstract val cacheDir: File
    open val packageName: String get() = "com.cherry.butler"
    open val applicationContext: Context get() = this
    open val resources: Resources get() = Resources
    open val packageManager: PackageManager get() = PackageManager
    open val contentResolver: ContentResolver get() = ContentResolver
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
    open fun getSystemService(name: String): Any? = null
    open fun <T> getSystemService(type: Class<T>): T? = null
    abstract fun startActivity(intent: Intent)
    open fun startForegroundService(intent: Intent): ComponentName? = null

    companion object {
        const val MODE_PRIVATE = 0
        const val POWER_SERVICE = "power"
        const val SENSOR_SERVICE = "sensor"
        const val NOTIFICATION_SERVICE = "notification"
        const val CLIPBOARD_SERVICE = "clipboard"
    }
}

class ComponentName(val packageName: String, val className: String) {
    constructor(context: Context, cls: Class<*>) : this(context.packageName, cls.name)
}
