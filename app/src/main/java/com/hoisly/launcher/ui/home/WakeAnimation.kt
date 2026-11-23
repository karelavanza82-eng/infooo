package com.hoisly.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hoisly.launcher.settings.WakeStyle

/** Which layer of the home screen an item belongs to; each layer sits at its own depth. */
enum class WakeLayer { Icon, Widget, Dock }

/**
 * Drives the entrance of the home screen after the phone was unlocked, like the iOS unlock:
 * the home screen is already drawn, fully visible, and a little closer to the viewer; it then
 * settles back item by item (top to bottom, widgets first, dock last), each on its own spring.
 *
 * The home screen is never hidden. While the display is off the state is [arm]ed, which only
 * holds the "closer" starting pose, so the system's unlock animation reveals a populated,
 * zoomed home screen instead of an empty one. [trigger] (the phone was unlocked) restarts the
 * single master clock [timeMs] from that same pose, so nothing jumps when the entrance begins.
 */
@Stable
class WakeState {
    var generation by mutableIntStateOf(0)
        private set

    /** True from the moment the display turned off until the entrance starts. */
    var armed by mutableStateOf(false)
        private set

    var style by mutableStateOf(WakeStyle.Bounce)

    /** The generation whose clock restart already ran; until it matches, the pose stays at the start. */
    internal var clockGeneration by mutableIntStateOf(0)

    /** True while the clock must read zero: display off, or an unlock whose clock has not restarted yet. */
    internal val holdingStart: Boolean get() = armed || generation != clockGeneration

    /** Milliseconds since the entrance started; [WAKE_TOTAL_MS] means "settled, nothing to draw". */
    internal val timeMs = Animatable(WAKE_TOTAL_MS)

    fun arm() {
        if (style != WakeStyle.Off) armed = true
    }

    /** Releases the held pose without an entrance (an app is closing into its icon instead). */
    fun disarm() {
        armed = false
    }

    fun trigger() {
        armed = false
        if (style != WakeStyle.Off) generation++
    }
}

val LocalWake = staticCompositionLocalOf { WakeState() }

internal const val WAKE_TOTAL_MS = 150f

/** Latest delay of an item: the bottom-right corner starts this long after the top-left. */
private const val WAKE_SPREAD_MS = 10f
internal const val WAKE_DAMPING = 0.68f
internal const val WAKE_STIFFNESS = 4500f
private const val WAKE_WALLPAPER_DAMPING = 0.8f
private const val WAKE_WALLPAPER_STIFFNESS = 4000f

/** How much closer each layer starts; the different depths read as parallax. */
private const val WAKE_ICON_SCALE = 1.10f
private const val WAKE_WIDGET_SCALE = 1.07f
private const val WAKE_DOCK_SCALE = 1.04f

/**
 * Runs the master clock. One effect for the whole home screen: it holds the clock at zero while
 * [WakeState.armed], and replays it from zero on every [WakeState.trigger]. Items and the
 * wallpaper only read [WakeState.timeMs] in their draw phase, so nothing recomposes per frame.
 */
@Composable
fun WakeClock() {
    val wake = LocalWake.current
    LaunchedEffect(wake.armed) { if (wake.armed) wake.timeMs.snapTo(0f) }
    LaunchedEffect(wake.generation) {
        if (wake.generation == 0) return@LaunchedEffect
        wake.timeMs.snapTo(0f)
        wake.clockGeneration = wake.generation
        wake.timeMs.animateTo(WAKE_TOTAL_MS, tween(WAKE_TOTAL_MS.toInt(), easing = LinearEasing))
    }
}

/** Where a wake item sits, measured in layout and read in draw (a plain object: no recomposition). */
private class WakeSlot {
    var left = 0f
    var top = 0f
    var width = 1f
    var height = 1f
    var rootWidth = 1f
    var rootHeight = 1f
}

/**
 * Spring progress of the wallpaper, 0 (closer) to 1 (settled, may overshoot). It starts with
 * the clock and settles more slowly than the icons, so it keeps moving after they stopped.
 */
internal fun WakeState.wallpaperProgress(): Float {
    if (style == WakeStyle.Off) return 1f
    if (holdingStart) return 0f
    val t = timeMs.value
    if (t >= WAKE_TOTAL_MS) return 1f
    return springStep(t / 1000f, WAKE_WALLPAPER_DAMPING, WAKE_WALLPAPER_STIFFNESS)
}

/**
 * Plays the [WakeStyle] entrance on this item each time [WakeState.trigger] runs. Bounce is the
 * iOS-style one described on [WakeState]; the other styles keep their own start poses.
 */
fun Modifier.wakePop(seed: Int, layer: WakeLayer = WakeLayer.Icon): Modifier = composed {
    val wake = LocalWake.current
    val slot = remember { WakeSlot() }
    val density = LocalDensity.current
    val distance = with(density) { 120.dp.toPx() }
    val kind = if (layer == WakeLayer.Icon && seed >= DOCK_SEED) WakeLayer.Dock else layer
    Modifier
        .onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            val root = coordinates.findRootCoordinates()
            slot.left = position.x
            slot.top = position.y
            slot.width = coordinates.size.width.coerceAtLeast(1).toFloat()
            slot.height = coordinates.size.height.coerceAtLeast(1).toFloat()
            slot.rootWidth = root.size.width.coerceAtLeast(1).toFloat()
            slot.rootHeight = root.size.height.coerceAtLeast(1).toFloat()
        }
        .graphicsLayer {
            val style = wake.style
            if (style == WakeStyle.Off) return@graphicsLayer
            val t = if (wake.holdingStart) 0f else wake.timeMs.value
            if (t >= WAKE_TOTAL_MS) return@graphicsLayer
            val centerX = slot.left + slot.width / 2f
            val centerY = slot.top + slot.height / 2f
            // Top to bottom, then left to right; the dock is the last layer to settle.
            val order = (centerY / slot.rootHeight) * 0.8f + (centerX / slot.rootWidth) * 0.2f
            val delay = order.coerceIn(0f, 1f) * WAKE_SPREAD_MS + if (kind == WakeLayer.Dock) 5f else 0f
            val p = springStep((t - delay) / 1000f, WAKE_DAMPING, WAKE_STIFFNESS)
            val inverse = 1f - p
            when (style) {
                WakeStyle.Off -> Unit
                WakeStyle.Bounce -> {
                    val start = when (kind) {
                        WakeLayer.Icon -> WAKE_ICON_SCALE
                        WakeLayer.Widget -> WAKE_WIDGET_SCALE
                        WakeLayer.Dock -> WAKE_DOCK_SCALE
                    }
                    val s = 1f + (start - 1f) * inverse
                    scaleX = s
                    scaleY = s
                    // Scale about the middle of the screen, so the items spread apart as they settle.
                    transformOrigin = TransformOrigin(
                        (slot.rootWidth / 2f - slot.left) / slot.width,
                        (slot.rootHeight * 0.45f - slot.top) / slot.height,
                    )
                }
                WakeStyle.Pop -> {
                    val s = 0.4f + 0.6f * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 2.2f).coerceIn(0f, 1f)
                }
                WakeStyle.Top -> {
                    translationY = -inverse * distance * 2f
                    val s = 0.85f + 0.15f * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 2.2f).coerceIn(0f, 1f)
                }
                WakeStyle.Side -> {
                    translationX = (if (seed % 2 == 0) -1f else 1f) * inverse * distance * 3f
                    val s = 0.85f + 0.15f * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 2.2f).coerceIn(0f, 1f)
                }
            }
        }
}

private const val DOCK_SEED = 1000
