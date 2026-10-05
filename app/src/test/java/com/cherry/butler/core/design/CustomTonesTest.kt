package com.cherry.butler.core.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomTonesTest {

    @Test
    fun `the default custom look is dark and keeps Butler red`() {
        val t = customTones(CustomColors.Default)
        assertFalse(t.isLight)
        assertEquals(Color(0xFFEA5A4F), t.red)
        assertFalse(CustomColors.Default.accentAdjusted())
    }

    @Test
    fun `a pale ground makes a light look with dark text`() {
        val t = customTones(CustomColors(accent = 0xFF2B63B5, ground = 0xFFF4EEDF))
        assertTrue(t.isLight)
        assertTrue(contrast(t.textHigh, t.ground) >= 7f)
    }

    @Test
    fun `an accent too close to the ground is moved until it reads`() {
        val colors = CustomColors(accent = 0xFF30333A, ground = 0xFF2B2E33)
        val t = customTones(colors)
        assertTrue(colors.accentAdjusted())
        assertTrue(contrast(t.red, t.ground) >= 3f)
    }

    @Test
    fun `text stays readable on a saturated ground`() {
        for (ground in listOf(0xFF3B0A57, 0xFF0B3D2E, 0xFF7A1F1F, 0xFF808080)) {
            val t = customTones(CustomColors(accent = 0xFFFFC857, ground = ground))
            assertTrue(contrast(t.textHigh, t.ground) >= 4.5f, "ground ${ground.toString(16)}")
            assertTrue(contrast(t.red, t.ground) >= 3f, "accent on ${ground.toString(16)}")
        }
    }

    @Test
    fun `a colour set by hand wins and is not nudged, but is flagged when hard to read`() {
        val colors = CustomColors.Default.copy(overrides = mapOf(CustomToken.Text.key to 0xFF33363C))
        assertEquals(Color(0xFF33363C), colors.colorOf(CustomToken.Text))
        assertTrue(colors.hardToRead(CustomToken.Text))
        assertFalse(CustomColors.Default.hardToRead(CustomToken.Text))
    }

    @Test
    fun `Auto follows the background while hand-set colours stay put`() {
        val colors = CustomColors.Default.copy(overrides = mapOf(CustomToken.Cards.key to 0xFF112233))
        val moved = colors.copy(ground = 0xFF3B0A57)
        assertEquals(Color(0xFF112233), moved.colorOf(CustomToken.Cards))
        assertTrue(moved.colorOf(CustomToken.Raised) != colors.colorOf(CustomToken.Raised))
    }
}
