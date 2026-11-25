package com.hoisly.launcher.ui.home

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTransitionStateTest {
    @Test fun rapidLaunchesKeepIndependentParallelSurfaces() {
        val state = AppTransitionState()

        state.begin(Rect(10f, 10f, 90f, 90f), null)
        state.begin(Rect(110f, 10f, 190f, 90f), null)

        assertEquals(2, state.active.value.size)
        assertEquals(2, state.active.value.count { it.phase == AppTransitionPhase.Opening })
    }

    @Test fun returningHomeClosesEveryActiveSurfaceIndependently() {
        val state = AppTransitionState()
        state.begin(Rect(10f, 10f, 90f, 90f), null)
        state.begin(Rect(110f, 10f, 190f, 90f), null)

        assertTrue(state.closeOpening())
        val closing = state.active.value.filter { it.phase == AppTransitionPhase.Closing }
        assertEquals(2, closing.size)

        state.finish(closing.first().id)
        assertEquals(1, state.active.value.size)
        assertEquals(AppTransitionPhase.Closing, state.active.value.single().phase)
    }
}
