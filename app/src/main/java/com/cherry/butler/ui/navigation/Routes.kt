package com.cherry.butler.ui.navigation

/** Non-tab destinations. Tabs live in [TopLevelDestination]. */
object Routes {
    /** The one destination that holds all five tabs (see TabHost). */
    const val TABS = "tabs"

    const val ARG_CHARACTER_ID = "characterId"
    const val ARG_CHAT_ID = "chatId"
    const val ARG_USER_ID = "userId"

    const val CHARACTER = "character/{$ARG_CHARACTER_ID}"
    const val CHAT = "chat/{$ARG_CHAT_ID}"

    const val SETTINGS_PROXY = "settings/proxy/{proxyId}"
    const val SETTINGS_PROMPTS = "settings/prompts"
    const val SETTINGS_PROMPT = "settings/prompt/{promptId}"

    const val COMMENTS = "comments/{characterId}?name={name}"
    const val PUBLISHED = "published/{slug}"
    const val MODEL_SETTINGS = "model-settings"
    /** One settings page (a [com.cherry.butler.feature.settings.SettingsPage] name). */
    const val SETTINGS_PAGE = "settings/page/{page}"
    const val GENERATION = "settings/generation"
    const val CUSTOMIZE = "customize"
    const val NOTIFICATION_PREFS = "settings/notifications"
    const val BLOCKED = "settings/blocked"
    const val DIAGNOSTICS = "settings/diagnostics"
    const val ROUTER = "settings/router"
    const val PERSONA = "persona/{personaId}"
    const val PUBLISHED_LIST = "published-list/{characterId}?name={name}"
    const val CREATOR = "creator/{$ARG_USER_ID}"

    fun character(characterId: String) = "character/$characterId"
    fun chat(chatId: Long) = "chat/$chatId"
    fun proxy(id: String?) = "settings/proxy/${id ?: "new"}"
    fun prompt(id: String?) = "settings/prompt/${id ?: "new"}"
    fun persona(id: String) = "persona/$id"
    fun comments(characterId: String, name: String) = "comments/$characterId?name=${android.net.Uri.encode(name)}"
    fun publishedList(characterId: String, name: String) = "published-list/$characterId?name=${android.net.Uri.encode(name)}"
    fun published(slug: String) = "published/${android.net.Uri.encode(slug)}"
    fun creator(userId: String) = "creator/$userId"
    fun settingsPage(page: String) = "settings/page/$page"

    /** Whether the bottom navigation bar belongs on this route. */
    fun isTopLevel(route: String?): Boolean = route == TABS
}
