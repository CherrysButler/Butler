package com.cherry.butler.core.background

import android.content.Context
import com.cherry.butler.core.data.ChatPrefs
import com.cherry.butler.desktop.DesktopTray
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The desktop's reply notifications: a Windows notification from the tray icon when a reply
 * lands or stops while Butler isn't the window in front. Same calls as Android's.
 */
@Singleton
class ReplyNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    @Suppress("unused") private val chatPrefs: ChatPrefs,
) {
    fun updateWorking(now: List<BackgroundReplies.Working>) = Unit

    suspend fun finished(chatId: Long, name: String, avatar: String?, botLocalId: Long?, text: String, private: Boolean = false) {
        val plain = if (private) "Open Butler to read it." else text.replace(Regex("[*_`~]"), "").trim()
        DesktopTray.notify(chatId, "$name finished replying", plain.take(240))
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
        DesktopTray.notify(chatId, if (partial) "$name stopped halfway" else "$name stopped", reason)
    }

    fun cancel(chatId: Long) = Unit

    companion object {
        const val WORKING_ID = 7_001
    }
}
