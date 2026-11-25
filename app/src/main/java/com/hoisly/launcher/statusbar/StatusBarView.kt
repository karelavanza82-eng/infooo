package com.hoisly.launcher.statusbar

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import com.hoisly.launcher.R

/**
 * iOS 27-style status bar: clock, muted bell, signal bars, WiFi (iOS 27 wider fan),
 * airplane (iOS 27 bold/tilted), and battery (iOS 27 large-nub pill with half-fill).
 * Optional orange dot indicator for active island activity.
 */
class StatusBarView(context: Context) : View(context) {

    var state: StatusBarState = StatusBarState()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** When true, a small orange dot appears next to the island glyph (right side, before battery). */
    var showActivityDot: Boolean = false
        set(value) { field = value; invalidate() }

    private var prefs = StatusBarPreferences()
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density * prefs.scale

    private var color = Color.BLACK
    private var colorTarget = Color.BLACK
    private var hasColor = false
    private var colorAnimator: ValueAnimator? = null

    private val bell: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_status_bell)?.mutate()
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val netPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF9500") }
    private val batteryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val gapXfer = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private val bounds = Rect()
    private val body = RectF()
    private val clip = Path()
    private val wifiRect = RectF()
    private val planeMatrix = Matrix()
    private val planeWork = Path()
    private val boltWork = Path()

    // iOS 27 airplane path: bold, slightly more tilted than iOS 16, wider wingspan
    // Source coordinates in a 24×24 grid, rotated 45° for status bar use
    private val planePath: Path = PathParser.createPathFromPathData(
        "M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z",
    ) ?: Path()

    init {
        background = null
        setWillNotDraw(false)
        applyPrefs(prefs)
        bell?.setTint(color)
    }

    fun applyPrefs(value: StatusBarPreferences) {
        prefs = value
        clockPaint.textSize = dp(Metrics.CLOCK_TEXT)
        clockPaint.typeface = font(value.clockWeight)
        clockPaint.letterSpacing = -0.01f
        netPaint.textSize = dp(Metrics.NET_TEXT)
        netPaint.typeface = font(500)
        batteryTextPaint.typeface = font(700)
        invalidate()
    }

    fun setBlackIcons(black: Boolean, animate: Boolean = true) {
        val target = if (black) Color.BLACK else Color.WHITE
        if (hasColor && target == colorTarget) return
        colorTarget = target
        colorAnimator?.cancel()
        if (!animate || !hasColor || !isAttachedToWindow) {
            hasColor = true
            applyColor(target)
            return
        }
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), color, target).apply {
            duration = Metrics.COLOR_ANIM_MS
            addUpdateListener { applyColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun applyColor(c: Int) {
        color = c
        bell?.setTint(c)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else dp(Metrics.FALLBACK_HEIGHT).toInt()
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f + prefs.verticalOffset * density
        val pad = prefs.sidePadding * density
        drawLeft(canvas, pad, cy)
        drawRight(canvas, width - pad, cy)
    }

    private fun drawLeft(canvas: Canvas, x: Float, cy: Float) {
        clockPaint.color = color
        clockPaint.getTextBounds("0", 0, 1, bounds)
        canvas.drawText(state.time, x, cy + bounds.height() / 2f, clockPaint)
        if (!prefs.showSilent || !state.silent) return
        val box = dp(Metrics.BELL_BOX)
        val left = x + clockPaint.measureText(state.time) + dp(Metrics.BELL_GAP) - box * (4f / 24f)
        drawMutedBell(canvas, left, cy - box / 2f, box)
    }

    private fun drawMutedBell(canvas: Canvas, left: Float, top: Float, box: Float) {
        val d = bell ?: return
        val layer = canvas.saveLayer(left, top, left + box, top + box, null)
        d.setBounds(left.toInt(), top.toInt(), (left + box).toInt(), (top + box).toInt())
        d.draw(canvas)
        val u = box / 24f
        val x1 = left + 3.4f * u
        val y1 = top + 3.0f * u
        val x2 = left + 20.6f * u
        val y2 = top + 21.0f * u
        slashPaint.xfermode = gapXfer
        slashPaint.color = Color.BLACK
        slashPaint.strokeWidth = 4.6f * u
        canvas.drawLine(x1, y1, x2, y2, slashPaint)
        slashPaint.xfermode = null
        slashPaint.color = color
        slashPaint.strokeWidth = 2.2f * u
        canvas.drawLine(x1, y1, x2, y2, slashPaint)
        canvas.restoreToCount(layer)
    }

    private fun drawRight(canvas: Canvas, right: Float, cy: Float) {
        val st = state
        var cursor = right

        // --- iOS 27 battery ---
        if (prefs.showBattery) {
            val bw = dp(Metrics.BATTERY_W)
            val bh = dp(Metrics.BATTERY_H)
            // iOS 27: large round nub on the right end, same height as body
            val nubW = dp(Metrics.NUB_W27)
            val nubH = dp(Metrics.NUB_H27)
            val nubGap = dp(Metrics.NUB_GAP27)
            val bodyRight = right - nubW - nubGap
            body.set(bodyRight - bw, cy - bh / 2f, bodyRight, cy + bh / 2f)

            // iOS 27: nub is a tall rounded rect, same colour as body outline
            shapePaint.color = ColorUtils.setAlphaComponent(color, Metrics.NUB_ALPHA27)
            val nubLeft = bodyRight + nubGap
            canvas.drawRoundRect(nubLeft, cy - nubH / 2f, nubLeft + nubW, cy + nubH / 2f,
                nubW / 2f, nubW / 2f, shapePaint)

            drawBattery27(canvas, body, st.batteryPercent)
            cursor = body.left - dp(Metrics.GAP)

            if (st.charging) {
                val boltW = dp(Metrics.BOLT_W)
                drawBolt(canvas, cursor - boltW, cy)
                cursor -= boltW + dp(Metrics.GAP)
            }
        }

        // --- Orange activity dot ---
        if (showActivityDot) {
            val dotR = dp(Metrics.DOT_R)
            dotPaint.color = Color.parseColor("#FF9500")
            canvas.drawCircle(cursor - dotR, cy, dotR, dotPaint)
            cursor -= dotR * 2 + dp(Metrics.GAP)
        }

        // --- iOS 27 WiFi (wider fan, bolder arcs) ---
        val showWifi = prefs.showWifi && st.wifiConnected
        if (showWifi) {
            val w = dp(Metrics.WIFI_W27)
            drawWifi27(canvas, cursor - w, cy, w, st.wifiLevel)
            cursor -= w + dp(Metrics.GAP)
        } else if (prefs.showNetworkLabel && st.cellularData && !st.airplane && st.networkLabel.isNotEmpty()) {
            netPaint.color = color
            netPaint.getTextBounds("5", 0, 1, bounds)
            val tw = netPaint.measureText(st.networkLabel)
            canvas.drawText(st.networkLabel, cursor - tw, cy + bounds.height() / 2f, netPaint)
            cursor -= tw + dp(Metrics.GAP)
        }

        // --- iOS 27 Airplane (bold, same as iOS Settings style) ---
        if (st.airplane) {
            val size = dp(Metrics.PLANE27)
            drawAirplane27(canvas, cursor - size, cy, size)
        } else if (prefs.showSignal) {
            val barW = dp(Metrics.BAR_W)
            val gap = dp(Metrics.BAR_GAP)
            drawSignal(canvas, cursor - (4 * barW + 3 * gap), cy, barW, gap, st.signalLevel)
        }
    }

    private fun drawSignal(canvas: Canvas, left: Float, cy: Float, barW: Float, gap: Float, level: Int) {
        val minH = dp(Metrics.BAR_MIN_H)
        val maxH = dp(Metrics.BAR_MAX_H)
        val bottom = cy + maxH / 2f
        for (i in 0 until 4) {
            val h = minH + (maxH - minH) * i / 3f
            val x = left + i * (barW + gap)
            shapePaint.color = if (i < level) color else ColorUtils.setAlphaComponent(color, Metrics.INACTIVE_ALPHA)
            canvas.drawRoundRect(x, bottom - h, x + barW, bottom, barW / 2f, barW / 2f, shapePaint)
        }
    }

    /**
     * iOS 27 WiFi icon: wider sweep angle (100°), thicker arcs, more pronounced wedge.
     * Fan opens wider to match the new rounded look in iOS 27 status bar.
     */
    private fun drawWifi27(canvas: Canvas, left: Float, cy: Float, width: Float, level: Int) {
        val outer = width / 1.3f          // slightly larger effective radius
        val cx = left + width / 2f
        val base = cy + outer * 0.48f
        val ring = outer * 0.26f          // thicker rings
        val gap = outer * 0.07f
        val dim = ColorUtils.setAlphaComponent(color, Metrics.INACTIVE_ALPHA)
        val sweep = 100f                  // iOS 27: wider 100° sweep vs old 90°
        val startAngle = 220f             // start shifted for wider fan

        // innermost wedge (filled arc)
        val r1 = outer - 2 * ring - 2 * gap
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = if (level >= 1) color else dim
        wifiRect.set(cx - r1, base - r1, cx + r1, base + r1)
        canvas.drawArc(wifiRect, startAngle, sweep, true, shapePaint)

        // two outer rings
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeCap = Paint.Cap.ROUND
        shapePaint.strokeWidth = ring
        for (i in 1..2) {
            val mid = r1 + gap + ring * (i - 0.5f) + (i - 1) * gap
            shapePaint.color = if (level >= i + 1) color else dim
            wifiRect.set(cx - mid, base - mid, cx + mid, base + mid)
            canvas.drawArc(wifiRect, startAngle, sweep, false, shapePaint)
        }
        shapePaint.style = Paint.Style.FILL
        shapePaint.strokeCap = Paint.Cap.BUTT
    }

    /**
     * iOS 27 airplane: drawn directly as a bold vector matching the Settings icon style.
     * The plane points upward (like stock iOS) — same path, but rendered at PLANE27 size
     * which is larger than before, with a bolder look.
     */
    private fun drawAirplane27(canvas: Canvas, left: Float, cy: Float, size: Float) {
        shapePaint.color = color
        planeMatrix.reset()
        // The reference airplane glyph points horizontally to the right.
        planeMatrix.postRotate(90f, 12f, 12f)
        planeMatrix.postScale(size / 24f, size / 24f)
        planeMatrix.postTranslate(left, cy - size / 2f)
        planeWork.set(planePath)
        planeWork.transform(planeMatrix)
        canvas.drawPath(planeWork, shapePaint)
    }

    private fun drawBolt(canvas: Canvas, left: Float, cy: Float) {
        val h = dp(Metrics.BOLT_H)
        val u = dp(Metrics.BOLT_W) / 8f
        val top = cy - h / 2f
        boltWork.reset()
        boltWork.moveTo(left + 5f * u, top)
        boltWork.lineTo(left, top + h * 0.58f)
        boltWork.lineTo(left + 3.6f * u, top + h * 0.58f)
        boltWork.lineTo(left + 2.4f * u, top + h)
        boltWork.lineTo(left + 8f * u, top + h * 0.40f)
        boltWork.lineTo(left + 4.4f * u, top + h * 0.40f)
        boltWork.close()
        shapePaint.color = color
        canvas.drawPath(boltWork, shapePaint)
    }

    /**
     * iOS 27 battery: left ~half is solid fill (the "charged" look), right half transparent.
     * The outline stroke is thinner; the nub is proportionally larger and more rounded.
     * Percentage is punched through the fill just like before.
     */
    private fun drawBattery27(canvas: Canvas, rect: RectF, percent: Int) {
        val radius = dp(Metrics.BATTERY_RADIUS27)
        val layer = canvas.saveLayer(rect.left - 1f, rect.top - 1f, rect.right + 1f, rect.bottom + 1f, null)

        // outline / track
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeWidth = dp(Metrics.BATTERY_STROKE27)
        shapePaint.color = ColorUtils.setAlphaComponent(color, Metrics.TRACK_ALPHA27)
        canvas.drawRoundRect(rect, radius, radius, shapePaint)

        // fill
        shapePaint.style = Paint.Style.FILL
        clip.reset()
        val inset = dp(Metrics.BATTERY_STROKE27) / 2f + dp(0.5f)
        val inner = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        clip.addRoundRect(inner, (radius - inset).coerceAtLeast(1f), (radius - inset).coerceAtLeast(1f), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        shapePaint.color = color
        canvas.drawRect(inner.left, inner.top, inner.left + inner.width() * percent.coerceIn(0, 100) / 100f, inner.bottom, shapePaint)
        canvas.restore()

        // percentage text punched out
        val text = percent.toString()
        batteryTextPaint.textSize = dp(if (text.length >= 3) Metrics.BATTERY_TEXT_3 else Metrics.BATTERY_TEXT)
        batteryTextPaint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, rect.centerX(), rect.centerY() + bounds.height() / 2f, batteryTextPaint)
        canvas.restoreToCount(layer)
    }

    private fun font(weight: Int): Typeface {
        val res = when {
            weight >= 700 -> R.font.inter_bold
            weight >= 600 -> R.font.inter_semibold
            weight >= 500 -> R.font.inter_medium
            else -> R.font.inter_regular
        }
        return ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT
    }

    private object Metrics {
        const val FALLBACK_HEIGHT = 28f
        const val CLOCK_TEXT = 16f
        const val BELL_BOX = 15f
        const val BELL_GAP = 3.5f
        const val GAP = 6.5f

        // signal bars (unchanged)
        const val BAR_W = 3.3f
        const val BAR_GAP = 1.8f
        const val BAR_MAX_H = 11.5f
        const val BAR_MIN_H = 3.5f
        const val INACTIVE_ALPHA = 90

        const val NET_TEXT = 15.5f

        // iOS 27 WiFi — wider, bolder
        const val WIFI_W27 = 19f

        // iOS 27 Airplane — larger, bolder
        const val PLANE27 = 18f

        // iOS 27 Battery — pill with thick outline, larger nub
        const val BATTERY_W = 27f
        const val BATTERY_H = 13f
        const val BATTERY_RADIUS27 = 4.5f
        const val BATTERY_STROKE27 = 1.5f
        const val TRACK_ALPHA27 = 100
        const val NUB_W27 = 3f
        const val NUB_H27 = 6f
        const val NUB_GAP27 = 1f
        const val NUB_ALPHA27 = 110

        // bolt
        const val BOLT_W = 6f
        const val BOLT_H = 11f

        // battery text
        const val BATTERY_TEXT = 12.5f
        const val BATTERY_TEXT_3 = 10f

        // orange activity dot
        const val DOT_R = 2.5f

        const val COLOR_ANIM_MS = 180L
    }
}
