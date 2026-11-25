package com.hoisly.launcher.panels.control

import kotlin.math.roundToInt

/** Android's brightness setting runs 0..255; 0 would turn the panel off on some devices. */
internal const val MAX_BRIGHTNESS: Int = 255
internal const val MIN_BRIGHTNESS: Int = 1

internal fun brightnessToFraction(value: Int): Float = (value / MAX_BRIGHTNESS.toFloat()).coerceIn(0f, 1f)

internal fun fractionToBrightness(fraction: Float): Int =
    (fraction.coerceIn(0f, 1f) * MAX_BRIGHTNESS).roundToInt().coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)

internal fun volumeToFraction(level: Int, max: Int): Float =
    if (max <= 0) 0f else (level.toFloat() / max).coerceIn(0f, 1f)

internal fun fractionToVolume(fraction: Float, max: Int): Int =
    (fraction.coerceIn(0f, 1f) * max.coerceAtLeast(0)).roundToInt().coerceIn(0, max.coerceAtLeast(0))
