package com.cherry.butler.core.background

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.SendJobEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replies that keep going when Butler isn't on screen.
 *
 * The pipeline already runs outside any screen; what Android needs is a foreground service
 * while a reply is in flight, or the process can be frozen or killed mid-stream. This keeps
 * that service up exactly as long as something is generating, and tells the user how it went
 * when they aren't looking at that chat.
 */
@Singleton
class BackgroundReplies @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ButlerDatabase,
    private val notifier: ReplyNotifier,
    private val presence: ChatPresence,
    private val widgets: ButlerWidgets,
    private val lock: com.cherry.butler.core.security.AppLock,
) {
    data class Working(val chatId: Long, val botLocalId: Long?, val name: String)

    private val _working = MutableStateFlow<Map<Long, Working>>(emptyMap())
    /** What's generating now, by chat; the service shows it and stops when it empties. */
    val working: StateFlow<Map<Long, Working>> = _working.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun started(job: SendJobEntity) {
        val name = db.chatDao().get(job.chatId)?.characterName?.takeIf { it.isNotBlank() } ?: "Your character"
        val working = Working(job.chatId, job.botMessageLocalId, name)
        _working.update { it + (job.chatId to working) }
        // Every start reaches the service, which renews its wake lock: overlapping replies
        // must not run past the first one's window.
        startService()
        widgets.working = working
        widgets.refresh()
    }

    fun stopped(chatId: Long) {
        _working.update { it - chatId }
        widgets.working = _working.value.values.firstOrNull()
        widgets.refresh()
    }

    /** A reply landed. Says so unless the user is reading that chat. */
    suspend fun finished(job: SendJobEntity) {
        if (presence.isViewing(job.chatId)) return
        val chat = db.chatDao().get(job.chatId) ?: return
        val text = job.botMessageLocalId?.let { db.messageDao().get(it) }?.text.orEmpty()
        if (text.isBlank()) return
        notifier.finished(chat.id, chat.characterName, chat.characterAvatar, job.botMessageLocalId, text, private = lock.wouldBeLocked())
    }

    /** A reply gave up for good (not a retry the pipeline will make by itself). */
    suspend fun failed(job: SendJobEntity, reason: String, partial: Boolean, network: Boolean = false) {
        if (presence.isViewing(job.chatId)) return
        val chat = db.chatDao().get(job.chatId) ?: return
        val pausedByPhone = network && pausedByPhone()
        notifier.failed(chat.id, chat.characterName, chat.characterAvatar, job.botMessageLocalId, reason, partial, pausedByPhone)
    }

    /**
     * The connection died mid-reply and the pipeline will try again. If the phone is the
     * likely cause (Butler isn't exempt from its power manager), say so now: the retry
     * itself may not run until Butler is opened again.
     */
    suspend fun interrupted(job: SendJobEntity, reason: String) {
        if (!pausedByPhone()) return
        failed(job, reason, partial = false, network = true)
    }

    /** Whether a cut connection is most likely the phone's doing. */
    private fun pausedByPhone(): Boolean =
        if (freezesAnyway) !widgets.isPlaced() else !isExempt(context)

    /** True the first time a reply is sent on Android 13+ without notification permission. */
    fun shouldAskPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        if (prefs.getBoolean(KEY_ASKED, false)) return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    fun markPermissionAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ReplyService::class.java))
        } catch (e: Exception) {
            // Started from the background (a retry after Butler was closed): Android refuses.
            // The reply still runs for as long as the process lives.
            Log.w(TAG, "couldn't start the reply service", e)
        }
    }

    companion object {
        /**
         * Whether the phone leaves Butler running when it's off screen. Without this some
         * phones (Transsion's "Hiber" among them) freeze it seconds after it leaves, foreground
         * service or not, and the reply's connection dies.
         */
        fun isExempt(context: Context): Boolean =
            (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)

        /** Android's own "let this app run in the background?" prompt. */
        @android.annotation.SuppressLint("BatteryLife")
        fun exemptIntent(context: Context): Intent =
            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))

        /** Where to take it back. */
        fun exemptSettingsIntent(): Intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        /**
         * Phones whose power manager freezes apps even with a foreground service and the
         * battery exemption ("no known solution on the developer end", dontkillmyapp.com).
         * On Transsion's, what holds is a home-screen widget: its freezer ("Hiber") leaves
         * apps with a placed widget alone (verified on the device).
         */
        val freezesAnyway: Boolean
            get() = Build.MANUFACTURER.lowercase() in setOf("tecno", "infinix", "itel", "transsion")

        private const val TAG = "BackgroundReplies"
        private const val PREFS = "butler_prefs"
        private const val KEY_ASKED = "asked_notifications"

        /** "Infinix", as the phone names its maker. */
        val maker: String
            get() = Build.MANUFACTURER.lowercase().replaceFirstChar { it.uppercase() }
    }
}
