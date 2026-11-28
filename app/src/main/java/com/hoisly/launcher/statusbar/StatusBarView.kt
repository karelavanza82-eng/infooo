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
    private val wifiPath = Path()

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
        netPaint.textSize = u(Metrics.NET_TEXT)
        netPaint.typeface = font(600)
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

    /**
     * One reference unit. Every right-hand icon is specified in units where the battery is 36 tall
     * (measured from the iOS reference), so signal, Wi-Fi and battery always keep the same balance.
     */
    private fun u(v: Float) = dp(Metrics.ICON_H) * v / 36f

    private fun drawRight(canvas: Canvas, right: Float, cy: Float) {
        val st = state
        var cursor = right
        val gap = u(Metrics.ICON_GAP)

        // Battery: pill that fills from the left, gray remainder, percentage cut out of the pill.
        // Charging turns the filled part green; the bolt sits to the RIGHT of the nub.
        if (prefs.showBattery) {
            val bw = u(Metrics.BATT_W)
            val bh = u(Metrics.BATT_H)
            val nubW = u(Metrics.NUB_W)
            val nubH = u(Metrics.NUB_H)
            val nubGap = u(Metrics.NUB_GAP)
            val boltW = if (st.charging) u(Metrics.BOLT_W) else 0f
            val boltGap = if (st.charging) u(Metrics.BOLT_GAP) else 0f
            val bodyRight = right - boltW - boltGap - nubW - nubGap
            body.set(bodyRight - bw, cy - bh / 2f, bodyRight, cy + bh / 2f)

            shapePaint.style = Paint.Style.FILL
            shapePaint.color = ColorUtils.setAlphaComponent(color, Metrics.NUB_ALPHA)
            val nubLeft = bodyRight + nubGap
            canvas.drawRoundRect(nubLeft, cy - nubH / 2f, nubLeft + nubW, cy + nubH / 2f,
                nubW / 2f, nubW / 2f, shapePaint)

            drawBatteryGlyph(canvas, body, st.batteryPercent, st.charging)
            if (st.charging) drawBolt(canvas, right - boltW, cy)
            cursor = body.left - gap
        }

        // --- Orange activity dot ---
        if (showActivityDot) {
            val dotR = dp(Metrics.DOT_R)
            dotPaint.color = Color.parseColor("#FF9500")
            canvas.drawCircle(cursor - dotR, cy, dotR, dotPaint)
            cursor -= dotR * 2 + gap
        }

        // --- Wi-Fi, or the 4G/5G label in the same slot when there is no Wi-Fi ---
        val showWifi = prefs.showWifi && st.wifiConnected
        if (showWifi) {
            val w = u(Metrics.WIFI_W)
            drawWifiGlyph(canvas, cursor - w, cy, st.wifiLevel)
            cursor -= w + gap
        } else if (prefs.showNetworkLabel && st.cellularData && !st.airplane && st.networkLabel.isNotEmpty()) {
            netPaint.color = color
            netPaint.textSize = u(Metrics.NET_TEXT)
            netPaint.getTextBounds("5", 0, 1, bounds)
            val tw = netPaint.measureText(st.networkLabel)
            canvas.drawText(st.networkLabel, cursor - tw, cy + bounds.height() / 2f, netPaint)
            cursor -= tw + gap
        }

        // --- Airplane mode, or cellular bars ---
        if (st.airplane) {
            val size = u(Metrics.PLANE)
            drawAirplaneGlyph(canvas, cursor - size, cy, size)
        } else if (prefs.showSignal) {
            val barW = u(Metrics.BAR_W)
            val barGap = u(Metrics.BAR_GAP)
            drawSignalGlyph(canvas, cursor - (4 * barW + 3 * barGap), cy, barW, barGap, st.signalLevel)
        }
    }

    private fun drawSignalGlyph(canvas: Canvas, left: Float, cy: Float, barW: Float, gap: Float, level: Int) {
        // Heights step evenly from 12 to 32 units; the bars share one baseline, centred on the row.
        val bottom = cy + u(Metrics.BAR_HEIGHTS.last()) / 2f
        val radius = minOf(u(Metrics.BAR_RADIUS), barW / 2f)
        shapePaint.style = Paint.Style.FILL
        for (i in 0 until 4) {
            val h = u(Metrics.BAR_HEIGHTS[i])
            val x = left + i * (barW + gap)
            shapePaint.color = if (i < level) color else ColorUtils.setAlphaComponent(color, Metrics.INACTIVE_ALPHA)
            canvas.drawRoundRect(x, bottom - h, x + barW, bottom, radius, radius, shapePaint)
        }
    }

    /** Fan with a filled wedge and two thick round-capped arcs, all concentric on the bottom apex. */
    private fun drawWifiGlyph(canvas: Canvas, left: Float, cy: Float, level: Int) {
        val cx = left + u(Metrics.WIFI_W) / 2f
        val apexY = cy + u(Metrics.WIFI_H) / 2f
        val stroke = u(Metrics.WIFI_STROKE)
        val gap = u(Metrics.WIFI_GAP)
        val wedgeR = u(Metrics.WIFI_WEDGE)
        val dim = ColorUtils.setAlphaComponent(color, Metrics.INACTIVE_ALPHA)
        val start = 270f - Metrics.WIFI_HALF_ANGLE
        val sweep = Metrics.WIFI_HALF_ANGLE * 2f

        // Wedge: slightly inset and stroked with a round join so the tip is softly rounded.
        val soften = u(2.4f)
        val r = wedgeR - soften / 2f
        wifiRect.set(cx - r, apexY - r, cx + r, apexY + r)
        wifiPath.reset()
        wifiPath.moveTo(cx, apexY)
        wifiPath.arcTo(wifiRect, start, sweep, false)
        wifiPath.close()
        shapePaint.style = Paint.Style.FILL_AND_STROKE
        shapePaint.strokeWidth = soften
        shapePaint.strokeJoin = Paint.Join.ROUND
        shapePaint.color = if (level >= 1) color else dim
        canvas.drawPath(wifiPath, shapePaint)

        shapePaint.style = Paint.Style.STROKE
        shapePaint.strokeCap = Paint.Cap.ROUND
        shapePaint.strokeWidth = stroke
        for (i in 1..2) {
            val mid = wedgeR + gap * i + stroke * (i - 0.5f)
            shapePaint.color = if (level >= i + 1) color else dim
            wifiRect.set(cx - mid, apexY - mid, cx + mid, apexY + mid)
            canvas.drawArc(wifiRect, start, sweep, false, shapePaint)
        }
        shapePaint.style = Paint.Style.FILL
        shapePaint.strokeCap = Paint.Cap.BUTT
        shapePaint.strokeJoin = Paint.Join.MITER
        shapePaint.strokeWidth = 0f
    }

    private fun drawBatteryGlyph(canvas: Canvas, rect: RectF, percent: Int, charging: Boolean) {
        val radius = u(Metrics.BATT_RADIUS)
        val layer = canvas.saveLayer(rect.left - 1f, rect.top - 1f, rect.right + 1f, rect.bottom + 1f, null)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)

        // Empty capacity is opaque gray; no outline is drawn.
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = Metrics.EMPTY_GRAY
        canvas.drawRect(rect, shapePaint)

        val fill = percent.coerceIn(0, 100) / 100f
        shapePaint.color = if (charging) Metrics.CHARGING_GREEN else color
        canvas.drawRect(rect.left, rect.top, rect.left + rect.width() * fill, rect.bottom, shapePaint)
        canvas.restore()

        // Percentage is a true transparent cut-out of the pill.
        val text = percent.coerceIn(0, 100).toString()
        batteryCutoutPaint.textSize = u(if (text.length >= 3) Metrics.BATT_TEXT_3 else Metrics.BATT_TEXT)
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
        val w = u(Metrics.BOLT_W)
        val h = u(Metrics.BOLT_H)
        val top = cy - h / 2f
        boltWork.reset()
        boltWork.moveTo(left + w * 0.62f, top)
        boltWork.lineTo(left, top + h * 0.58f)
        boltWork.lineTo(left + w * 0.45f, top + h * 0.58f)
        boltWork.lineTo(left + w * 0.30f, top + h)
        boltWork.lineTo(left + w, top + h * 0.40f)
        boltWork.lineTo(left + w * 0.55f, top + h * 0.40f)
        boltWork.close()
        shapePaint.style = Paint.Style.FILL
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

    /**
     * Proportions measured from the iOS reference. Units are "battery height = 36"; [ICON_H] is the
     * battery height in dp. Signal (32), Wi-Fi (34) and battery (36) are therefore the same height.
     */
    private object Metrics {
        const val FALLBACK_HEIGHT = 28f
        const val CLOCK_TEXT = 16f
        const val BELL_BOX = 15f
        const val BELL_GAP = 3.5f

        const val ICON_H = 12.5f
        const val ICON_GAP = 15.5f
        const val INACTIVE_ALPHA = 62

        // cellular bars: 8 wide, 5 apart, heights 12/18/25/32
        const val BAR_W = 8f
        const val BAR_GAP = 5f
        const val BAR_RADIUS = 3.2f
        val BAR_HEIGHTS = floatArrayOf(12f, 18f, 25f, 32f)

        // 4G/5G label shares the Wi-Fi slot; cap height stays below the signal height
        const val NET_TEXT = 33f

        // Wi-Fi fan
        const val WIFI_W = 46f
        const val WIFI_H = 34f
        const val WIFI_STROKE = 7f
        const val WIFI_GAP = 4.5f
        const val WIFI_WEDGE = 11f
        const val WIFI_HALF_ANGLE = 40f

        // battery
        const val BATT_W = 62f
        const val BATT_H = 36f
        const val BATT_RADIUS = 10f
        const val BATT_TEXT = 29f
        const val BATT_TEXT_3 = 22f
        const val NUB_W = 3.4f
        const val NUB_H = 8f
        const val NUB_GAP = 4f
        const val NUB_ALPHA = 125
        val EMPTY_GRAY = Color.rgb(132, 136, 144)
        val CHARGING_GREEN = Color.rgb(48, 209, 88)

        // bolt (charging)
        const val BOLT_W = 9f
        const val BOLT_GAP = 5f
        const val BOLT_H = 22f

        const val PLANE = 40f

        // orange activity dot
        const val DOT_R = 2.5f

        const val COLOR_ANIM_MS = 180L
    }
}
