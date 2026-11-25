
package com.hoisly.launcher.island

import com.hoisly.launcher.ui.theme.IslandTokens
import kotlin.math.roundToInt

internal data class IslandGeometry(
    val centerOffsetPx: Int,
    val topPx: Int,
    val widthDp: Float,
    val maxHeightDp: Float,
    val available: Boolean,
    val expansionAllowed: Boolean,
    val isLandscape: Boolean = false,
)

internal fun islandGeometry(
    widthPx: Int,
    heightPx: Int,
    leftPx: Int,
    rightPx: Int,
    bottomPx: Int,
    statusBarPx: Int,
    cameraX: Int?,
    cameraY: Int?,
    density: Float,
    fontScale: Float,
    style: IslandStyle = IslandStyle(),
): IslandGeometry {
    val scale = density.takeIf { it.isFinite() && it > 0 } ?: 1f
    val font = fontScale.takeIf { it.isFinite() && it > 0 } ?: 1f

    val landscape = widthPx > heightPx
    val compact = IslandTokens.CompactHeight.value * scale

    val centerY: Int
    val centerX: Int

    if (landscape) {
        // In landscape the camera cutout is on a side edge. Keep the island at the
        // vertical middle of that edge instead of leaving it at the old top-center.
        centerY = cameraY ?: heightPx / 2
        val sideInset = ((IslandTokens.EdgeGap.value + IslandTokens.CompactHeight.value / 2) * scale).roundToInt()
        centerX = cameraX ?: (leftPx + sideInset)
    } else {
        centerY = cameraY ?: if (statusBarPx > 0) {
            statusBarPx / 2
        } else {
            (IslandTokens.EdgeGap.value * scale + compact / 2).roundToInt()
        }

        centerX = cameraX ?: (leftPx + (widthPx - leftPx - rightPx) / 2)
    }

    val top = if (landscape) {
        // The landscape capsule is rotated: reserve its long axis vertically.
        (centerY - IslandTokens.IdleWidth.value * scale / 2f).roundToInt().coerceAtLeast(0)
    } else {
        (centerY - compact / 2 + style.topOffset * scale).roundToInt().coerceAtLeast(0)
    }

    val width = (widthPx - leftPx - rightPx) / scale -
        style.sideMargin * 2

    val height = (heightPx - bottomPx - top) / scale -
        IslandTokens.EdgeGap.value

    val expansionHeight = if (landscape) {
        (heightPx - bottomPx - top) / scale -
            IslandTokens.EdgeGap.value
    } else {
        height
    }

    return IslandGeometry(
        centerOffsetPx = centerX - widthPx / 2,
        topPx = top,
        widthDp = width.coerceIn(0f, IslandTokens.CardWidth.value),
        maxHeightDp = expansionHeight.coerceAtLeast(0f),

        available = width >= style.compactWidth &&
            expansionHeight >= IslandTokens.CompactHeight.value * font.coerceAtLeast(1f),

        // Landscape must remain compact.
        expansionAllowed = !landscape &&
            width >= IslandTokens.MinCardWidth.value &&
            expansionHeight >= IslandTokens.CARD_FIXED_HEIGHT_DP +
            IslandTokens.CARD_TEXT_HEIGHT_DP * font,

        isLandscape = landscape,
    )
}
