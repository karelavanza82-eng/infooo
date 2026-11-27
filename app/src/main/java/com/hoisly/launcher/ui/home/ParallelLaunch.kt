package com.hoisly.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.LauncherViewModel
import kotlin.math.roundToInt

/** Most cards alive at once; the oldest is dropped first so a tap storm cannot pile up. */
const val PARALLEL_MAX_CARDS = 10

/** After the last tap, this long passes before that app really opens. */
const val PARALLEL_IDLE_MS = 380L

private const val PARALLEL_CARD_MS = 620
private val ParallelEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** One icon tap: a card that grows out of the icon at [bounds] (window pixels) and fades away. */
@Immutable
data class ParallelCard(
    val id: Long,
    val bounds: Rect,
    val icon: ImageBitmap?,
    val color: Color,
)

/** Draws every live [ParallelCard]. Each card runs its own animation, so they overlap freely. */
@Composable
fun ParallelLaunchOverlay(vm: LauncherViewModel) {
    val cards by vm.parallelCards.collectAsStateWithLifecycle()
    if (cards.isEmpty()) return
    Box(Modifier.fillMaxSize()) {
        cards.forEach { card ->
            key(card.id) {
                ParallelCardLayer(card) { vm.removeParallelCard(card.id) }
            }
        }
    }
}

@Composable
private fun ParallelCardLayer(card: ParallelCard, onDone: () -> Unit) {
    val progress = remember(card.id) { Animatable(0f) }
    LaunchedEffect(card.id) {
        progress.animateTo(1f, tween(PARALLEL_CARD_MS, easing = ParallelEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) { drawParallelCard(card, progress.value) }
}

internal fun DrawScope.drawParallelCard(card: ParallelCard, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val bounds = card.bounds
    // Fades in quickly, then fades out for the rest of the time.
    val alpha = if (p < 0.25f) p / 0.25f else 1f - (p - 0.25f) / 0.75f
    if (alpha <= 0f) return

    val targetSize = Size(size.width * 0.44f, size.height * 0.30f)
    val width = lerp(bounds.width, targetSize.width, p)
    val height = lerp(bounds.height, targetSize.height, p)
    // Drifts a third of the way from the icon toward the middle of the screen while it grows.
    val center = Offset(
        lerp(bounds.center.x, (bounds.center.x + size.width / 2f) / 2f, p),
        lerp(bounds.center.y, (bounds.center.y + size.height / 2f) / 2f, p),
    )
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
    val corner = lerp(bounds.width * 0.2237f, 28f * density, p)
    val fill = androidx.compose.ui.graphics.lerp(card.color, Color.White, 0.55f)

    drawRoundRect(fill.copy(alpha = alpha * 0.92f), topLeft, Size(width, height), CornerRadius(corner))

    val icon = card.icon ?: return
    val side = lerp(bounds.width, bounds.width * 2.2f, p)
    drawImage(
        icon,
        dstOffset = IntOffset((center.x - side / 2f).roundToInt(), (center.y - side / 2f).roundToInt()),
        dstSize = IntSize(side.roundToInt(), side.roundToInt()),
        alpha = alpha,
    )
}

/** Settings preview: three cards from neighbouring icons, started a little apart so they overlap. */
fun DrawScope.drawParallelPreview(bounds: Rect, icon: ImageBitmap?, color: Color, progress: Float) {
    val step = bounds.width * 1.25f
    for (i in 0..2) {
        val q = (progress * 1.7f - i * 0.28f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) continue
        val shifted = Rect(bounds.left + (i - 1) * step, bounds.top, bounds.right + (i - 1) * step, bounds.bottom)
        drawParallelCard(ParallelCard(-1L - i, shifted, icon, color), q)
    }
}
