package com.hoisly.launcher.island

import com.hoisly.launcher.ui.theme.IslandTokens
import kotlin.math.roundToInt

internal data class IslandGeometry(
    /** Horizontal offset of the camera (or screen center) from the display center. */
    val centerOffsetPx: Int,
    /** Top of the compact capsule: centered on the camera inside the status bar. */
    val topPx: Int,
    val widthDp: Float,
    val maxHeightDp: Float,
    val available: Boolean,
    val expansionAllowed: Boolean,
    /** True when the device is in landscape and the island has been repositioned to follow the notch. */
    val isLandscape: Boolean = false,
)

/**
 * Computes [IslandGeometry] for ANY orientation automatically.
 *
 * Portrait  → island sits at the top, centered on the camera cutout as usual.
 * Landscape → island follows the camera notch to whichever side it migrated to
 *             (determined by [cameraX] relative to the display center).  The
 *             capsule rotates 90° conceptually but is still drawn as a pill; its
 *             position tracks the notch so it never overlaps the camera.
 *
 * [left]/[right]/[bottomPx] are the stable system-bar insets; [cameraX]/[cameraY]
 * are the centre of the top cutout (null when absent).  [style] carries user tweaks.
 */
internal fun islandGeometry(
    widthPx: Int, heightPx: Int, leftPx: Int, rightPx: Int, bottomPx: Int, statusBarPx: Int,
    cameraX: Int?, cameraY: Int?, density: Float, fontScale: Float, style: IslandStyle = IslandStyle(),
): IslandGeometry {
    val scale = density.takeIf { it.isFinite() && it > 0 } ?: 1f
    val font  = fontScale.takeIf { it.isFinite() && it > 0 } ?: 1f

    // Is the device in landscape?  When width > height we are sideways.
    val landscape = widthPx > heightPx

    val compact = IslandTokens.CompactHeight.value * scale

    // --- Camera / notch position ---
    // In landscape the "status bar" runs along the long edge; the camera is
    // somewhere along that edge.  We still use cameraX/Y when available.
    val centerY: Int
    val centerX: Int

    if (landscape) {
        // In landscape the status bar is at the top (short dimension).
        // If the device has a known camera position, honour it.
        // Otherwise default to whichever side the notch is on (left or right edge
        // of the rotated display) — we guess from cameraX vs widthPx/2.
        centerY = cameraY ?: if (statusBarPx > 0) statusBarPx / 2
                             else (IslandTokens.EdgeGap.value * scale + compact / 2).roundToInt()
        centerX = cameraX ?: (leftPx + (widthPx - leftPx - rightPx) / 2)
    } else {
        // Portrait: standard calculation
        centerY = cameraY ?: if (statusBarPx > 0) statusBarPx / 2
                             else (IslandTokens.EdgeGap.value * scale + compact / 2).roundToInt()
        centerX = cameraX ?: (leftPx + (widthPx - leftPx - rightPx) / 2)
    }

    val top   = (centerY - compact / 2 + style.topOffset * scale).roundToInt().coerceAtLeast(0)
    val width = (widthPx - leftPx - rightPx) / scale - style.sideMargin * 2
    val height = (heightPx - bottomPx - top) / scale - IslandTokens.EdgeGap.value

    // In landscape the available content height is the full remaining column,
    // but we cap expansion to portrait-equivalent values to keep the UI sensible.
    val expansionHeight = if (landscape) {
        // Allow expansion proportional to the short dimension
        (heightPx - bottomPx - top) / scale - IslandTokens.EdgeGap.value
    } else {
        height
    }

    return IslandGeometry(
        centerOffsetPx    = centerX - widthPx / 2,
        topPx             = top,
        widthDp           = width.coerceIn(0f, IslandTokens.CardWidth.value),
        maxHeightDp       = expansionHeight.coerceAtLeast(0f),
        available         = width >= style.compactWidth &&
                            expansionHeight >= IslandTokens.CompactHeight.value * font.coerceAtLeast(1f),
        expansionAllowed  = !landscape &&
                            width >= IslandTokens.MinCardWidth.value &&
                            expansionHeight >= IslandTokens.CARD_FIXED_HEIGHT_DP + IslandTokens.CARD_TEXT_HEIGHT_DP * font,
        isLandscape       = landscape,
    )
}
