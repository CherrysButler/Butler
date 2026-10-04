package com.cherry.butler.core.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.CharacterSort
import com.cherry.butler.core.model.NsfwMode
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.model.SpecialMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user was, kept on disk so leaving the app never sends them back to the start.
 *
 * Android restores the screen on its own when it only paused the app. This covers the
 * rest: the phone's battery manager stopping the app outright, or the user swiping it
 * away. On such a cold start the app reopens on the last tab and, if it was left less
 * than [DETAIL_FOR_MS] ago, on the chat or character page that was open.
 *
 * Also keeps unsent chat drafts per chat, so a half-written line survives the same.
 */
@Singleton
class LastPlace @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_place", Context.MODE_PRIVATE)

    /** Set by the activity: true only when it starts with no saved state of its own. */
    @Volatile
    private var coldStart = false

    /** True until the first screen of this process has started. */
    private var firstInProcess = true

    /**
     * [cold]: the activity started with no saved state. The first activity of a new process
     * counts too, saved state or not: when Android kills Butler in the background and
     * recreates it later, the back stack doesn't come back with it.
     */
    fun markColdStart(cold: Boolean) {
        coldStart = cold || firstInProcess
        firstInProcess = false
    }

    data class Place(val tab: String, val detail: String?)

    /** The place to reopen, once per cold start; null otherwise. */
    fun takeRestore(now: Long = System.currentTimeMillis()): Place? {
        if (!coldStart) return null
        coldStart = false
        val tab = prefs.getString(KEY_TAB, null) ?: return null
        val detail = prefs.getString(KEY_DETAIL, null)
            ?.takeIf { now - prefs.getLong(KEY_AT, 0L) < DETAIL_FOR_MS }
        return Place(tab, detail)
    }

    /** Records the current tab and the pushed chat or character route on top of it, if any. */
    fun record(tab: String?, detail: String?, now: Long = System.currentTimeMillis()) {
        prefs.edit().apply {
            if (tab != null) putString(KEY_TAB, tab)
            if (detail != null) putString(KEY_DETAIL, detail) else remove(KEY_DETAIL)
            putLong(KEY_AT, now)
        }.apply()
    }

    fun draft(chatId: Long): String = prefs.getString(draftKey(chatId), null).orEmpty()

    fun saveDraft(chatId: Long, text: String) {
        prefs.edit().apply {
            if (text.isBlank()) remove(draftKey(chatId)) else putString(draftKey(chatId), text)
        }.apply()
    }

    /** The Browse search, sort, filter and tags as last left. */
    fun browseQuery(): BrowseQuery = BrowseQuery(
        search = prefs.getString(KEY_SEARCH, null)?.takeIf { it.isNotBlank() },
        sort = CharacterSort.entries.firstOrNull { it.wire == prefs.getString(KEY_SORT, null) } ?: CharacterSort.Popular,
        // From v3 on, "no special" (a Popular/Latest/Relevance pick) is saved as "none";
        // anything older falls back to the Trending 24h default.
        special = when (val saved = prefs.getString(KEY_SPECIAL, null).takeIf { prefs.getInt(KEY_BROWSE_VERSION, 1) >= 3 }) {
            null -> SpecialMode.Trending24
            "none" -> null
            else -> SpecialMode.entries.firstOrNull { it.wire == saved } ?: SpecialMode.Trending24
        },
        // Before v2 the saved mode was only ever Butler's old SFW default, never a choice:
        // ignore it once so Browse opens on Janitor's default, All.
        mode = prefs.getString(KEY_MODE, null)
            ?.takeIf { prefs.getInt(KEY_BROWSE_VERSION, 1) >= 2 }
            ?.let { saved -> NsfwMode.entries.firstOrNull { it.wire == saved } } ?: NsfwMode.All,
        source = BrowseSource.entries.firstOrNull { it.name == prefs.getString(KEY_SOURCE, null) } ?: BrowseSource.All,
        tagIds = prefs.getString(KEY_TAGS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty(),
        minMessages = prefs.getLong(KEY_MIN_MESSAGES, 0L),
        minTokens = prefs.getInt(KEY_MIN_TOKENS, 0),
        proxyOnly = prefs.getBoolean(KEY_PROXY_ONLY, false),
    )

    fun saveBrowseQuery(query: BrowseQuery) {
        prefs.edit()
            .putString(KEY_SEARCH, query.search.orEmpty())
            .putString(KEY_SORT, query.sort.wire)
            .putString(KEY_SPECIAL, query.special?.wire ?: "none")
            .putString(KEY_MODE, query.mode.wire)
            .putString(KEY_SOURCE, query.source.name)
            .putString(KEY_TAGS, query.tagIds.joinToString(","))
            .putLong(KEY_MIN_MESSAGES, query.minMessages)
            .putInt(KEY_MIN_TOKENS, query.minTokens)
            .putBoolean(KEY_PROXY_ONLY, query.proxyOnly)
            .putInt(KEY_BROWSE_VERSION, 3)
            .apply()
    }

    /** Sign-out: nothing of the last account's place or drafts stays behind. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun draftKey(chatId: Long) = "draft_$chatId"

    private companion object {
        const val KEY_TAB = "tab"
        const val KEY_DETAIL = "detail"
        const val KEY_AT = "at"
        const val KEY_SEARCH = "browse_search"
        const val KEY_SORT = "browse_sort"
        const val KEY_MODE = "browse_mode"
        const val KEY_TAGS = "browse_tags"
        const val KEY_SPECIAL = "browse_special"
        const val KEY_SOURCE = "browse_source"
        const val KEY_MIN_MESSAGES = "browse_min_messages"
        const val KEY_MIN_TOKENS = "browse_min_tokens"
        const val KEY_BROWSE_VERSION = "browse_version"
        const val KEY_PROXY_ONLY = "browse_proxy_only"
        /** After this long away, a cold start reopens the tab but not the chat on top of it. */
        const val DETAIL_FOR_MS = 12L * 60 * 60 * 1000
    }
}

/** Lets the navigation shell reach [LastPlace] without threading it through every screen. */
val LocalLastPlace = staticCompositionLocalOf<LastPlace?> { null }
