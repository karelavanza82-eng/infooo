package com.hoisly.launcher.statusbar

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.akira.island.core.IslandBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Shows [StatusBarView] as a transparent, untouchable accessibility overlay across the whole system.
 * Lives inside the Dynamic Island accessibility service (the only window the launcher may add over
 * other apps) and follows [StatusBarStore]. The window has no background and never takes touches.
 */
class StatusBarOverlay(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val sampler = BackdropSampler(service) { onLuminance(it) }

    private var view: StatusBarView? = null
    private var monitor: DeviceStatusMonitor? = null
    private var prefs = StatusBarPreferences()
    private var blackIcons: Boolean? = null
    private var barHeight = 0
    private var jobs = emptyList<Job>()

    private val poll = object : Runnable {
        override fun run() {
            sampleNow()
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        StatusBarStore.init(service)
        jobs = listOf(
            scope.launch { StatusBarStore.state.collect { apply(it) } },
            scope.launch {
                combine(IslandBus.shadeOpen, IslandBus.keyguard, IslandBus.screenOn) { shade, keyguard, on ->
                    on && !shade && !keyguard
                }.collect { visibleByState = it; updateVisibility() }
            },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        detach()
        SystemIconHider.apply(service, hide = false)
        sampler.close()
    }

    /** Window or content changed in some app: re-measure the background soon. */
    fun onAccessibilityEvent() {
        if (view != null && prefs.colorMode == StatusBarColorMode.Auto) sampleNow()
        updateVisibility()
    }

    /** Called by the service when the user grants READ_PHONE_STATE, so the 5G label shows up. */
    fun refreshTelephony() {
        monitor?.refreshTelephony()
    }

    private var visibleByState = true

    private fun apply(next: StatusBarPreferences) {
        prefs = next
        if (!next.enabled) {
            detach()
            SystemIconHider.apply(service, hide = false)
            return
        }
        attach()
        view?.applyPrefs(next)
        SystemIconHider.apply(service, next.hideSystemIcons)
        when (next.colorMode) {
            StatusBarColorMode.Black -> setBlack(true)
            StatusBarColorMode.White -> setBlack(false)
            StatusBarColorMode.Auto -> sampleNow()
        }
        updateVisibility()
    }

    private fun attach() {
        if (view != null) return
        barHeight = statusBarHeightPx()
        val v = StatusBarView(service)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "HoislyStatusBar"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        if (runCatching { wm.addView(v, lp) }.isFailure) return
        view = v
        monitor = DeviceStatusMonitor(service) { v.state = it }.also { it.start() }
        handler.postDelayed(poll, POLL_MS)
    }

    private fun detach() {
        handler.removeCallbacks(poll)
        monitor?.stop()
        monitor = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        blackIcons = null
    }

    private fun updateVisibility() {
        val v = view ?: return
        val landscape = service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val show = visibleByState && !(prefs.hideInLandscape && landscape)
        val target = if (show) View.VISIBLE else View.INVISIBLE
        if (v.visibility != target) v.visibility = target
    }

    private fun sampleNow() {
        if (prefs.colorMode != StatusBarColorMode.Auto || view == null || !IslandBus.screenOn.value) return
        sampler.sample(barHeight)
    }

    private fun onLuminance(luminance: Float) {
        if (view == null || prefs.colorMode != StatusBarColorMode.Auto) return
        setBlack(IconContrast.blackIconsOn(luminance, blackIcons))
    }

    private fun setBlack(black: Boolean) {
        blackIcons = black
        view?.setBlackIcons(black, animate = true)
    }

    private fun statusBarHeightPx(): Int {
        val res = service.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        val px = if (id != 0) res.getDimensionPixelSize(id) else 0
        return if (px > 0) px else (28 * res.displayMetrics.density).toInt()
    }

    private companion object {
        /** Safety net for backgrounds that change without an accessibility event (scrolling, video). */
        const val POLL_MS = 1500L
    }
}
