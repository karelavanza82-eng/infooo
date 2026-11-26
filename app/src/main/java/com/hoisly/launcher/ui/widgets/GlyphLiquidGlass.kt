package com.hoisly.launcher.ui.widgets

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.hoisly.launcher.ui.glass.GlassOutline
import com.hoisly.launcher.ui.glass.GlassSource
import com.hoisly.launcher.ui.glass.LIQUID_GLASS_MIN_SDK
import com.hoisly.launcher.ui.glass.LiquidGlassFrame
import com.hoisly.launcher.ui.glass.LiquidGlassPainter
import com.hoisly.launcher.ui.glass.LocalGlassSource
import com.hoisly.launcher.ui.glass.isLiquidGlassReady

/**
 * Paints the shared Hoisly Liquid Glass shader through the actual numeral glyph mask.
 * There is deliberately no card, rounded panel, border or background behind the clock.
 * The shared renderer still uses its lens/refraction/specular pipeline; clipping limits
 * every visible pixel to the bold digit outlines.
 */
@Composable
internal fun GlyphLiquidGlassClock(
    text: String,
    fontSize: Dp,
    typeface: Typeface,
    modifier: Modifier = Modifier,
) {
    val source = LocalGlassSource.current
    val density = LocalDensity.current
    val painter = remember { LiquidGlassPainter() }
    val frame = remember { LiquidGlassFrame() }
    val mask = remember { AndroidPath() }
    val paint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.FILL
            color = android.graphics.Color.WHITE
            textAlign = AndroidPaint.Align.CENTER
        }
    }
    val pos = remember { floatArrayOf(0f, 0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(fontSize * 1.22f)
            .onGloballyPositioned { coordinates ->
                val p = coordinates.positionInWindow()
                pos[0] = p.x
                pos[1] = p.y
            },
    ) {
        val px = with(density) { fontSize.toPx() }
        paint.textSize = px
        paint.typeface = typeface
        mask.reset()
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        paint.getTextPath(text, 0, text.length, size.width / 2f, baseline, mask)

        val glassSource = source
        if (Build.VERSION.SDK_INT >= LIQUID_GLASS_MIN_SDK && glassSource?.sharp != null && glassSource.body != null && isLiquidGlassReady()) {
            frame.outline = GlassOutline.rounded(size.width, size.height, 0f, 0f, 0f, 0f)
            frame.windowPos = Offset(pos[0], pos[1])
            frame.rim = with(density) { 13.dp.toPx() }
            frame.refraction = with(density) { 7.dp.toPx() }
            frame.tone.red = 1f
            frame.tone.green = 1f
            frame.tone.blue = 1f
            frame.tone.alpha = 0.015f
            frame.tone.highlight = 1.15f
            frame.tone.shadow = 0.45f
            frame.highlight = Color.White
            frame.edge = true
            frame.lightAngle = 0f
            frame.touchAmount = 0f
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val save = native.save()
                native.clipPath(mask)
                with(painter) { draw(frame, glassSource) }
                native.restoreToCount(save)
            }
        } else {
            drawIntoCanvas { canvas ->
                paint.color = android.graphics.Color.WHITE
                canvas.nativeCanvas.drawPath(mask, paint)
            }
        }
    }
}

