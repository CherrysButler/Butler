package com.cherry.butler.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BrowserUserAgentTest {

    private val shapes = mapOf(
        BrowserUserAgent.Family.Chrome to Regex("""Mozilla/5\.0 \((Windows NT 10\.0; Win64; x64|Macintosh; Intel Mac OS X 10_15_7|X11; Linux x86_64)\) AppleWebKit/537\.36 \(KHTML, like Gecko\) Chrome/15[2-5]\.0\.0\.0 Safari/537\.36"""),
        BrowserUserAgent.Family.Edge to Regex("""Mozilla/5\.0 \((Windows NT 10\.0; Win64; x64|Macintosh; Intel Mac OS X 10_15_7)\) AppleWebKit/537\.36 \(KHTML, like Gecko\) Chrome/(15[2-5])\.0\.0\.0 Safari/537\.36 Edg/\2\.0\.0\.0"""),
        BrowserUserAgent.Family.Firefox to Regex("""Mozilla/5\.0 \((Windows NT 10\.0; Win64; x64|Macintosh; Intel Mac OS X 10\.15|X11; Linux x86_64|X11; Ubuntu; Linux x86_64); rv:(15[4-7])\.0\) Gecko/20100101 Firefox/\2\.0"""),
        BrowserUserAgent.Family.Safari to Regex("""Mozilla/5\.0 \(Macintosh; Intel Mac OS X 10_15_7\) AppleWebKit/605\.1\.15 \(KHTML, like Gecko\) Version/(18\.6|26\.0|26\.1) Safari/605\.1\.15"""),
    )

    @Test
    fun `every pick is a browser that could have shipped`() {
        val seen = mutableMapOf<BrowserUserAgent.Family, Int>()
        repeat(500) { seed ->
            val ua = BrowserUserAgent.pick(random = Random(seed))
            val family = BrowserUserAgent.familyOf(ua)
            val shape = shapes[family] ?: throw AssertionError("not a named browser: $ua")
            assertTrue(ua, shape.matches(ua))
            seen[family!!] = (seen[family] ?: 0) + 1
        }
        // Every family turns up, and Chrome most of all, as on the web.
        assertEquals(shapes.keys, seen.keys)
        assertTrue(seen.toString(), seen.getValue(BrowserUserAgent.Family.Chrome) > seen.filterKeys { it != BrowserUserAgent.Family.Chrome }.values.max())
    }

    @Test
    fun `picks vary`() {
        val distinct = (0 until 300).map { BrowserUserAgent.pick(random = Random(it)) }.toSet()
        assertTrue("only ${distinct.size} different strings", distinct.size >= 25)
    }

    @Test
    fun `after a block comes the engine alone, after that another browser`() {
        repeat(100) { seed ->
            val ua = BrowserUserAgent.pick(random = Random(seed))
            val engine = BrowserUserAgent.pick(not = ua, random = Random(seed))
            assertEquals(engine, BrowserUserAgent.Family.WebKit, BrowserUserAgent.familyOf(engine))
            assertTrue(engine, engine.endsWith("(KHTML, like Gecko)"))
            val next = BrowserUserAgent.pick(not = engine, random = Random(seed))
            val family = BrowserUserAgent.familyOf(next)
            assertTrue(next, family != null && family != BrowserUserAgent.Family.WebKit)
        }
    }

    @Test
    fun `family is read off the string`() {
        assertEquals(BrowserUserAgent.Family.Edge, BrowserUserAgent.familyOf("Mozilla/5.0 (Windows NT 10.0) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0"))
        assertEquals(BrowserUserAgent.Family.Safari, BrowserUserAgent.familyOf("Mozilla/5.0 (Macintosh) Version/26.1 Safari/605.1.15"))
        assertEquals(BrowserUserAgent.Family.WebKit, BrowserUserAgent.familyOf("Mozilla/5.0 (Macintosh) AppleWebKit/605.1.15 (KHTML, like Gecko)"))
        assertEquals(null, BrowserUserAgent.familyOf("Butler/0.3.0"))
    }
}
