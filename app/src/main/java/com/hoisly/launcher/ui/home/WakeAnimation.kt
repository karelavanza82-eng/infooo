package com.hoisly.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
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
import com.hoisly.launcher.settings.WakeStyle
import com.hoisly.launcher.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Drives the entrance animation of the home screen after the phone was unlocked.
 *
 * When the display turns off the state is [arm]ed: every icon stays hidden, so the system's
 * unlock animation reveals an empty home screen. [trigger] runs once the phone is unlocked
 * (or when the home screen comes back); every icon listening through [wakePop] then restarts
 * from hidden and flies in.
 */
@Stable
class WakeState {
    var generation by mutableIntStateOf(0)
        private set

    /** True from the moment the display turned off until the entrance starts. */
    var armed by mutableStateOf(false)
        private set

    var style by mutableStateOf(WakeStyle.Bounce)

    fun arm() {
        if (style != WakeStyle.Off) armed = true
    }

    /** Shows the icons again without an entrance (an app is closing into its icon instead). */
    fun disarm() {
        armed = false
    }

    fun trigger() {
        armed = false
        if (style != WakeStyle.Off) generation++
    }
}

val LocalWake = staticCompositionLocalOf { WakeState() }

/** Icons nearer the start of the grid come first; this caps the whole sequence. */
private const val MAX_STEPS = 40
private const val STEP_MS = 14L

/**
 * Plays the [WakeStyle] entrance on this item each time [WakeState.trigger] runs. The seed is
 * the one the jiggle uses (page * 100 + index), so the order follows the grid.
 */
fun Modifier.wakePop(seed: Int): Modifier = composed {
    val wake = LocalWake.current
    val generation = wake.generation
    val progress = remember { Animatable(1f) }
    // The generation this item has already started from hidden; until then it stays hidden,
    // so there is no frame where the icon shows at full size before the entrance begins.
    val started = remember { mutableIntStateOf(0) }
    LaunchedEffect(generation) {
        if (generation == 0) return@LaunchedEffect
        progress.snapTo(0f)
        started.intValue = generation
        delay((seed % 100).coerceIn(0, MAX_STEPS) * STEP_MS)
        if (wake.style == WakeStyle.Bounce) progress.animateTo(1f, Motion.wake()) else progress.animateTo(1f, Motion.pop())
    }
    val distance = with(LocalDensity.current) { 120.dp.toPx() }
    graphicsLayer {
        val waiting = wake.armed || wake.generation != started.intValue
        val p = if (waiting) 0f else progress.value
        if (p == 1f) return@graphicsLayer
        val inverse = 1f - p
        alpha = (p * 2.5f).coerceIn(0f, 1f)
        when (wake.style) {
            WakeStyle.Off -> Unit
            WakeStyle.Bounce -> {
                // Top, left and right in turn; p overshoots 1, so the icon swings past its
                // place and back: that is the bounce.
                when (seed % 3) {
                    0 -> translationY = -inverse * distance * 2.4f
                    1 -> translationX = -inverse * distance * 3f
                    else -> translationX = inverse * distance * 3f
                }
                val s = 0.55f + 0.45f * p
                scaleX = s
                scaleY = s
            }
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
