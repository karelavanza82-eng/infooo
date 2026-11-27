package com.hoisly.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp as lerpRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.LauncherViewModel
import com.hoisly.launcher.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


enum class AppTransitionPhase { Opening, Closing }

/** An app opening out of its icon, or closing back into it. [bounds] is in window pixels. */
@Immutable
data class AppTransition(
    val id: Long,
    val phase: AppTransitionPhase,
    val bounds: Rect,
    val icon: ImageBitmap?,
    val color: Color,
)

/** Average color of [icon], slightly darkened: the colour of the surface that grows from it. */
fun iconSurfaceColor(icon: ImageBitmap?): Color {
    val bitmap = icon?.asAndroidBitmap() ?: return Color.Black
    return runCatching {
        val pixel = android.graphics.Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
        androidx.compose.ui.graphics.lerp(Color(pixel).copy(alpha = 1f), Color.Black, 0.25f)
    }.getOrDefault(Color.Black)
}

@Composable
fun AppTransitionOverlay(vm: LauncherViewModel, progress: Animatable<Float, AnimationVector1D>) {
    val current by vm.appTransition.collectAsStateWithLifecycle()
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val transition = current
    val style = settings.appAnimationStyle
    val speedFactor = (1.35f - settings.animationSpeedPercent.coerceIn(0, 100) / 100f * 0.7f).coerceIn(0.55f, 1.35f)

    LaunchedEffect(transition?.id, transition?.phase, style, speedFactor) {
        val t = transition ?: return@LaunchedEffect
        val base = when (style) {
            "IOS27" -> 650
            "AURA" -> 520
            "SHUTTER" -> 500
            "ORBIT" -> 620
            "ANTICIPATE" -> 480
            "SCAN" -> 520
            "MIST" -> 580
            else -> Motion.APP_OPEN_MS
        }
        val duration = if (style == "HOISLY") Motion.APP_OPEN_MS else (base * speedFactor).roundToInt().coerceAtLeast(1)
        val closeDuration = if (style == "HOISLY") Motion.APP_CLOSE_MS else (duration * 0.86f).roundToInt().coerceAtLeast(1)
        val easing = when (style) {
            "IOS27", "ANTICIPATE" -> LaunchiiAppMotion.spring(duration.toLong())
            "SHUTTER" -> androidx.compose.animation.core.LinearOutSlowInEasing
            "ORBIT" -> androidx.compose.animation.core.FastOutSlowInEasing
            "AURA", "SCAN", "MIST" -> androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
            else -> Motion.IosEasing
        }
        when (t.phase) {
            AppTransitionPhase.Opening -> {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(duration, easing = easing))
            }
            AppTransitionPhase.Closing -> {
                progress.animateTo(0f, tween(closeDuration, easing = easing))
                vm.endAppTransition(t.id)
            }
        }
    }

    if (transition == null) return
    Canvas(Modifier.fillMaxSize()) {
        drawAppTransitionVisual(style, transition.bounds, transition.icon, transition.color, progress.value)
    }
}

/** Shared renderer used by the live launcher transition and the animated style preview. */
fun DrawScope.drawAppTransitionVisual(style: String, bounds: Rect, icon: ImageBitmap?, color: Color, progress: Float) {
    when (style) {
        "IOS27" -> drawLaunchiiZoom(bounds, icon, color, progress)
        "AURA" -> drawAura(bounds, color, progress)
        "SHUTTER" -> drawShutter(bounds, color, progress)
        "ORBIT" -> drawOrbit(bounds, color, progress)
        "ANTICIPATE" -> drawAnticipate(bounds, color, progress)
        "SCAN" -> drawScan(bounds, color, progress)
        "MIST" -> drawMist(bounds, color, progress)
        "PARALLEL" -> drawHoislySurface(bounds, icon, color, progress)
        else -> drawHoislySurface(bounds, icon, color, progress)
    }
}

