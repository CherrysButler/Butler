package com.cherry.butler.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoTimeTest {

    @Test
    fun `message dialect with Z`() {
        assertEquals(1_790_127_611_265L, IsoTime.parseMillis("2026-09-23T01:40:11.265Z"))
    }

    @Test
    fun `character dialect with offset and microseconds`() {
        assertEquals(1_685_840_117_988L, IsoTime.parseMillis("2023-06-04T00:55:17.988502+00:00"))
    }

    @Test
    fun `first_published_at dialect with no zone is read as UTC`() {
        assertEquals(1_685_840_117_988L, IsoTime.parseMillis("2023-06-04T00:55:17.988502"))
    }

    @Test
    fun `garbage and blanks are null not exceptions`() {
        assertNull(IsoTime.parseMillis(null))
        assertNull(IsoTime.parseMillis(""))
        assertNull(IsoTime.parseMillis("yesterday"))
    }

    @Test
    fun `format round-trips the Z dialect`() {
        assertEquals("2026-09-23T01:40:11.265Z", IsoTime.format(1_790_127_611_265L))
    }
}
