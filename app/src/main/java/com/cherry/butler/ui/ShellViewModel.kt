package com.cherry.butler.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.CommunityRepository
import com.cherry.butler.core.background.OpenChatRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app shell shows across tabs: the bell's unread dot. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val community: CommunityRepository,
    private val openChats: OpenChatRequests,
) : ViewModel() {
    val unread: StateFlow<Int> = community.unread

    /** A chat a tapped notification asked for, until the shell opens it. */
    val openChat: StateFlow<Long?> = openChats.pending

    fun openedChat(chatId: Long) = openChats.consume(chatId)

    /** Cheap: one count request, made when the tab changes. */
    fun refreshUnread() {
        viewModelScope.launch { community.refreshUnread() }
    }
}
