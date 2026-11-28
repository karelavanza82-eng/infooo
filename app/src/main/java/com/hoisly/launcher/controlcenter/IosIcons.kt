package com.hoisly.launcher.controlcenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons drawn to look like the ones in the reference control center (battery, screen record,
 * calculator, screen mirroring, rotation lock, AirPlay, Shazam-style S). All are 24x24 and
 * single-colour, so they take the tint from `Icon(tint = ...)`.
 */
object IosIcons {
    private val ink = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun PathBuilder.rrect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        horizontalLineTo(r - rad)
        arcTo(rad, rad, 0f, false, true, r, t + rad)
        verticalLineTo(b - rad)
        arcTo(rad, rad, 0f, false, true, r - rad, b)
        horizontalLineTo(l + rad)
        arcTo(rad, rad, 0f, false, true, l, b - rad)
        verticalLineTo(t + rad)
        arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        close()
    }

    /** Battery outline with a small level block, as on the Low Power Mode tile. */
    val Battery: ImageVector = icon("IosBattery") {
        path(stroke = ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            rrect(2.6f, 6.2f, 19.2f, 17.8f, 3.4f)
        }
        path(stroke = ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round) {
            moveTo(21.4f, 10.4f); verticalLineTo(13.6f)
        }
        path(fill = ink) { rrect(5f, 8.6f, 9.6f, 15.4f, 1.4f) }
    }

    /** Ring with a dot: screen recording. */
    val ScreenRecord: ImageVector = icon("IosScreenRecord") {
        path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { circle(12f, 12f, 8.6f) }
        path(fill = ink) { circle(12f, 12f, 4.3f) }
    }

    /** Solid calculator with a display and key dots punched out. */
    val Calculator: ImageVector = icon("IosCalculator") {
        path(fill = ink, pathFillType = PathFillType.EvenOdd) {
            rrect(5f, 2.4f, 19f, 21.6f, 3f)
            // display
            moveTo(7.6f, 5.2f); horizontalLineTo(16.4f); verticalLineTo(9.2f); horizontalLineTo(7.6f); close()
            // keys
            for (row in 0..2) for (col in 0..2) circle(9.1f + col * 2.9f, 12.6f + row * 2.9f, 0.95f)
        }
    }

    /** Two overlapping screens: screen mirroring. */
    val ScreenMirror: ImageVector = icon("IosScreenMirror") {
        path(stroke = ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            rrect(3f, 4.6f, 16.2f, 15f, 2.6f)
            rrect(8f, 9.2f, 21f, 19.6f, 2.6f)
        }
    }

    /** Circular arrow around a padlock: rotation lock. */
    val RotationLock: ImageVector = icon("IosRotationLock") {
        path(stroke = ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3.6f, 12f)
            arcTo(8.4f, 8.4f, 0f, true, true, 12f, 20.4f)
            moveTo(15.2f, 17.2f); lineTo(12f, 20.4f); lineTo(15.2f, 23.4f)
            moveTo(10.3f, 11.4f); verticalLineTo(10.2f)
            arcTo(1.7f, 1.7f, 0f, false, true, 13.7f, 10.2f)
            verticalLineTo(11.4f)
        }
        path(fill = ink) { rrect(8.9f, 11.2f, 15.1f, 16.4f, 1.3f) }
    }

    /** Rings over a triangle: AirPlay / output switcher. */
    val AirPlay: ImageVector = icon("IosAirPlay") {
        path(stroke = ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(6.34f, 16.66f)
            arcTo(8f, 8f, 0f, true, true, 17.66f, 16.66f)
            moveTo(8.61f, 14.39f)
            arcTo(4.8f, 4.8f, 0f, true, true, 15.39f, 14.39f)
        }
        path(fill = ink, stroke = ink, strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 15.4f); lineTo(16.6f, 21.2f); lineTo(7.4f, 21.2f); close()
        }
    }

    /** A bold S, standing in for the Shazam logo. */
    val Shazam: ImageVector = icon("IosShazam") {
        path(stroke = ink, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(16.4f, 8.4f)
            curveTo(14.6f, 5.8f, 8.4f, 6.4f, 8.8f, 10f)
            curveTo(9.2f, 13.2f, 15.2f, 11f, 15.2f, 14.2f)
            curveTo(15.2f, 17.8f, 9.4f, 18.2f, 7.6f, 15.6f)
        }
    }
}
