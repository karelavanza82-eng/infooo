package com.hoisly.launcher.ui.lockscreen

import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

/** Battery percentage from Android's level and scale extras; null when Android gave none. */
internal fun batteryPercent(level: Int, scale: Int): Int? =
    if (level < 0 || scale <= 0) null else (level * 100f / scale).roundToInt().coerceIn(0, 100)

/** Lock Screen clock weight setting (0 thin .. 3 bold) as a font weight. */
internal fun clockFontWeight(setting: Int): FontWeight = when (setting) {
    0 -> FontWeight.Thin
    1 -> FontWeight.Light
    2 -> FontWeight.Normal
    else -> FontWeight.Bold
}

/** Milliseconds until the next full minute, so the clock redraws once a minute instead of every second. */
internal fun millisToNextMinute(nowMs: Long): Long = 60_000L - (nowMs % 60_000L)

/** How far an upward drag has pulled the Lock Screen away: 0..1 of [height]. */
internal fun unlockProgress(offsetPx: Float, height: Float): Float =
    if (height <= 0f) 0f else (-offsetPx / height).coerceIn(0f, 1f)

/** A swipe up unlocks past this share of the screen height, or when flicked fast enough. */
internal const val UNLOCK_DISTANCE_SHARE: Float = 0.18f

internal fun shouldUnlock(offsetPx: Float, height: Float, velocityPxPerS: Float): Boolean =
    unlockProgress(offsetPx, height) >= UNLOCK_DISTANCE_SHARE || (offsetPx < 0f && velocityPxPerS < -1_800f)
