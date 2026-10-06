package com.cherry.butler.core.model

/**
 * What the browse screen is currently asking for, in the official app's terms (verified
 * against the signed-in API 2026-10-03, docs/JANITOR_API.md §5):
 *
 * - the "Popular" dropdown is [sort]: Popular, Latest, Relevance
 * - the "Trending" dropdown and "Hidden Gems" are [special] (`special_mode`), which the
 *   server applies instead of the sort
 * - the filter sheet's View is [mode], Source is [source], plus [tagIds]
 * - "Messages ≥" and "Tokens ≥" are [minMessages] / [minTokens]: the server ignores every
 *   name tried for them, and the official app filters with `applyMessagesFilter` /
 *   `applyTokensFilter`, so Butler filters the fetched rows on the phone too
 *
 * The server half ([serverKey]) is the mirror's cache key: changing it invalidates the
 * mirrored page order, because the server's ordering is what is cached.
 */
data class BrowseQuery(
    val search: String? = null,
    val sort: CharacterSort = CharacterSort.Popular,
    /** Trending 24h by default, as the user prefers. */
    val special: SpecialMode? = SpecialMode.Trending24,
    /** All by default, as the official app shows a signed-in user. */
    val mode: NsfwMode = NsfwMode.All,
    val source: BrowseSource = BrowseSource.All,
    val tagIds: List<Int> = emptyList(),
    /**
     * Creators' own tags (`custom_tags[]`), lowercase. Each one narrows the results further
     * (all must match), as on the website (captured 2026-10-07).
     */
    val customTags: List<String> = emptyList(),
    val minMessages: Long = 0,
    val minTokens: Int = 0,
    /** The filter sheet's Proxy switch: only characters whose creator allows proxies. Applied on the phone. */
    val proxyOnly: Boolean = false,
) {
    /**
     * The part the server sees; the minimums are applied on the phone. A search covers all
     * characters: `special_mode` would narrow it to that day's trending list and find nothing.
     */
    fun server(): BrowseQuery = copy(
        minMessages = 0,
        minTokens = 0,
        proxyOnly = false,
        special = if (search.isNullOrBlank()) special else null,
    )

    /** How many filter-sheet settings differ from the defaults (for the filter key's dot). */
    val filterCount: Int
        get() = listOf(mode != NsfwMode.All, source != BrowseSource.All, minMessages > 0, minTokens > 0, proxyOnly).count { it }

    /** Stable identity for the Room `remote_keys` table and mirror partitioning. */
    val cacheKey: String
        get() = buildString {
            append(special?.wire ?: sort.wire).append('|').append(mode.wire)
            append('|').append(source.name)
            append('|').append(search?.trim()?.lowercase().orEmpty())
            append('|').append(tagIds.sorted().joinToString(","))
            if (customTags.isNotEmpty()) append('|').append(customTags.sorted().joinToString(","))
        }
}

/** The "Popular" dropdown. Wire values are exact; an invalid one is a 400 listing the enum. */
enum class CharacterSort(val wire: String, val label: String) {
    Popular("popular", "Popular"),
    Latest("latest", "Latest"),
    Relevance("relevance", "Relevance"),
}

/** `special_mode`: takes precedence over `sort`. Server enum: trending, newcomer, trending24, hidden_gems. */
enum class SpecialMode(val wire: String, val label: String) {
    Trending24("trending24", "24h"),
    TrendingWeek("trending", "Weekly"),
    HiddenGems("hidden_gems", "Hidden Gems"),
}

/**
 * The View filter. The parameter is `mode`, **not** `nsfw` (`nsfw=` is silently ignored).
 * "Limited only" is SFW: Janitor tags unrestricted characters "Limitless".
 */
enum class NsfwMode(val wire: String, val label: String) {
    All("all", "All"),
    Sfw("sfw", "Limited only"),
}

/** The Source filter: `following=true` / `favorites=true`, both need a signed-in caller. */
enum class BrowseSource(val label: String) {
    All("All"),
    Following("Following"),
    Favorites("Favorites"),
}
