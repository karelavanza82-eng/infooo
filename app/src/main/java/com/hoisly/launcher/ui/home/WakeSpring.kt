package com.hoisly.launcher.ui.home

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Closed-form step response of a damped spring (mass 1, from 0 to 1, no initial velocity).
 *
 * One master clock drives every icon of the unlock entrance; each item evaluates this curve at
 * its own delayed time. That gives every item a real spring (overshoot, soft settle) without a
 * coroutine, an animation or a recomposition per item.
 */
internal fun springStep(seconds: Float, dampingRatio: Float, stiffness: Float): Float {
    if (seconds <= 0f) return 0f
    val w0 = sqrt(stiffness)
    if (dampingRatio >= 1f) {
        // Critically damped (overdamped specs are treated the same: no overshoot either way).
        val decay = exp(-w0 * seconds)
        return 1f - decay * (1f + w0 * seconds)
    }
    val wd = w0 * sqrt(1f - dampingRatio * dampingRatio)
    val decay = exp(-dampingRatio * w0 * seconds)
    return 1f - decay * (cos(wd * seconds) + dampingRatio * w0 / wd * sin(wd * seconds))
}
