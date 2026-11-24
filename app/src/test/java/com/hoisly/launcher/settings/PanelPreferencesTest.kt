package com.hoisly.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PanelPreferencesTest {
    @Test
    fun missingValuesKeepTheNotificationCenterOff() {
        val prefs = PanelPreferences.fromStored(emptyMap<String, Any>())
        assertFalse(prefs.notificationCenter)
        assertFalse(prefs.hideNotificationContent)
    }

    @Test
    fun wronglyTypedValuesFallBackToDefaults() {
        val prefs = PanelPreferences.fromStored(mapOf("panels_notification_center" to "yes"))
        assertFalse(prefs.notificationCenter)
    }

    @Test
    fun storedValuesRoundTrip() {
        val original = PanelPreferences(notificationCenter = true, hideNotificationContent = true)
        assertEquals(original, PanelPreferences.fromStored(original.storedValues()))
    }

    @Test
    fun panelGesturesSurviveTheCodec() {
        listOf(HomeAction.NotificationCenter).forEach {
            assertEquals(it, HomeActionCodec.decode(HomeActionCodec.encode(it)))
        }
    }
}

class PanelPreferencesLockScreenTest {
    @Test
    fun lockScreenValuesRoundTrip() {
        val original = PanelPreferences(
            controlCenter = false, lockClockWeight = 3, lockClockScale = 1.2f, lockClockCentered = true,
            lockShowBattery = false, lockShowNotifications = false, lockWallpaper = false, lockWallpaperBlur = 0.8f,
        )
        assertEquals(original, PanelPreferences.fromStored(original.storedValues()))
    }

    @Test
    fun patternHashRoundTripsAndDefaultsToNone() {
        assertEquals("", PanelPreferences.fromStored(emptyMap<String, Any>()).lockPattern)
        val original = PanelPreferences(lockPattern = "00ff:11aa")
        assertEquals(original, PanelPreferences.fromStored(original.storedValues()))
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val prefs = PanelPreferences.fromStored(mapOf("lock_clock_weight" to 99, "lock_clock_scale" to 5f, "lock_wallpaper_blur" to -1f))
        assertEquals(PanelPreferences.MAX_WEIGHT, prefs.lockClockWeight)
        assertEquals(PanelPreferences.MAX_SCALE, prefs.lockClockScale)
        assertEquals(0f, prefs.lockWallpaperBlur)
    }

    @Test
    fun controlCenterIsOnByDefault() {
        assertEquals(true, PanelPreferences.fromStored(emptyMap<String, Any>()).controlCenter)
    }
}
