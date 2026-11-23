package com.hoisly.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeSpringTest {
    @Test
    fun startsAtZeroBeforeItsDelay() {
        assertEquals(0f, springStep(-0.1f, 0.68f, 180f), 0f)
        assertEquals(0f, springStep(0f, 0.68f, 180f), 1e-6f)
    }

    @Test
    fun overshootsOnceThenSettlesAtOne() {
        var peak = 0f
        var t = 0f
        while (t < 0.6f) {
            peak = maxOf(peak, springStep(t, 0.68f, 180f))
            t += 0.004f
        }
        assertTrue("expected a soft overshoot, was $peak", peak in 1.03f..1.20f)
        assertEquals(1f, springStep(1.2f, 0.68f, 180f), 0.01f)
    }

    @Test
    fun criticalDampingNeverOvershoots() {
        var t = 0f
        while (t < 2f) {
            assertTrue(springStep(t, 1f, 180f) <= 1f + 1e-4f)
            t += 0.01f
        }
    }

    @Test
    fun entranceIsSettledBeforeTheClockEnds() {
        // Latest item: the spread delay plus the spring settle must fit inside the master clock.
        assertEquals(1f, springStep((WAKE_TOTAL_MS - 15f) / 1000f, WAKE_DAMPING, WAKE_STIFFNESS), 0.005f)
    }
}
