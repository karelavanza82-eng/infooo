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
 * iOS 27-style status bar: clock, muted bell, signal pills, WiFi (two bold arcs + rounded wedge),
 * airplane (classic solid glyph pointing right, like iOS) and battery (solid track, flush fill, small nub).
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

    // Classic iOS airplane glyph, nose pointing right. Traced from the reference screenshot,
    // box 24 x 21.47 (width x height).
    private val planePath: Path = PathParser.createPathFromPathData(
        "M0,5.42 L1.4,5.3 L3.55,8.73 L8.26,8.42 L8.73,7.87 L5.3,1.09 L5.42,0 L7.32,0 " +
            "L15.39,8.42 L21.66,8.42 C23.3,8.42 24,9.3 24,10.74 C24,12.18 23.3,13.05 21.04,13.05 " +
            "L15.08,13.05 L7.32,21.47 L5.42,21.47 L8.42,13.21 L3.55,12.43 L2.14,15.12 L0.43,16.17 Z",
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
     * iOS 27 Wi-Fi: a 90 degree fan made of two bold arcs with round ends and a small rounded wedge
     * at the bottom. [width] is the visible width of the icon; proportions follow the reference.
     */
    private fun drawWifi27(canvas: Canvas, left: Float, cy: Float, width: Float, level: Int) {
        val outer = width / 1.40f              // outer radius of the biggest arc, from the apex
        val cx = left + width / 2f
        val apexY = cy + outer * 0.50f
        val ring = outer * 0.19f
        val gap = outer * 0.115f
        val dim = ColorUtils.setAlphaComponent(color, Metrics.INACTIVE_ALPHA)
        val sweep = 90f
        val startAngle = 225f

        // two arcs, stroked, with round ends (level 3 = outer, level 2 = middle)
        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeCap = Paint.Cap.ROUND
        shapePaint.strokeWidth = ring
        val mid2 = outer - ring / 2f
        val mid1 = outer - ring - gap - ring / 2f
        val rings = floatArrayOf(mid2, mid1)
        val needed = intArrayOf(4, 3)
        for (i in rings.indices) {
            val r = rings[i]
            shapePaint.color = if (level >= needed[i]) color else dim
            wifiRect.set(cx - r, apexY - r, cx + r, apexY + r)
            canvas.drawArc(wifiRect, startAngle, sweep, false, shapePaint)
        }

        // bottom wedge: filled sector with softly rounded corners
        val wedge = mid1 - ring / 2f - gap
        val soft = outer * 0.06f
        val r = wedge - soft / 2f
        shapePaint.color = if (level >= 1) color else dim
        shapePaint.strokeJoin = Paint.Join.ROUND
        shapePaint.strokeWidth = soft
        shapePaint.style = Paint.Style.FILL_AND_STROKE
        wifiRect.set(cx - r, apexY - r, cx + r, apexY + r)
        canvas.drawArc(wifiRect, startAngle, sweep, true, shapePaint)

        shapePaint.style = Paint.Style.FILL
        shapePaint.strokeCap = Paint.Cap.BUTT
        shapePaint.strokeJoin = Paint.Join.MITER
    }

    /** Classic solid airplane glyph pointing right, exactly as iOS draws airplane mode. */
    private fun drawAirplane27(canvas: Canvas, left: Float, cy: Float, size: Float) {
        shapePaint.color = color
        shapePaint.style = Paint.Style.FILL
        val scale = size / Metrics.PLANE_SRC_W
        planeMatrix.reset()
        planeMatrix.postScale(scale, scale)
        planeMatrix.postTranslate(left, cy - Metrics.PLANE_SRC_H * scale / 2f)
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
     * iOS 27 battery: a big rounded body with a solid translucent track, the charge drawn flush
     * from the left edge (no inner gap) and no number inside, like the reference.
     */
    private fun drawBattery27(canvas: Canvas, rect: RectF, percent: Int) {
        val radius = dp(Metrics.BATTERY_RADIUS27)
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = ColorUtils.setAlphaComponent(color, Metrics.TRACK_ALPHA27)
        canvas.drawRoundRect(rect, radius, radius, shapePaint)

        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        shapePaint.color = color
        val fillRight = rect.left + rect.width() * percent.coerceIn(0, 100) / 100f
        canvas.drawRect(rect.left, rect.top, fillRight, rect.bottom, shapePaint)
        canvas.restore()
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
        const val BAR_W = 3.1f
        const val BAR_GAP = 2.2f
        const val BAR_MAX_H = 12f
        const val BAR_MIN_H = 4.8f
        const val INACTIVE_ALPHA = 85

        const val NET_TEXT = 15.5f

        // iOS 27 WiFi — wider, bolder
        const val WIFI_W27 = 18f

        // iOS 27 Airplane — larger, bolder
        const val PLANE27 = 17f

        // iOS 27 Battery — pill with thick outline, larger nub
        const val BATTERY_W = 27f
        const val BATTERY_H = 13f
        const val BATTERY_RADIUS27 = 4.2f
        const val TRACK_ALPHA27 = 80
        const val NUB_W27 = 2.4f
        const val NUB_H27 = 4.6f
        const val NUB_GAP27 = 1.1f
        const val NUB_ALPHA27 = 80
        const val PLANE_SRC_W = 24f
        const val PLANE_SRC_H = 21.47f

        // bolt
        const val BOLT_W = 6f
        const val BOLT_H = 11f

        // orange activity dot
        const val DOT_R = 2.5f

        const val COLOR_ANIM_MS = 180L
    }
}
