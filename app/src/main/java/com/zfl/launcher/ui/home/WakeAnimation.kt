package com.zfl.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zfl.launcher.settings.WakeStyle
import com.zfl.launcher.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Drives the entrance animation of the home screen after the display was off. [trigger] is
 * called when the home screen comes back (or when the launcher's own Lock Screen is swiped
 * away); every icon listening through [wakePop] restarts from hidden and pops in.
 */
@Stable
class WakeState {
    var generation by mutableIntStateOf(0)
        private set

    /** Bumped when the launcher's Lock Screen should be shown after the display was off. */
    var lockRequests by mutableIntStateOf(0)
        private set

    var style by mutableStateOf(WakeStyle.Pop)

    fun trigger() {
        if (style != WakeStyle.Off) generation++
    }

    fun requestLock() {
        lockRequests++
    }
}

val LocalWake = staticCompositionLocalOf { WakeState() }

/** Icons nearer the start of the grid come first; this caps the whole sequence. */
private const val MAX_STEPS = 40
private const val STEP_MS = 18L

/**
 * Plays the [WakeStyle] entrance on this item each time [WakeState.trigger] runs. The seed is
 * the one the jiggle uses (page * 100 + index), so the order follows the grid.
 */
fun Modifier.wakePop(seed: Int): Modifier = composed {
    val wake = LocalWake.current
    val generation = wake.generation
    val style = wake.style
    val progress = remember { Animatable(1f) }
    LaunchedEffect(generation) {
        if (generation == 0) return@LaunchedEffect
        progress.snapTo(0f)
        delay((seed % 100).coerceIn(0, MAX_STEPS) * STEP_MS)
        progress.animateTo(1f, Motion.pop())
    }
    val distance = with(LocalDensity.current) { 120.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        if (p == 1f) return@graphicsLayer
        val inverse = 1f - p
        alpha = (p * 2.5f).coerceIn(0f, 1f)
        when (style) {
            WakeStyle.Off -> Unit
            WakeStyle.Pop -> {
                val s = 0.4f + 0.6f * p
                scaleX = s
                scaleY = s
            }
            WakeStyle.Top -> {
                translationY = -inverse * distance * 2f
                val s = 0.85f + 0.15f * p
                scaleX = s
                scaleY = s
            }
            WakeStyle.Side -> {
                translationX = (if (seed % 2 == 0) -1f else 1f) * inverse * distance * 3f
                val s = 0.85f + 0.15f * p
                scaleX = s
                scaleY = s
            }
        }
    }
}
