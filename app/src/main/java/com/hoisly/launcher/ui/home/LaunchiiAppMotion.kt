package com.hoisly.launcher.ui.home

import androidx.compose.animation.core.Easing
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motion constants ported from Launchii's app-launch Zoom In implementation.
 * Kept narrowly scoped to opening/closing app windows; no folder, widget, drag or page motion
 * is imported into Hoisly.
 */
object LaunchiiAppMotion {
    const val OPEN_STIFFNESS = 300f
    const val OPEN_DAMPING = 0.84f
    const val OPEN_BASE_DURATION_MS = 650
    const val CLOSE_BASE_DURATION_MS = 450
    const val HOME_ZOOM = 1.15f
    const val CARD_END_SCALE = 1.20f
    const val HOME_DIM = 0.25f
    const val ICON_RADIUS = 0.30f
    const val SPLASH_ICON_DP = 160f
    const val WINDOW_CORNER_DP = 28f

    /** Same damped spring curve used by Launchii's Zoom In card. */
    fun spring(durationMs: Long): Easing {
        val w = sqrt(OPEN_STIFFNESS)
        val wd = w * sqrt(1f - OPEN_DAMPING * OPEN_DAMPING)
        return Easing { fraction ->
            if (fraction >= 1f) 1f
            else {
                val t = fraction * durationMs / 1000f
                (1f - exp((-OPEN_DAMPING * w * t).toDouble()).toFloat() *
                    (cos((wd * t).toDouble()).toFloat() +
                        (OPEN_DAMPING * w / wd) * sin((wd * t).toDouble()).toFloat()))
            }
        }
    }
}
