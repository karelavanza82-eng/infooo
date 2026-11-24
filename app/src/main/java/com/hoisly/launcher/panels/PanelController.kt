package com.hoisly.launcher.panels

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hoisly.launcher.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Top-level sheets that slide in from the top edge. */
enum class Panel { Notifications }

/**
 * A 0..1 value that follows the finger while dragged and settles with the shared sheet spring,
 * keeping the release velocity. A new drag or target cancels a running spring, so every
 * animation is interruptible. Springs may overshoot: readers clamp where it matters.
 */
@Stable
internal class SpringValue(private val scope: CoroutineScope, initial: Float) {
    var value: Float by mutableFloatStateOf(initial)
        private set
    private var job: Job? = null

    fun jump(to: Float) {
        job?.cancel()
        value = to
    }

    fun drag(delta: Float) {
        job?.cancel()
        value = (value + delta).coerceIn(0f, 1f)
    }

    fun animateTo(target: Float, velocity: Float = 0f, onEnd: () -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            animate(value, target, velocity, Motion.sheet()) { v, _ -> value = v }
            value = target
            onEnd()
        }
    }
}

/**
 * State of the Notification Center. One panel is
 * open at a time; [fraction] is how far it is pulled out (1 = fully open). [heightPx] is set by
 * the host so a drag of N pixels moves the panel by N pixels.
 */
@Stable
class PanelController(scope: CoroutineScope) {
    private val shown = SpringValue(scope, 0f)

    var panel: Panel? by mutableStateOf(null)
        private set
    var heightPx: Float = 1f
    var widthPx: Float = 1f

    val fraction: Float get() = shown.value

    val isOpen: Boolean get() = panel != null

    fun open(target: Panel) {
        panel = target
        shown.animateTo(1f)
    }

    fun close() {
        if (panel == null) return
        shown.animateTo(0f) { panel = null }
    }

    /** A drag from the edge starts here: the panel appears at 0 and follows the finger. */
    fun beginDrag(target: Panel) {
        if (panel != target) {
            shown.jump(0f)
            panel = target
        }
    }

    fun drag(deltaPx: Float) = shown.drag(deltaPx / heightPx.coerceAtLeast(1f))

    /** Settles by position and, when flicked, by direction. [velocityPx] is positive downwards. */
    fun release(velocityPx: Float) {
        val velocity = velocityPx / heightPx.coerceAtLeast(1f)
        val open = when {
            velocity > FLING -> true
            velocity < -FLING -> false
            else -> shown.value > 0.5f
        }
        if (open) shown.animateTo(1f, velocity) else shown.animateTo(0f, velocity) { panel = null }
    }

    private companion object {
        /** Screen heights per second that count as a flick. */
        const val FLING = 1.5f
    }
}
