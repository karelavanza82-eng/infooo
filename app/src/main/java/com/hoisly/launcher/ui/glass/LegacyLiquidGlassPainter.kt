package com.hoisly.launcher.ui.glass

import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Path as AndroidPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.hoisly.launcher.ui.theme.LiquidGlassTokens
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Compatibility renderer for Android 9–12.
 *
 * It deliberately consumes the same GlassFrame, GlassTone, wallpaper body, profile rim,
 * touch state, light angle and LiquidGlassTokens as the AGSL renderer. Android versions
 * before 13 do not expose RuntimeShader/AGSL, so the optical layers are reproduced with
 * the platform Canvas shader primitives available since older Android releases.
 *
 * The result is the same Hoisly Liquid Glass design language and tuning; pixel-identical
 * per-pixel refraction is not possible without AGSL, but the legacy renderer keeps the
 * wallpaper, glass body, edge lens, specular reflection, caustic, shadow and touch response.
 */
internal class LegacyLiquidGlassPainter {
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val matrix = Matrix()

    fun DrawScope.draw(path: Path, frame: LiquidGlassFrame, source: GlassSource): Boolean {
        val outline = frame.outline ?: return false
        val body = source.body ?: return false
        val bitmap = runCatching { body.asAndroidBitmap() }.getOrNull() ?: return false
        if (outline.width <= 0f || outline.height <= 0f) return false

        val k = source.sharpDownscale.toFloat().coerceAtLeast(1f)
        val bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        matrix.setScale(k, k)
        matrix.postTranslate(-frame.windowPos.x, -frame.windowPos.y)
        bitmapShader.setLocalMatrix(matrix)
        imagePaint.shader = bitmapShader
        imagePaint.alpha = 255

        val tone = frame.tone
        val tint = android.graphics.Color.rgb(
            (tone.red.coerceIn(0f, 1f) * 255f).toInt(),
            (tone.green.coerceIn(0f, 1f) * 255f).toInt(),
            (tone.blue.coerceIn(0f, 1f) * 255f).toInt()
        )
        val w = outline.width
        val h = outline.height
        val strokeColor = frame.highlight.toArgb()
        val lightX = sin(LIGHT_BASE + frame.lightAngle)
        val lightY = -cos(LIGHT_BASE + frame.lightAngle)

        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val androidPath: AndroidPath = path.asAndroidPath()

            // Floating shadow, matching the original shader's offset and softness.
            val saveShadow = canvas.save()
            overlayPaint.shader = null
            overlayPaint.style = Paint.Style.FILL
            overlayPaint.color = android.graphics.Color.BLACK
            overlayPaint.alpha = (LiquidGlassTokens.ShadowOpacity * tone.shadow * 255f)
                .toInt().coerceIn(0, 255)
            overlayPaint.setShadowLayer(
                LiquidGlassTokens.ShadowRadius.toPx(),
                0f,
                LiquidGlassTokens.ShadowOffsetY.toPx(),
                android.graphics.Color.BLACK
            )
            canvas.drawPath(androidPath, overlayPaint)
            overlayPaint.clearShadowLayer()
            canvas.restoreToCount(saveShadow)

            val save = canvas.save()
            canvas.clipPath(androidPath)

            // Wallpaper body: same preprocessed sharp/soft mix used by the AGSL shader.
            canvas.drawPath(androidPath, imagePaint)

            // Adaptive tint from the exact same luminance calculation as Android 13+.
            overlayPaint.shader = null
            overlayPaint.style = Paint.Style.FILL
            overlayPaint.color = tint
            overlayPaint.alpha = (tone.alpha.coerceIn(0f, 1f) * 255f).toInt()
            canvas.drawPath(androidPath, overlayPaint)

            // Broad translucent body reflection and lower falloff.
            overlayPaint.alpha = 255
            overlayPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0x35FFFFFF, 0x0EFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(androidPath, overlayPaint)

            overlayPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0x00000000, 0x08000000, 0x20000000),
                floatArrayOf(0f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(androidPath, overlayPaint)

            // Moving specular reflection follows the same tilt sensor/light angle.
            val reflectionX = w * (0.5f - lightX * 0.20f)
            val reflectionY = h * (0.10f - lightY * 0.08f)
            val reflectionRadius = max(max(w, h) * 0.82f, 1f)
            overlayPaint.shader = RadialGradient(
                reflectionX, reflectionY, reflectionRadius,
                intArrayOf(0x30FFFFFF, 0x12FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.36f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(androidPath, overlayPaint)

            // Lens-like caustic: a narrow, soft highlight on the light-facing inner edge.
            val causticX = w * (if (lightX >= 0f) 0.76f else 0.24f)
            val causticY = h * (if (lightY >= 0f) 0.92f else 0.08f)
            overlayPaint.shader = RadialGradient(
                causticX, causticY, max(min(w, h) * 0.72f, 1f),
                intArrayOf(0x24FFFFFF, 0x0AFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(androidPath, overlayPaint)

            // Bright glass lip + softer inner bevel. Stroke width follows the profile rim.
            if (frame.edge) {
                val edgeAlpha = (LiquidGlassTokens.HighlightStrength * tone.highlight *
                    frame.highlight.alpha / 0.34f).coerceIn(0f, 1f)
                edgePaint.strokeWidth = max(frame.rim * 0.48f, 1f)
                edgePaint.color = strokeColor
                edgePaint.alpha = (edgeAlpha * 115f).toInt().coerceIn(0, 255)
                edgePaint.shader = LinearGradient(
                    0f, 0f, w * 0.18f, h,
                    intArrayOf(0xBFFFFFFF, 0x32FFFFFF, 0x12FFFFFF),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(androidPath, edgePaint)

                edgePaint.shader = null
                edgePaint.strokeWidth = max(LiquidGlassTokens.HighlightWidth.toPx() * 0.42f, 0.7f)
                edgePaint.color = strokeColor
                edgePaint.alpha = (edgeAlpha * 185f).toInt().coerceIn(0, 255)
                canvas.drawPath(androidPath, edgePaint)
            }

            // Local touch light uses the same press amount and radius as the AGSL implementation.
            if (frame.touchAmount > 0f) {
                val radius = max(min(w, h) * LiquidGlassTokens.TouchGlowRadius, 1f)
                overlayPaint.shader = RadialGradient(
                    frame.touchX, frame.touchY, radius,
                    intArrayOf(
                        (0x24FFFFFF * frame.touchAmount).toInt(),
                        (0x10FFFFFF * frame.touchAmount).toInt(),
                        0x00FFFFFF
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(androidPath, overlayPaint)
            }

            overlayPaint.shader = null
            edgePaint.shader = null
            canvas.restoreToCount(save)
        }
        imagePaint.shader = null
        return true
    }

    private companion object {
        val LIGHT_BASE: Float =
            Math.toRadians(LiquidGlassTokens.LightBaseDegrees.toDouble()).toFloat()
    }
}
