package com.zfl.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelPreferencesTest {
    @Test
    fun missingValuesKeepTheNotificationCenterOff() {
        val prefs = PanelPreferences.fromStored(emptyMap<String, Any>())
        assertTrue(prefs.controlCenter)
        assertFalse(prefs.notificationCenter)
        assertFalse(prefs.hideNotificationContent)
    }

    @Test
    fun wronglyTypedValuesFallBackToDefaults() {
        val prefs = PanelPreferences.fromStored(mapOf("panels_notification_center" to "yes", "panels_control_center" to false))
        assertFalse(prefs.notificationCenter)
        assertFalse(prefs.controlCenter)
    }

    @Test
    fun storedValuesRoundTrip() {
        val original = PanelPreferences(controlCenter = false, notificationCenter = true, hideNotificationContent = true)
        assertEquals(original, PanelPreferences.fromStored(original.storedValues()))
    }

    @Test
    fun panelGesturesSurviveTheCodec() {
        listOf(HomeAction.ControlCenter, HomeAction.NotificationCenter).forEach {
            assertEquals(it, HomeActionCodec.decode(HomeActionCodec.encode(it)))
        }
    }
}
