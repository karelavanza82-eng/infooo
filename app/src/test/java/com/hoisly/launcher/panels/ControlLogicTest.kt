package com.hoisly.launcher.panels

import com.hoisly.launcher.panels.control.brightnessToFraction
import com.hoisly.launcher.panels.control.fractionToBrightness
import com.hoisly.launcher.panels.control.fractionToVolume
import com.hoisly.launcher.panels.control.volumeToFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlLogicTest {
    @Test
    fun rightPartOfTheEdgeOpensTheControlCenter() {
        assertEquals(Panel.Control, panelForEdgeTouch(900f, 1000f, controlEnabled = true, notificationsEnabled = true))
        assertEquals(Panel.Notifications, panelForEdgeTouch(100f, 1000f, controlEnabled = true, notificationsEnabled = true))
    }

    @Test
    fun theOnlyEnabledPanelOwnsTheWholeEdge() {
        assertEquals(Panel.Control, panelForEdgeTouch(50f, 1000f, controlEnabled = true, notificationsEnabled = false))
        assertEquals(Panel.Notifications, panelForEdgeTouch(950f, 1000f, controlEnabled = false, notificationsEnabled = true))
        assertNull(panelForEdgeTouch(500f, 1000f, controlEnabled = false, notificationsEnabled = false))
    }

    @Test
    fun brightnessNeverReachesZero() {
        assertEquals(1, fractionToBrightness(0f))
        assertEquals(255, fractionToBrightness(2f))
        assertEquals(0.5f, brightnessToFraction(127), 0.01f)
    }

    @Test
    fun volumeMapsBothWays() {
        assertEquals(8, fractionToVolume(0.5f, 15))
        assertEquals(0f, volumeToFraction(3, 0), 0f)
        assertEquals(1f, volumeToFraction(20, 15), 0f)
    }
}
