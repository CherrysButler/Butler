package com.cherry.butler.feature.browse

import com.cherry.butler.core.model.BrowseQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomTagsTest {

    @Test
    fun `a typed tag is made to look like janitor's`() {
        assertEquals("kinktober2026", BrowseViewModel.normalizeCustomTag("  #Kinktober 2026 "))
        assertNull(BrowseViewModel.normalizeCustomTag(" # "))
    }

    @Test
    fun `custom tags keep their own cached results, and stay out of the filter key's count`() {
        val plain = BrowseQuery()
        val tagged = BrowseQuery(customTags = listOf("kinktober"), tagIds = listOf(6))
        // Tags show in their own row under the sort bar, so the filter key doesn't count them.
        assertEquals(plain.filterCount, tagged.filterCount)
        assertNotEquals(plain.cacheKey, tagged.cacheKey)
        assertEquals(tagged.cacheKey, BrowseQuery(customTags = listOf("kinktober"), tagIds = listOf(6)).cacheKey)
    }
}
