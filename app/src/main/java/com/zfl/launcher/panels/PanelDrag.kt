package com.zfl.launcher.panels

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * One vertical drag that drives a panel. [accepts] decides at touch-down whether this gesture
 * is for the panel at all; a child that consumes the movement first (a slider, a scrolling
 * list) keeps it, so panel drags never fight controls. [onEnd] always runs after a started
 * drag, with the release velocity in px/s (0 when the gesture was cancelled).
 */
internal fun Modifier.verticalPanelDrag(
    accepts: (Offset) -> Boolean,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!accepts(down.position)) return@awaitEachGesture
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var started = false
        val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
            change.consume()
            started = true
            tracker.addPosition(change.uptimeMillis, change.position)
            onStart()
            onDrag(overSlop)
        } ?: return@awaitEachGesture
        val completed = verticalDrag(slop.id) { change ->
            tracker.addPosition(change.uptimeMillis, change.position)
            onDrag(change.positionChange().y)
            change.consume()
        }
        if (started) onEnd(if (completed) tracker.calculateVelocity().y else 0f)
    }
}
