package com.hoisly.launcher.statusbar

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import com.hoisly.launcher.R

/**
 * iOS-style status bar drawn on a Canvas: clock, muted bell, signal bars, network label and a
 * battery with its percentage knocked out of the fill. The view has no background at all.
 * All glyphs share one colour (black or white) that is animated when it flips.
 */
class StatusBarView(context: Context) : View(context) {

    var state: StatusBarState = StatusBarState()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

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
    private val batteryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) // punches the digits out of the battery
    }
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val gapXfer = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private val bounds = Rect()
    private val body = RectF()
    private val clip = Path()

    init {
        background = null
        setWillNotDraw(false)
        applyPrefs(prefs)
        bell?.setTint(color)
    }

    /** Applies size, spacing, font weight and visibility options. */
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

    /** true = black glyphs (light background); false = white glyphs (dark background). */
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

    /** Bell plus a slash with a transparent gap around it (like iOS bell.slash.fill). */
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
        var cursor = right
        if (prefs.showBattery) {
            val bw = dp(Metrics.BATTERY_W)
            val bh = dp(Metrics.BATTERY_H)
            val nubW = dp(Metrics.NUB_W)
            val nubH = dp(Metrics.NUB_H)
            val nubGap = dp(Metrics.NUB_GAP)
            val bodyRight = right - nubW - nubGap
            body.set(bodyRight - bw, cy - bh / 2f, bodyRight, cy + bh / 2f)
            shapePaint.color = ColorUtils.setAlphaComponent(color, Metrics.NUB_ALPHA)
            canvas.drawRoundRect(bodyRight + nubGap, cy - nubH / 2f, right, cy + nubH / 2f, nubW / 2f, nubW / 2f, shapePaint)
            drawBattery(canvas, body, state.batteryPercent)
            cursor = body.left - dp(Metrics.NET_TO_BATTERY_GAP)
        }
        if (prefs.showNetworkLabel && state.networkLabel.isNotEmpty()) {
            netPaint.color = color
            netPaint.getTextBounds("5", 0, 1, bounds)
            val tw = netPaint.measureText(state.networkLabel)
            canvas.drawText(state.networkLabel, cursor - tw, cy + bounds.height() / 2f, netPaint)
            cursor -= tw + dp(Metrics.SIGNAL_TO_NET_GAP)
        }
        if (prefs.showSignal) {
            val barW = dp(Metrics.BAR_W)
            val gap = dp(Metrics.BAR_GAP)
            drawSignal(canvas, cursor - (4 * barW + 3 * gap), cy, barW, gap, state.signalLevel)
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

    /** Filled part solid, the rest translucent, digits punched through to the background. */
    private fun drawBattery(canvas: Canvas, rect: RectF, percent: Int) {
        val radius = dp(Metrics.BATTERY_RADIUS)
        val layer = canvas.saveLayer(rect.left - 1f, rect.top - 1f, rect.right + 1f, rect.bottom + 1f, null)
        shapePaint.color = ColorUtils.setAlphaComponent(color, Metrics.TRACK_ALPHA)
        canvas.drawRoundRect(rect, radius, radius, shapePaint)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        shapePaint.color = color
        canvas.drawRect(rect.left, rect.top, rect.left + rect.width() * percent.coerceIn(0, 100) / 100f, rect.bottom, shapePaint)
        canvas.restore()
        val text = percent.toString()
        batteryTextPaint.textSize = dp(if (text.length >= 3) Metrics.BATTERY_TEXT_3 else Metrics.BATTERY_TEXT)
        batteryTextPaint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, rect.centerX(), rect.centerY() + bounds.height() / 2f, batteryTextPaint)
        canvas.restoreToCount(layer)
    }

    /** Inter ships with the launcher and is the closest bundled match to SF Pro. */
    private fun font(weight: Int): Typeface {
        val res = when {
            weight >= 700 -> R.font.inter_bold
            weight >= 600 -> R.font.inter_semibold
            weight >= 500 -> R.font.inter_medium
            else -> R.font.inter_regular
        }
        return ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT
    }

    /** Sizes in dp at scale 1.0, measured from the reference screenshot. */
    private object Metrics {
        const val FALLBACK_HEIGHT = 28f
        const val CLOCK_TEXT = 17f
        const val BELL_BOX = 16f
        const val BELL_GAP = 3.5f
        const val BAR_W = 3f
        const val BAR_GAP = 1.7f
        const val BAR_MAX_H = 9.6f
        const val BAR_MIN_H = 3f
        const val INACTIVE_ALPHA = 90
        const val NET_TEXT = 14f
        const val SIGNAL_TO_NET_GAP = 5.6f
        const val NET_TO_BATTERY_GAP = 6f
        const val BATTERY_W = 22f
        const val BATTERY_H = 11f
        const val BATTERY_RADIUS = 3.3f
        const val TRACK_ALPHA = 90
        const val NUB_W = 1.3f
        const val NUB_H = 4f
        const val NUB_GAP = 0.8f
        const val NUB_ALPHA = 100
        const val BATTERY_TEXT = 11.5f
        const val BATTERY_TEXT_3 = 9.5f
        const val COLOR_ANIM_MS = 180L
    }
}
