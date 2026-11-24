package com.hoisly.launcher.ui.lockscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hoisly.launcher.ui.theme.LocalAccent

/** Colour state of the pad, as in LockDroid: normal, warning (wrong) and valid (accepted). */
internal enum class PadState { Normal, Warning, Valid }

private val WarningColor = Color(0xFFFF453A)
private val ValidColor = Color(0xFF30D158)
private val IdleColor = Color.White.copy(alpha = .55f)

/**
 * A 3x3 swipe-lock pad (a Compose port of LockDroid's swipe lock view): drag across the dots to
 * select them in order; the line follows the finger. [onComplete] gets the selected dots when the
 * finger lifts. The parent answers with [PadState.Valid] or [PadState.Warning] and goes back to
 * [PadState.Normal] to clear the pad; input is ignored while the state is not Normal.
 */
@Composable
internal fun PatternPad(
    state: PadState,
    onComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    onChange: (List<Int>) -> Unit = {},
) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val accent = LocalAccent.current.primary
    val complete by rememberUpdatedState(onComplete)
    val change by rememberUpdatedState(onChange)

    LaunchedEffect(state) {
        if (state == PadState.Normal) {
            selected.clear()
            finger = null
        }
    }

    val active = when (state) {
        PadState.Normal -> accent
        PadState.Warning -> WarningColor
        PadState.Valid -> ValidColor
    }

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(state) {
                if (state != PadState.Normal) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    selected.clear()
                    fun touch(position: Offset) {
                        finger = position
                        val node = nodeAt(position.x, position.y, size.width.toFloat())
                        if (node != null && node !in selected) {
                            selected += node
                            change(selected.toList())
                        }
                    }
                    touch(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        touch(pointer.position)
                        pointer.consume()
                        if (!pointer.pressed) break
                    }
                    finger = null
                    if (selected.isNotEmpty()) complete(selected.toList())
                }
            },
    ) {
        val side = size.width
        val radius = side / PATTERN_SIZE * 0.2f
        if (selected.isNotEmpty()) {
            val path = Path()
            selected.forEachIndexed { index, node ->
                val (x, y) = nodeCenter(node, side)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            finger?.let { path.lineTo(it.x, it.y) }
            drawPath(
                path, active.copy(alpha = .85f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        for (node in 0 until PATTERN_SIZE * PATTERN_SIZE) {
            val (x, y) = nodeCenter(node, side)
            val center = Offset(x, y)
            val color = if (node in selected) active else IdleColor
            drawCircle(color, radius, center)
            drawCircle(color.copy(alpha = if (node in selected) .35f else .25f), radius * 1.9f, center, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
