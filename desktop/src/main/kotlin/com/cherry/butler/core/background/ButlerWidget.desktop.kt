package com.cherry.butler.core.background

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** There is no home-screen widget on a PC; nothing freezes Butler there either. */
class ButlerWidget

@Singleton
class ButlerWidgets @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile var working: BackgroundReplies.Working? = null
    fun isPlaced(): Boolean = false
    fun requestPin(): Boolean = false
    fun refresh(force: Boolean = false) = Unit
}