/** Existing Hoisly surface animation, unchanged for Default Hoisly. */
private fun DrawScope.drawHoislySurface(bounds: Rect, icon: ImageBitmap?, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val rect = lerpRect(bounds, Rect(Offset.Zero, size), p)
    val corner = lerp(bounds.width * 0.2237f, Motion.APP_WINDOW_CORNER_DP * density, p)
    drawRoundRect(color.copy(alpha = (p * 5f).coerceIn(0f, 1f)), rect.topLeft, rect.size, CornerRadius(corner))
    icon ?: return
    val side = lerp(bounds.width, bounds.width * 1.25f, p)
    val center = rect.center
    drawImage(icon, dstOffset = androidx.compose.ui.unit.IntOffset((center.x - side / 2f).roundToInt(), (center.y - side / 2f).roundToInt()),
        dstSize = androidx.compose.ui.unit.IntSize(side.roundToInt(), side.roundToInt()), alpha = (1f - p * 2.5f).coerceIn(0f, 1f))
}

/** Launchii Zoom In adapted to Hoisly's own icon and home-screen coordinates. */
private fun DrawScope.drawLaunchiiZoom(bounds: Rect, icon: ImageBitmap?, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val endScale = LaunchiiAppMotion.CARD_END_SCALE
    val ex = size.width * (1f - endScale) / 2f
    val ey = size.height * (1f - endScale) / 2f
    val end = Rect(ex, ey, size.width - ex, size.height - ey)
    val rect = lerpRect(bounds, end, p)
    drawRect(Color.Black.copy(alpha = LaunchiiAppMotion.HOME_DIM * p))
    val corner = lerp(bounds.width * LaunchiiAppMotion.ICON_RADIUS, LaunchiiAppMotion.WINDOW_CORNER_DP * density, p)
    drawRoundRect(color.copy(alpha = (p / 0.08f).coerceIn(0f, 1f)), rect.topLeft, rect.size, CornerRadius(corner))
    icon ?: return
    val side = lerp(bounds.width, LaunchiiAppMotion.SPLASH_ICON_DP * density, p)
    val center = rect.center
    val iconRect = Rect(center.x - side / 2f, center.y - side / 2f, center.x + side / 2f, center.y + side / 2f)
    val clip = Path().apply { addRoundRect(RoundRect(iconRect, CornerRadius(lerp(side * 0.30f, side * 0.5f, p)))) }
    val alpha = (1f - ((p - 0.38f) / 0.52f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    if (alpha > 0f && side > 0f) clipPath(clip) {
        drawImage(icon, dstOffset = androidx.compose.ui.unit.IntOffset(iconRect.left.roundToInt(), iconRect.top.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(side.roundToInt(), side.roundToInt()), alpha = alpha)
    }
}

/** Aura: a coloured glow grows out of the tapped icon while Home dims. */
private fun DrawScope.drawAura(bounds: Rect, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    drawRect(Color.Black.copy(alpha = 0.48f * p))
    val from = bounds.center
    val center = Offset(lerp(from.x, size.width / 2f, p), lerp(from.y, size.height * 0.47f, p))
    val radius = lerp(bounds.width * 0.28f, size.width * 0.92f, p)
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.92f * p), color.copy(alpha = 0.36f * p), Color.Transparent), center, radius), radius, center)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f * p), Color.Transparent), center, radius * 0.55f), radius * 0.55f, center)
}

/** Shutter: staggered horizontal colour slats roll out from the icon row. */
private fun DrawScope.drawShutter(bounds: Rect, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    drawRect(Color.Black.copy(alpha = 0.16f * p))
    val count = 14
    val slatHeight = size.height / count
    val origin = bounds.center.y
    for (i in 0 until count) {
        val top = i * slatHeight
        val stagger = kotlin.math.abs(top + slatHeight / 2f - origin) / size.height * 0.22f
        val local = ((p - stagger) / (1f - stagger)).coerceIn(0f, 1f)
        if (local <= 0f) continue
        val eased = local * local * (3f - 2f * local)
        val height = slatHeight * eased
        val y = if (top + slatHeight / 2f < origin) top + slatHeight - height else top
        clipRect(0f, top, size.width, top + slatHeight) {
            drawRect(color.copy(alpha = 0.86f * eased), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, height))
            drawRect(Color.White.copy(alpha = 0.12f * eased), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1.5f * density))
        }
    }
}

