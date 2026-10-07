package com.cherry.butler.core.network

import kotlin.random.Random

/**
 * Ordinary browser User-Agents, one of which Butler goes out as (see [ButlerUserAgent]).
 *
 * The pool is the current desktop browsers on the platforms they run on, with their version
 * numbers as of October 2026: a request from any of them is what Janitor's website sees all
 * day. [pick] draws one at random; with [not], one from a different browser family than that
 * string's, so a retry after a firewall block is a real change and not the same name again.
 * Bump the version constants with releases; nothing else needs to change.
 */
object BrowserUserAgent {
    private const val CHROME = 155
    private const val FIREFOX = 157
    private const val EDGE = 155
    private const val SAFARI = "26.1"

    private const val WINDOWS = "Windows NT 10.0; Win64; x64"
    private const val MAC = "Macintosh; Intel Mac OS X 10_15_7"
    private const val LINUX = "X11; Linux x86_64"

    enum class Family { Chrome, Firefox, Safari, Edge }

    private val pool: List<Pair<Family, String>> = listOf(
        Family.Chrome to "Mozilla/5.0 ($WINDOWS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME.0.0.0 Safari/537.36",
        Family.Chrome to "Mozilla/5.0 ($MAC) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME.0.0.0 Safari/537.36",
        Family.Chrome to "Mozilla/5.0 ($LINUX) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME.0.0.0 Safari/537.36",
        Family.Firefox to "Mozilla/5.0 ($WINDOWS; rv:$FIREFOX.0) Gecko/20100101 Firefox/$FIREFOX.0",
        Family.Firefox to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:$FIREFOX.0) Gecko/20100101 Firefox/$FIREFOX.0",
        Family.Firefox to "Mozilla/5.0 ($LINUX; rv:$FIREFOX.0) Gecko/20100101 Firefox/$FIREFOX.0",
        Family.Safari to "Mozilla/5.0 ($MAC) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/$SAFARI Safari/605.1.15",
        Family.Edge to "Mozilla/5.0 ($WINDOWS) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME.0.0.0 Safari/537.36 Edg/$EDGE.0.0.0",
    )

    /** A browser at random; with [not], one from another family than [not]'s. */
    fun pick(not: String? = null, random: Random = Random.Default): String {
        val avoid = not?.let(::familyOf)
        val choices = pool.filter { (family, _) -> family != avoid }
        return choices[random.nextInt(choices.size)].second
    }

    /** Which browser [userAgent] says it is, or null for a string that isn't one of ours. */
    fun familyOf(userAgent: String): Family? = when {
        "Edg/" in userAgent -> Family.Edge
        "Firefox/" in userAgent -> Family.Firefox
        "Chrome/" in userAgent -> Family.Chrome
        "Safari/" in userAgent -> Family.Safari
        else -> null
    }
}
