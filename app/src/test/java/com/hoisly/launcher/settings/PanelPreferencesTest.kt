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
