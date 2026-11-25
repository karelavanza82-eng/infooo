package com.hoisly.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp as lerpRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.LauncherViewModel
import kotlin.math.roundToInt

/** Each tap owns an independent visual burst. */
enum class AppTransitionPhase { Opening, Closing }

@androidx.compose.runtime.Immutable
data class AppTransition(
    val id: Long,
    val phase: AppTransitionPhase,
    val bounds: Rect,
    val icon: ImageBitmap?,
    val color: Color,
)

/** Average icon colour, darkened slightly for the small popup surface. */
fun iconSurfaceColor(icon: ImageBitmap?): Color {
    val bitmap = icon?.asAndroidBitmap() ?: return Color(0xFF34343A)
    return runCatching {
        val pixel = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        androidx.compose.ui.graphics.lerp(Color(pixel).copy(alpha = 1f), Color.Black, 0.18f)
    }.getOrDefault(Color(0xFF34343A))
}

/**
 * Draws several short, non-interactive popup bursts at once. These are visual previews only:
 * they do not start Activities, hold input, or prevent the user tapping another launcher icon.
 */
@Composable
fun AppTransitionOverlay(vm: LauncherViewModel) {
    val transitions by vm.appTransitions.collectAsStateWithLifecycle()
    transitions.forEach { transition ->
        key(transition.id) {
            val progress = remember(transition.id) { Animatable(0f) }
            LaunchedEffect(transition.id) {
                progress.animateTo(1f, tween(190))
                kotlinx.coroutines.delay(65)
                progress.animateTo(1.10f, tween(105))
                vm.endAppTransition(transition.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                drawPopup(transition, progress.value.coerceIn(0f, 1.10f))
            }
        }
    }
}

private fun DrawScope.drawPopup(t: AppTransition, rawProgress: Float) {
    val p = rawProgress.coerceIn(0f, 1f)
    val iconSide = t.bounds.width.coerceAtLeast(1f)
    val popupWidth = minOf(size.width * 0.43f, 148f * density).coerceAtLeast(iconSide * 1.45f)
    val popupHeight = minOf(size.height * 0.30f, 188f * density).coerceAtLeast(iconSide * 1.7f)
    val cx = t.bounds.center.x.coerceIn(popupWidth / 2f + 8f * density, size.width - popupWidth / 2f - 8f * density)
    val cy = t.bounds.center.y.coerceIn(popupHeight / 2f + 8f * density, size.height - popupHeight / 2f - 8f * density)
    val target = Rect(cx - popupWidth / 2f, cy - popupHeight / 2f, cx + popupWidth / 2f, cy + popupHeight / 2f)
    val rect = lerpRect(t.bounds, target, p)
    val alpha = (1f - ((rawProgress - 0.72f) / 0.38f).coerceIn(0f, 1f)) * p
    val corner = lerp(iconSide * ICON_CORNER_RATIO, 28f * density, p)

    // A compact translucent card, not a fullscreen black layer.
    drawRoundRect(
        color = t.color.copy(alpha = (0.90f * alpha).coerceIn(0f, 0.90f)),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(corner),
    )
    val icon = t.icon ?: return
    val side = lerp(iconSide * 0.92f, minOf(54f * density, popupWidth * 0.42f), p)
    val center = rect.center
    drawImage(
        image = icon,
        dstOffset = IntOffset((center.x - side / 2f).roundToInt(), (center.y - side / 2f).roundToInt()),
        dstSize = IntSize(side.roundToInt().coerceAtLeast(1), side.roundToInt().coerceAtLeast(1)),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

private const val ICON_CORNER_RATIO = 0.2237f
