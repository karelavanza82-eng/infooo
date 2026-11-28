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
 * Custom status bar with one consistent compact icon family, live system indicators,
 * a borderless battery glyph, and optional activity marker.
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
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val moonCutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    // DST_OUT makes the percentage a real cut-out: wallpaper/status-bar content shows through it.
    private val batteryCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
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

    // Compact horizontal airplane glyph, transformed from a 24×24 vector path.
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
        clockPaint.typeface = when (value.clockWeight) {
            400 -> Typeface.create("sans-serif", Typeface.NORMAL)
            500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            600 -> Typeface.create("sans-serif", Typeface.BOLD)
            else -> Typeface.create("sans-serif", Typeface.BOLD)
        }
        clockPaint.letterSpacing = -0.015f
        netPaint.textSize = dp(Metrics.NET_TEXT)
        netPaint.typeface = font(500)
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
        clockPaint.textSize = dp(Metrics.CLOCK_TEXT)
        clockPaint.getTextBounds("0", 0, 1, bounds)

        // Clock stays at the leading edge. DND crescent sits immediately AFTER the clock,
        // matching the requested iOS placement. DND replaces the silent-bell glyph.
        val clockX = x
        canvas.drawText(state.time, clockX, cy + bounds.height() / 2f, clockPaint)
        val afterClock = clockX + clockPaint.measureText(state.time) + dp(4f)
        if (state.doNotDisturb) {
            val moonSize = dp(12.5f)
            drawDoNotDisturbMoon(canvas, afterClock, cy - moonSize / 2f, moonSize)
            return
        }
        if (!prefs.showSilent || !state.silent) return
        val box = dp(Metrics.BELL_BOX)
        val left = afterClock + dp(Metrics.BELL_GAP) - box * (4f / 24f)
        drawMutedBell(canvas, left, cy - box / 2f, box)
    }


    private fun drawDoNotDisturbMoon(canvas: Canvas, left: Float, top: Float, size: Float) {
        val layer = canvas.saveLayer(left, top, left + size, top + size, null)
        moonPaint.color = color
        val radius = size * 0.43f
        val cx = left + size * 0.47f
        val cy = top + size * 0.52f
        canvas.drawCircle(cx, cy, radius, moonPaint)
        // Knock out the upper-right portion to form a clean crescent.
        canvas.drawCircle(cx + radius * 0.58f, cy - radius * 0.62f, radius * 0.88f, moonCutPaint)
        canvas.restoreToCount(layer)
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

        // Compact battery: no outline. The unfilled capacity stays opaque gray;
        // only the filled portion turns green while charging. The bolt is to its RIGHT.
        if (prefs.showBattery) {
            val bw = dp(Metrics.BATTERY_W)
            val bh = dp(Metrics.BATTERY_H)
            val nubW = dp(Metrics.NUB_W27)
            val nubH = dp(Metrics.NUB_H27)
            val nubGap = dp(Metrics.NUB_GAP27)
            val boltW = if (st.charging) dp(Metrics.BOLT_W) else 0f
            val boltGap = if (st.charging) dp(Metrics.GAP) else 0f
            val boltRight = right
            val bodyRight = right - boltW - boltGap - nubW - nubGap
            body.set(bodyRight - bw, cy - bh / 2f, bodyRight, cy + bh / 2f)

            shapePaint.style = Paint.Style.FILL
            shapePaint.color = ColorUtils.setAlphaComponent(color, Metrics.NUB_ALPHA27)
            val nubLeft = bodyRight + nubGap
            canvas.drawRoundRect(nubLeft, cy - nubH / 2f, nubLeft + nubW, cy + nubH / 2f,
                nubW / 2f, nubW / 2f, shapePaint)

            drawBatteryGlyph(canvas, body, st.batteryPercent, st.charging)
            if (st.charging) {
                drawBolt(canvas, boltRight - boltW, cy)
            }
            cursor = body.left - dp(Metrics.GAP)
        }

        // --- Orange activity dot ---
        if (showActivityDot) {
            val dotR = dp(Metrics.DOT_R)
            dotPaint.color = Color.parseColor("#FF9500")
            canvas.drawCircle(cursor - dotR, cy, dotR, dotPaint)
            cursor -= dotR * 2 + dp(Metrics.GAP)
        }

        // --- Compact Wi-Fi ---
        val showWifi = prefs.showWifi && st.wifiConnected
        if (showWifi) {
            val w = dp(13f)
            drawWifiGlyph(canvas, cursor - w, cy, w, st.wifiLevel)
            cursor -= w + dp(Metrics.GAP)
        } else if (prefs.showNetworkLabel && st.cellularData && !st.airplane && st.networkLabel.isNotEmpty()) {
            netPaint.color = color
            netPaint.getTextBounds("5", 0, 1, bounds)
            val tw = netPaint.measureText(st.networkLabel)
            canvas.drawText(st.networkLabel, cursor - tw, cy + bounds.height() / 2f, netPaint)
            cursor -= tw + dp(Metrics.GAP)
        }

        // --- Airplane mode ---
        if (st.airplane) {
            val size = dp(15.5f)
            drawAirplaneGlyph(canvas, cursor - size, cy, size)
        } else if (prefs.showSignal) {
            val barW = dp(Metrics.BAR_W)
            val gap = dp(Metrics.BAR_GAP)
            drawSignalGlyph(canvas, cursor - (4 * barW + 3 * gap), cy, barW, gap, st.signalLevel)
        }
    }

    private fun drawSignalGlyph(canvas: Canvas, left: Float, cy: Float, barW: Float, gap: Float, level: Int) {
        val minH = dp(Metrics.BAR_MIN_H)
        val maxH = dp(Metrics.BAR_MAX_H)
        val width = barW
        val spacing = gap
        val bottom = cy + maxH / 2f
        for (i in 0 until 4) {
            val h = minH + (maxH - minH) * i / 3f
            val x = left + i * (width + spacing)
            shapePaint.style = Paint.Style.FILL
            shapePaint.color = if (i < level) color else ColorUtils.setAlphaComponent(color, Metrics.INACTIVE_ALPHA)
            val radius = width / 2f
            canvas.drawRoundRect(x, bottom - h, x + width, bottom, radius, radius, shapePaint)
        }
    }

    private fun drawWifiGlyph(canvas: Canvas, left: Float, cy: Float, width: Float, level: Int) {
        val outer = width / 1.3f
        val cx = left + width / 2f
        val base = cy + outer * 0.48f
        val ring = outer * 0.115f
        val gap = outer * 0.07f
        val dim = ColorUtils.setAlphaComponent(color, Metrics.INACTIVE_ALPHA)
        val sweep = 92f
        val start = (270f - sweep / 2f)
        val innerR = (outer - 2 * ring - 2 * gap).coerceAtLeast(1f)
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = if (level >= 1) color else dim
        wifiRect.set(cx - innerR, base - innerR, cx + innerR, base + innerR)
        canvas.drawArc(wifiRect, start, sweep, true, shapePaint)
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeCap = Paint.Cap.ROUND
        shapePaint.strokeWidth = ring
        for (i in 1..2) {
            val mid = innerR + gap + ring * (i - 0.5f) + (i - 1) * gap
            shapePaint.color = if (level >= i + 1) color else dim
            wifiRect.set(cx - mid, base - mid, cx + mid, base + mid)
            canvas.drawArc(wifiRect, start, sweep, false, shapePaint)
        }
        shapePaint.style = Paint.Style.FILL
        shapePaint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawBatteryGlyph(canvas: Canvas, rect: RectF, percent: Int, charging: Boolean) {
        val radius = dp(2.7f)
        val layer = canvas.saveLayer(rect.left - 1f, rect.top - 1f, rect.right + 1f, rect.bottom + 1f, null)
        val inner = RectF(rect)
        clip.reset()
        clip.addRoundRect(inner, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)

        // Empty capacity is always opaque gray. No outline or border is drawn.
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = Color.rgb(132, 136, 144)
        canvas.drawRoundRect(inner, radius, radius, shapePaint)

        val fill = percent.coerceIn(0, 100) / 100f
        shapePaint.color = if (charging) Color.rgb(42, 205, 91) else color
        canvas.drawRect(inner.left, inner.top, inner.left + inner.width() * fill, inner.bottom, shapePaint)
        canvas.restore()

        // Percentage remains a true transparent cut-out.
        val text = percent.coerceIn(0, 100).toString()
        batteryCutoutPaint.textSize = dp(if (text.length >= 3) 7.8f else 8.8f)
        batteryCutoutPaint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, rect.centerX(), rect.centerY() + bounds.height() / 2f, batteryCutoutPaint)
        canvas.restoreToCount(layer)
    }

    private fun drawAirplaneGlyph(canvas: Canvas, left: Float, cy: Float, size: Float) {
        shapePaint.color = color
        planeMatrix.reset()
        // Point right, toward the battery at the far-right edge of the status bar.
        planeMatrix.postRotate(90f, 12f, 12f)
        planeMatrix.postScale(size / 24f, size / 24f)
        planeMatrix.postTranslate(left, cy - size / 2f)
        planeWork.set(planePath)
        planeWork.transform(planeMatrix)
        canvas.drawPath(planeWork, shapePaint)
    }

    private fun drawBolt(canvas: Canvas, left: Float, cy: Float) {
        val h = dp(Metrics.BOLT_H)
        val u = dp(Metrics.BOLT_W) / 5f
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

        // Compact Wi-Fi dimensions
        // Compact battery dimensions
        const val BATTERY_W = 20f
        const val BATTERY_H = 9.5f
        const val NUB_W27 = 1.2f
        const val NUB_H27 = 3.2f
        const val NUB_GAP27 = 0.45f
        const val NUB_ALPHA27 = 125

        // bolt
        const val BOLT_W = 5f
        const val BOLT_H = 9f

        // orange activity dot
        const val DOT_R = 2.5f

        const val COLOR_ANIM_MS = 180L
    }
}
