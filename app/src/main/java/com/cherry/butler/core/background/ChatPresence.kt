package com.cherry.butler.core.background

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which chat is on screen right now (resumed, not merely in the back stack). A reply that
 * lands there needs no notification; one that lands anywhere else does.
 */
@Singleton
class ChatPresence @Inject constructor(private val notifier: ReplyNotifier) {
    @Volatile private var viewing: Long? = null

    fun enter(chatId: Long) {
        viewing = chatId
        // Reading the chat answers its notification.
        notifier.cancel(chatId)
    }

    fun leave(chatId: Long) {
        if (viewing == chatId) viewing = null
    }

    fun isViewing(chatId: Long): Boolean = viewing == chatId
}

/** A chat to open, from a tapped notification; the shell consumes it. */
@Singleton
class OpenChatRequests @Inject constructor() {
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val pending: kotlinx.coroutines.flow.StateFlow<Long?> = _pending

    fun post(chatId: Long) {
        _pending.value = chatId
    }

    fun consume(chatId: Long) {
        _pending.compareAndSet(chatId, null)
    }

    companion object {
        const val EXTRA_CHAT_ID = "butler.open_chat_id"
    }
}
