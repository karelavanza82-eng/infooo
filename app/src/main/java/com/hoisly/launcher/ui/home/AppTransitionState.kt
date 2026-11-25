package com.hoisly.launcher.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Holds independent app-opening surfaces. A list is used rather than a single slot so rapid taps
 * can animate concurrently; each transition is removed only when its own animation completes.
 */
internal class AppTransitionState {
    val current = MutableStateFlow<AppTransition?>(null)
    val active = MutableStateFlow<List<AppTransition>>(emptyList())
    private var ids = 0L

    fun begin(bounds: Rect, icon: ImageBitmap?) {
        val transition = AppTransition(
            ++ids,
            AppTransitionPhase.Opening,
            bounds,
            icon,
            iconSurfaceColor(icon),
        )
        active.value = active.value + transition
        current.value = transition
    }

    fun closeOpening(): Boolean {
        val opening = active.value.filter { it.phase == AppTransitionPhase.Opening }
        if (opening.isEmpty()) return false
        val closingByOldId = opening.associate { old ->
            old.id to old.copy(id = ++ids, phase = AppTransitionPhase.Closing)
        }
        active.value = active.value.map { transition ->
            closingByOldId[transition.id] ?: transition
        }
        current.value = closingByOldId.values.lastOrNull()
        return true
    }

    fun finish(id: Long) {
        val remaining = active.value.filterNot { it.id == id }
        active.value = remaining
        if (current.value?.id == id) current.value = remaining.lastOrNull()
    }
}
