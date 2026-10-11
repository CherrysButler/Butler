package androidx.core.content

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object ContextCompat {
    fun checkSelfPermission(context: Context, permission: String): Int = PackageManager.PERMISSION_GRANTED
    fun startForegroundService(context: Context, intent: Intent) { context.startForegroundService(intent) }
}
