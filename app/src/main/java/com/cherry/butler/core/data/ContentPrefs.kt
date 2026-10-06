package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `allow_mobile_nsfw`, sent in every generation's `userConfig` as the website sends it
 * (captured 2026-10-07: `false`, and the account's config had no such key). Kept on the
 * phone, off by default like the website's.
 */
@Singleton
class ContentPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _allowMobileNsfw = MutableStateFlow(prefs.getBoolean(KEY_NSFW, false))
    val allowMobileNsfw: StateFlow<Boolean> = _allowMobileNsfw.asStateFlow()

    fun setAllowMobileNsfw(on: Boolean) {
        _allowMobileNsfw.value = on
        prefs.edit().putBoolean(KEY_NSFW, on).apply()
    }

    private companion object {
        const val KEY_NSFW = "allow_mobile_nsfw"
    }
}
