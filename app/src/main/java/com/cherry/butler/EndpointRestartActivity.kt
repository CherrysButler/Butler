package com.cherry.butler

import android.app.Activity
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.os.RemoteException

/**
 * Relaunches Butler after the previous process and its endpoint-specific graph have exited.
 * Runs in a separate process so it can wait for that exit.
 */
class EndpointRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previous = intent.getBundleExtra(EXTRA_PREVIOUS)
        val binder = previous?.getBinder(EXTRA_BINDER)
        val pid = previous?.getInt(EXTRA_PID) ?: 0

        if (binder == null || pid <= 0 || pid == Process.myPid()) {
            finishAndRemoveTask()
            return
        }

        try {
            binder.linkToDeath({ runOnUiThread(::launchButler) }, 0)
            Process.killProcess(pid)
        } catch (_: RemoteException) {
            launchButler()
        }
    }

    private fun launchButler() {
        startActivity(Intent.makeRestartActivityTask(android.content.ComponentName(this, MainActivity::class.java)))
        finishAndRemoveTask()
    }

    companion object {
        const val PROCESS_SUFFIX = ":endpoint_restart"
        private const val EXTRA_PREVIOUS = "previous"
        private const val EXTRA_BINDER = "binder"
        private const val EXTRA_PID = "pid"

        fun launch(activity: Activity): Boolean = runCatching {
            val previous = Bundle().apply {
                putBinder(EXTRA_BINDER, Binder())
                putInt(EXTRA_PID, Process.myPid())
            }
            activity.startActivity(
                Intent(activity, EndpointRestartActivity::class.java)
                    .putExtra(EXTRA_PREVIOUS, previous)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }
}
