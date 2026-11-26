package com.hoisly.launcher.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StatusBarPreferencesTest {
    @Test
    fun emptyStoreIsOffWithDefaults() {
        assertEquals(StatusBarPreferences(), StatusBarPreferences.fromStored(emptyMap<String, Any>()))
        assertFalse(StatusBarPreferences.fromStored(emptyMap<String, Any>()).enabled)
    }

    @Test
    fun roundTripsEveryValue() {
        val custom = StatusBarPreferences(
            enabled = true, colorMode = StatusBarColorMode.White, showSilent = false, showSignal = false,
            showNetworkLabel = false, showWifi = false, showBattery = false, hideInLandscape = false, hideSystemIcons = true, hideSystemStatusBar = true,
            scale = 1.2f, sidePadding = 30f, verticalOffset = -4f, clockWeight = 700,
            batteryTheme = 4, wifiTheme = 3, signalTheme = 1, clockTheme = 0, airplaneTheme = 4,
        )
        assertEquals(custom, StatusBarPreferences.fromStored(custom.storedValues()))
    }

    @Test
    fun invalidValuesFallBackAndNumbersAreClamped() {
        val parsed = StatusBarPreferences.fromStored(
            mapOf("sb_color_mode" to "Neon", "sb_scale" to 9f, "sb_side_padding" to Float.NaN, "sb_clock_weight" to 650, "sb_enabled" to "yes"),
        )
        assertEquals(StatusBarColorMode.Auto, parsed.colorMode)
        assertEquals(StatusBarPreferences.SCALE_RANGE.endInclusive, parsed.scale)
        assertEquals(StatusBarPreferences.DEFAULT_SIDE_PADDING, parsed.sidePadding)
        assertEquals(StatusBarPreferences.DEFAULT_CLOCK_WEIGHT, parsed.clockWeight)
        assertFalse(parsed.enabled)
    }
}
