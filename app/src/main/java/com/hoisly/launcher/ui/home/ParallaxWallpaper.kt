package com.hoisly.launcher.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tilt of the phone, -1..1 on each axis, relative to how it is held right now. */
@Stable
internal class ParallaxState {
    var x: Float by mutableFloatStateOf(0f)
    var y: Float by mutableFloatStateOf(0f)
}

/**
 * Reads gravity (else the accelerometer; neither needs a permission) and turns it into a tilt.
 * The reference slowly follows the phone, so a phone held at an angle drifts back to the
 * middle and only the movement itself pushes the wallpaper.
 */
private class ParallaxSensor(context: Context, private val state: ParallaxState) : SensorEventListener {
    private val manager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.let {
        it.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private var smoothX = 0f
    private var smoothZ = 0f
    private var baseX = 0f
    private var baseZ = 0f
    private var primed = false

    fun start(): Boolean {
        val m = manager ?: return false
        val s = sensor ?: return false
        return m.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager?.unregisterListener(this)
        primed = false
        state.x = 0f
        state.y = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (length < 1e-3f) return
        // x: sideways tilt. z: forwards/backwards tilt (it changes fastest when the phone is upright).
        val nx = v[0] / length
        val nz = v[2] / length
        if (!primed) {
            smoothX = nx; smoothZ = nz; baseX = nx; baseZ = nz
            primed = true
        }
        smoothX += (nx - smoothX) * SMOOTH
        smoothZ += (nz - smoothZ) * SMOOTH
        baseX += (smoothX - baseX) * DRIFT
        baseZ += (smoothZ - baseZ) * DRIFT
        state.x = ((smoothX - baseX) * GAIN).coerceIn(-1f, 1f)
        state.y = ((smoothZ - baseZ) * GAIN).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val SMOOTH = 0.12f
        const val DRIFT = 0.01f
        /** About 20 degrees of tilt moves the wallpaper all the way. */
        const val GAIN = 3f
    }
}

/** How far the wallpaper is enlarged so it can move without showing its edges. */
private const val BASE_SCALE = 1.14f
private const val SHIFT_X = 0.05f
private const val SHIFT_Y = 0.04f

/**
 * The wallpaper, drawn by the launcher itself so it can move: it slides against the tilt of
 * the phone while the home screen is started, and settles from a closer zoom each time the
 * icons fly in after unlock. Draws nothing without a readable wallpaper or when [enabled] is
 * false; the system wallpaper behind the window then stays as it is.
 */
@Composable
internal fun ParallaxWallpaper(wallpaper: ImageBitmap?, enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wake = LocalWake.current
    val state = remember { ParallaxState() }
    val owner = context as? LifecycleOwner
    val active = enabled && wallpaper != null

    LaunchedEffect(active, owner) {
        if (!active || owner == null || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        val sensor = ParallaxSensor(context, state)
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (sensor.start()) {
                try {
                    awaitCancellation()
                } finally {
                    sensor.stop()
                }
            }
        }
    }

    // 1 right after the phone was unlocked (zoomed in), settling to 0 with a soft bounce.
    val zoom = remember { Animatable(0f) }
    LaunchedEffect(wake.generation) {
        if (wake.generation == 0) return@LaunchedEffect
        zoom.snapTo(1f)
        zoom.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 80f))
    }

    if (!active || wallpaper == null) return
    Spacer(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = (BASE_SCALE + 0.12f * zoom.value).coerceAtLeast(1.1f)
                scaleX = scale
                scaleY = scale
                translationX = state.x * size.width * SHIFT_X
                translationY = state.y * size.height * SHIFT_Y
            }
            .drawBehind {
                drawImage(wallpaper, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            },
    )
}
