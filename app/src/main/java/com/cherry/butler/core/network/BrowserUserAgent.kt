package com.cherry.butler.core.network

import kotlin.random.Random

/**
 * Makes an ordinary browser User-Agent, the one Butler goes out as (see [ButlerUserAgent]).
 *
 * [pick] puts one together at random from parts real browsers ship: a family (Chrome, Firefox,
 * Safari, Edge) weighted the way the web sees them, a platform the family actually runs on,
 * and a release number from that family's last few, so no two installs need look alike and
 * none looks like something that never shipped. Each family's string follows its own shape:
 * Chrome and Edge carry the frozen `10_15_7` Mac and `x.0.0.0` minor, Firefox caps its Mac at
 * `10.15` and names Ubuntu on Linux, Safari is Mac only.
 *
 * With [not], the name a firewall block came back on, [pick] answers with the engine alone
 * (`AppleWebKit/… (KHTML, like Gecko)`, no browser named): Janitor's firewall turned every
 * named browser away from a phone (Safari, Firefox; 2026-10-08) and let the same request through
 * the moment the browser name was cut off the end. Should the engine name be blocked too, a
 * named browser of another family is tried.
 *
 * Release numbers are as of October 2026; bump [latest] with releases, nothing else changes.
 */
object BrowserUserAgent {
    enum class Family { Chrome, Firefox, Safari, Edge, WebKit }

    private enum class Platform { Windows, Mac, Linux }

    /** The newest major of each family; a pick is this or one of the few before it. */
    private val latest = mapOf(
        Family.Chrome to 155,
        Family.Firefox to 157,
        Family.Edge to 155,
    )
    private const val BEHIND = 3

    /** Safari numbers its own way; the releases still in use. */
    private val safariVersions = listOf("18.6", "26.0", "26.1")

    /** Roughly the web's share, so a pick is usually Chrome and sometimes the rest. */
    private val familyWeights = listOf(
        Family.Chrome to 60,
        Family.Firefox to 15,
        Family.Edge to 15,
        Family.Safari to 10,
    )

    private val platformsOf = mapOf(
        Family.Chrome to listOf(Platform.Windows, Platform.Windows, Platform.Windows, Platform.Mac, Platform.Linux),
        Family.Firefox to listOf(Platform.Windows, Platform.Windows, Platform.Mac, Platform.Linux, Platform.Linux),
        Family.Edge to listOf(Platform.Windows, Platform.Windows, Platform.Windows, Platform.Mac),
        Family.Safari to listOf(Platform.Mac),
    )

    /**
     * A browser at random. With [not], what to try after a firewall block on [not]: the engine
     * alone after a named browser, a named browser of another family after the engine.
     */
    fun pick(not: String? = null, random: Random = Random.Default): String {
        val avoid = not?.let(::familyOf)
        if (not != null && avoid != Family.WebKit) return engine(random)
        val family = draw(familyWeights.filter { (f, _) -> f != avoid }, random)
        val platform = platformsOf.getValue(family).random(random)
        return build(family, platform, random)
    }

    /** The engine alone, as a WebView or an embedded browser says it. */
    private fun engine(random: Random): String = when (listOf(Platform.Windows, Platform.Mac, Platform.Linux).random(random)) {
        Platform.Mac -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)"
        Platform.Windows -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
        Platform.Linux -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)"
    }

    private fun build(family: Family, platform: Platform, random: Random): String {
        fun major(f: Family) = latest.getValue(f) - random.nextInt(BEHIND + 1)
        return when (family) {
            Family.Chrome -> "Mozilla/5.0 (${chromiumOs(platform)}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${major(Family.Chrome)}.0.0.0 Safari/537.36"
            Family.Edge -> {
                // Edge tracks Chromium's major; both numbers are the same release.
                val v = major(Family.Edge)
                "Mozilla/5.0 (${chromiumOs(platform)}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$v.0.0.0 Safari/537.36 Edg/$v.0.0.0"
            }
            Family.Firefox -> {
                val v = major(Family.Firefox)
                val os = when (platform) {
                    Platform.Windows -> "Windows NT 10.0; Win64; x64"
                    Platform.Mac -> "Macintosh; Intel Mac OS X 10.15"
                    Platform.Linux -> if (random.nextBoolean()) "X11; Linux x86_64" else "X11; Ubuntu; Linux x86_64"
                }
                "Mozilla/5.0 ($os; rv:$v.0) Gecko/20100101 Firefox/$v.0"
            }
            Family.Safari -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/${safariVersions.random(random)} Safari/605.1.15"
            Family.WebKit -> engine(random)
        }
    }

    private fun chromiumOs(platform: Platform) = when (platform) {
        Platform.Windows -> "Windows NT 10.0; Win64; x64"
        Platform.Mac -> "Macintosh; Intel Mac OS X 10_15_7"
        Platform.Linux -> "X11; Linux x86_64"
    }

    private fun <T> draw(weighted: List<Pair<T, Int>>, random: Random): T {
        var n = random.nextInt(weighted.sumOf { it.second })
        for ((item, weight) in weighted) {
            n -= weight
            if (n < 0) return item
        }
        return weighted.last().first
    }

    /** Which browser [userAgent] says it is, or null for a string that isn't one of ours. */
    fun familyOf(userAgent: String): Family? = when {
        "Edg/" in userAgent -> Family.Edge
        "Firefox/" in userAgent -> Family.Firefox
        "Chrome/" in userAgent -> Family.Chrome
        "Safari/" in userAgent -> Family.Safari
        "AppleWebKit/" in userAgent -> Family.WebKit
        else -> null
    }
}
