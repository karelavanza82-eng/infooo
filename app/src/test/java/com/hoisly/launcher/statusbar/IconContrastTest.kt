package com.hoisly.launcher.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconContrastTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun lightBackgroundGetsBlackIcons() {
        assertTrue(IconContrast.blackIconsOn(IconContrast.luminance(0xFFE3DAD0.toInt()), null))
        assertTrue(IconContrast.blackIconsOn(IconContrast.luminance(white), null))
    }

    @Test
    fun darkBackgroundGetsWhiteIcons() {
        assertFalse(IconContrast.blackIconsOn(IconContrast.luminance(black), null))
        assertFalse(IconContrast.blackIconsOn(IconContrast.luminance(0xFF0B2A4A.toInt()), null))
    }

    @Test
    fun hysteresisKeepsTheCurrentColourNearTheCrossover() {
        // Contrast is equal at a luminance of about 0.179.
        assertTrue(IconContrast.blackIconsOn(0.17f, previousBlack = true))
        assertFalse(IconContrast.blackIconsOn(0.19f, previousBlack = false))
        assertFalse(IconContrast.blackIconsOn(0.05f, previousBlack = true))
        assertTrue(IconContrast.blackIconsOn(0.60f, previousBlack = false))
    }

    @Test
    fun medianIgnoresThinGlyphsAndTheCameraPill() {
        val strip = IntArray(100) { white }
        for (i in 0 until 20) strip[i] = black // 20% dark: text and island
        assertEquals(1f, IconContrast.medianLuminance(strip), 0.001f)
        assertTrue(IconContrast.blackIconsOn(IconContrast.medianLuminance(strip), null))
    }
}
