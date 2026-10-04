package com.cherry.butler.core.background

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps Butler's process alive while a reply is generating, so leaving the app doesn't cut a
 * stream off. Its notification is silent and minimal; the one that matters is posted when
 * the reply lands ([ReplyNotifier.finished]).
 */
@AndroidEntryPoint
class ReplyService : Service() {

    @Inject lateinit var replies: BackgroundReplies
    @Inject lateinit var notifier: ReplyNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    /**
     * A foreground service alone isn't enough on every phone: Transsion's power manager
     * ("Hiber") judges a trickle of streamed text as idle, freezes the app and destroys its
     * sockets a few seconds after it leaves the screen. It leaves an app holding a wake lock
     * alone. Held only while something generates, and never longer than [WAKE_LIMIT_MS].
     */
    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Butler:reply")
            .apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android wants the service in the foreground within seconds of starting, even if the
        // reply already finished in the meantime.
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, ReplyNotifier.WORKING_ID, notifier.working(replies.working.value.values.toList()), type)
        // Not reference-counted: each start resets the window to a full WAKE_LIMIT_MS.
        wakeLock.acquire(WAKE_LIMIT_MS)
        if (watching == null) {
            watching = scope.launch {
                replies.working.collect { now ->
                    if (now.isEmpty()) {
                        releaseWakeLock()
                        ServiceCompat.stopForeground(this@ReplyService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notifier.updateWorking(now.values.toList())
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        releaseWakeLock()
        // Android 15's daily limit for this kind of service: let go rather than be killed.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** Longer than any reply; a hung stream can't keep the phone awake for good. */
        const val WAKE_LIMIT_MS = 10 * 60 * 1000L
    }
}
