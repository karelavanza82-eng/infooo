package com.hoisly.launcher.ui.lockscreen

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockLogicTest {
    @Test
    fun batteryPercentHandlesMissingExtras() {
        assertNull(batteryPercent(-1, 100))
        assertNull(batteryPercent(50, 0))
        assertEquals(50, batteryPercent(50, 100))
        assertEquals(100, batteryPercent(255, 100))
    }

    @Test
    fun clockWeightMapsAllSettings() {
        assertEquals(FontWeight.Thin, clockFontWeight(0))
        assertEquals(FontWeight.Light, clockFontWeight(1))
        assertEquals(FontWeight.Normal, clockFontWeight(2))
        assertEquals(FontWeight.Bold, clockFontWeight(3))
    }

    @Test
    fun clockRedrawsOnTheMinute() {
        assertEquals(60_000L, millisToNextMinute(0L))
        assertEquals(1_000L, millisToNextMinute(59_000L))
    }

    @Test
    fun unlockNeedsDistanceOrAFlick() {
        assertFalse(shouldUnlock(-100f, 2000f, 0f))
        assertTrue(shouldUnlock(-400f, 2000f, 0f))
        assertTrue(shouldUnlock(-50f, 2000f, -3000f))
        assertFalse(shouldUnlock(0f, 2000f, -3000f))
    }
}
