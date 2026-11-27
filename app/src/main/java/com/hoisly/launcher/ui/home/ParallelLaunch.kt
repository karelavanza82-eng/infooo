package com.hoisly.launcher.ui.home

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.LauncherViewModel
import com.hoisly.launcher.ui.theme.Motion

/** After the last tap, this long passes before the tapped apps really open (so you can keep tapping). */
const val PARALLEL_IDLE_MS = 400L

/** Pause between starting one tapped app and the next, so Android does not drop any. */
const val PARALLEL_GAP_MS = 150L

/** Safety ceiling only; each surface removes itself right after its animation, so taps are effectively unlimited. */
const val PARALLEL_MAX_CARDS = 60

/**
 * One extra icon tap made while another app is already opening: it gets its own copy of the
 * default Hoisly surface (icon grows into a full-screen rounded surface). [bounds] is in window pixels.
 */
@Immutable
data class ParallelCard(
    val id: Long,
    val bounds: Rect,
    val icon: ImageBitmap?,
    val color: Color,
)

/** Draws every live [ParallelCard]; each runs its own default-style animation, so they overlap freely. */
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
    val fade = remember(card.id) { Animatable(1f) }
    LaunchedEffect(card.id) {
        progress.animateTo(1f, tween(Motion.APP_OPEN_MS, easing = Motion.IosEasing))
        fade.animateTo(0f, tween(250))
        onDone()
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) {
        // Same drawing as "Default Hoisly".
        drawAppTransitionVisual("HOISLY", card.bounds, card.icon, card.color, progress.value)
    }
}