/** Orbit: six app-coloured lights circle the icon as the screen darkens. */
private fun DrawScope.drawOrbit(bounds: Rect, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    drawRect(Color.Black.copy(alpha = 0.42f * p))
    val center = Offset(lerp(bounds.center.x, size.width / 2f, p), lerp(bounds.center.y, size.height / 2f, p))
    val radius = lerp(bounds.width * 0.22f, size.width * 0.23f, p)
    drawCircle(color.copy(alpha = 0.18f * p), radius * 1.45f, center)
    for (i in 0 until 6) {
        val angle = (i * (2f * PI.toFloat() / 6f)) + p * 2f * PI.toFloat()
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        val dot = lerp(2.5f, 8f, p) * density
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * p), color.copy(alpha = 0.75f * p), Color.Transparent), Offset(x, y), dot * 2.8f), dot * 2.8f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.9f * p), dot * 0.42f, Offset(x, y))
    }
}

/** Anticipate: a small pull-back, then a forward lunge. */
private fun DrawScope.drawAnticipate(bounds: Rect, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    val wind = (p / 0.24f).coerceIn(0f, 1f)
    val go = ((p - 0.24f) / 0.76f).coerceIn(0f, 1f)
    val scale = if (p < 0.24f) 1f - 0.035f * wind else 0.965f + 0.10f * go
    val insetX = (size.width * (1f - scale)) / 2f
    val insetY = (size.height * (1f - scale)) / 2f
    drawRect(Color.Black.copy(alpha = 0.22f * p))
    drawRoundRect(color.copy(alpha = 0.40f * p), Offset(insetX, insetY), androidx.compose.ui.geometry.Size(size.width - 2 * insetX, size.height - 2 * insetY), CornerRadius(24f.dp.toPx()))
    val flash = (1f - kotlin.math.abs(p - 0.42f) / 0.42f).coerceIn(0f, 1f)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.12f * flash), Color.Transparent)))
}

/** Scan: two luminous rails separate from the icon, leaving a frosted band between them. */
private fun DrawScope.drawScan(bounds: Rect, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    drawRect(Color.Black.copy(alpha = 0.18f * p))
    val origin = bounds.center.y
    val gap = lerp(bounds.width * 0.16f, size.height * 0.48f, p)
    val top = origin - gap
    val bottom = origin + gap
    drawRect(color.copy(alpha = 0.10f * p), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, (bottom - top).coerceAtLeast(0f)))
    val lineAlpha = (0.95f * p)
    drawLine(Color.White.copy(alpha = lineAlpha), Offset(0f, top), Offset(size.width, top), 2.2f * density)
    drawLine(color.copy(alpha = lineAlpha), Offset(0f, bottom), Offset(size.width, bottom), 2.2f * density)
    val sweep = ((p * 1.8f) % 1f) * size.height
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f * p), color.copy(alpha = 0.10f * p), Color.Transparent), sweep - 45f * density, sweep + 45f * density))
}

/** Mist: soft colour clouds roll in from the edges and gather over the app. */
private fun DrawScope.drawMist(bounds: Rect, color: Color, progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    drawRect(Color.Black.copy(alpha = 0.10f * p))
    val radius = size.width * 0.48f * p
    val edge = listOf(
        Offset(-radius * 0.34f, size.height * 0.22f), Offset(size.width + radius * 0.32f, size.height * 0.30f),
        Offset(-radius * 0.25f, size.height * 0.72f), Offset(size.width + radius * 0.28f, size.height * 0.78f),
        Offset(size.width * 0.5f, -radius * 0.45f), Offset(size.width * 0.5f, size.height + radius * 0.42f),
    )
    edge.forEachIndexed { i, start ->
        val target = Offset(size.width / 2f + if (i % 2 == 0) -size.width * 0.10f else size.width * 0.10f, size.height * 0.48f + (i - 2.5f) * 9f * density)
        val center = Offset(lerp(start.x, target.x, p), lerp(start.y, target.y, p))
        val cloudRadius = (radius * (if (i % 2 == 0) 0.78f else 0.62f)).coerceAtLeast(1f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * p), color.copy(alpha = 0.18f * p), Color.Transparent), center, cloudRadius), cloudRadius, center)
    }
}

private const val ICON_END_SCALE = 1.25f
