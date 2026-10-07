package com.cherry.butler.core.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import com.cherry.butler.MainActivity
import com.cherry.butler.R
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.feature.chat.ThinkingWords
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The words on Butler's reply notifications. They speak the chat's own language: the same
 * word the thinking line used for that reply ("Laine is moseying…", "Laine finished
 * moseying"), never "Bot replied".
 */
@Singleton
class ReplyNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    // Constructed so the user's thinking words are loaded before a notification needs one.
    @Suppress("unused") private val chatPrefs: com.cherry.butler.core.data.ChatPrefs,
) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val system = context.getSystemService(NotificationManager::class.java)
            system.createNotificationChannel(
                NotificationChannel(CHANNEL_WORKING, "Replies in progress", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while a reply is still being written, so it can finish after you leave."
                    setShowBadge(false)
                },
            )
            system.createNotificationChannel(
                NotificationChannel(CHANNEL_REPLIES, "Replies", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a reply lands while you're elsewhere."
                },
            )
        }
    }

    fun working(now: List<BackgroundReplies.Working>): Notification {
        val first = now.firstOrNull()
        val title = when {
            first == null -> "Finishing up…"
            now.size == 1 -> "${first.name} is ${wordFor(first.botLocalId)}…"
            else -> "${now.size} replies on their way"
        }
        val text = if (now.size > 1) now.joinToString(", ") { it.name } else "You can leave; it keeps going."
        return NotificationCompat.Builder(context, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_stat_butler)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .apply { first?.let { setContentIntent(openChat(it.chatId)) } }
            .build()
    }

    fun updateWorking(now: List<BackgroundReplies.Working>) {
        if (!allowed()) return
        manager.notify(WORKING_ID, working(now))
    }

    /** [private]: Butler is locked, so the reply's words stay out of the shade. */
    suspend fun finished(chatId: Long, name: String, avatar: String?, botLocalId: Long?, text: String, private: Boolean = false) {
        if (!allowed()) return
        val plain = if (private) "Open Butler to read it." else plain(text)
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_stat_butler)
            .setContentTitle("$name finished ${wordFor(botLocalId)}")
            .setContentText(firstSentence(plain))
            .setStyle(NotificationCompat.BigTextStyle().bigText(plain.take(600)))
            .setLargeIcon(avatarBitmap(avatar))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openChat(chatId))
            .build()
        manager.notify(idFor(chatId), notification)
    }

    suspend fun failed(
        chatId: Long,
        name: String,
        avatar: String?,
        botLocalId: Long?,
        reason: String,
        partial: Boolean,
        pausedByPhone: Boolean,
    ) {
        if (!allowed()) return
        val word = wordFor(botLocalId)
        val text = when {
            !pausedByPhone -> reason
            BackgroundReplies.freezesAnyway ->
                "Your ${BackgroundReplies.maker} paused Butler when you left. Open the chat to pick it back up."
            else -> "Your phone paused Butler when you left. Allow it to keep running and replies will finish on their own."
        }
        val offerAllow = pausedByPhone && !BackgroundReplies.freezesAnyway && !BackgroundReplies.isExempt(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_stat_butler)
            .setContentTitle(if (partial) "$name stopped $word halfway" else "$name stopped $word")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .apply {
                if (offerAllow) {
                    val allow = PendingIntent.getActivity(
                        context, ALLOW_REQUEST, BackgroundReplies.exemptIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    addAction(0, "Allow", allow)
                }
            }
            .setLargeIcon(avatarBitmap(avatar))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openChat(chatId))
            .build()
        manager.notify(idFor(chatId), notification)
    }

    fun cancel(chatId: Long) {
        manager.cancel(idFor(chatId))
    }

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openChat(chatId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(OpenChatRequests.EXTRA_CHAT_ID, chatId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, idFor(chatId), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private suspend fun avatarBitmap(avatar: String?): Bitmap? {
        val url = JanitorConfig.avatarUrl(avatar) ?: return null
        return withTimeoutOrNull(4_000) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(192)
                .allowHardware(false)
                .transformations(CircleCropTransformation())
                .build()
            ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }
    }

    companion object {
        const val WORKING_ID = 7_001
        private const val ALLOW_REQUEST = 7_002
        private const val CHANNEL_WORKING = "replies_working"
        private const val CHANNEL_REPLIES = "replies"

        /** Always even; [WORKING_ID] is odd, so no chat can take the in-progress slot. */
        private fun idFor(chatId: Long): Int = (chatId xor (chatId ushr 32)).toInt() shl 1

        /** The reply's own thinking word, lower-cased to sit mid-sentence. */
        fun wordFor(botLocalId: Long?): String = (botLocalId?.let { ThinkingWords.forSeed(it) } ?: "Thinking").lowercase()

        /** Prose as a notification can show it: no markup, one run of text. */
        fun plain(text: String): String = text
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("[*_`#>~]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        /** The opening sentence, or a clean cut of it when it runs long. */
        fun firstSentence(plain: String, max: Int = 140): String {
            val end = Regex("[.!?…](\"|”)?(\\s|$)").findAll(plain).map { it.range.last + 1 }.firstOrNull { it >= 24 }
            val sentence = plain.substring(0, end ?: plain.length).trim()
            if (sentence.length <= max) return sentence
            val cut = sentence.take(max).substringBeforeLast(' ').ifBlank { sentence.take(max) }
            return "$cut…"
        }
    }
}
