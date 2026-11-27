package com.hoisly.launcher.statusbar

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Measures how bright the screen is right behind the status bar, whatever app is in front.
 * Uses the accessibility screenshot (the service already declares canTakeScreenshot), keeps only
 * the top strip and reports its median luminance on the main thread. Nothing is stored or sent.
 */
class BackdropSampler(
    private val service: AccessibilityService,
    private val onLuminance: (Float) -> Unit,
) {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    private var lastRequest = 0L

    /** [stripHeightPx] is the status bar height. Calls closer than [MIN_INTERVAL_MS] are dropped. */
    fun sample(stripHeightPx: Int) {
        val now = SystemClock.uptimeMillis()
        if (busy || now - lastRequest < MIN_INTERVAL_MS || stripHeightPx <= 0) return
        busy = true
        lastRequest = now
        val started = runCatching {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val luminance = runCatching { measure(result, stripHeightPx) }.getOrNull()
                    busy = false
                    if (luminance != null) main.post { onLuminance(luminance) }
                }

                override fun onFailure(errorCode: Int) {
                    busy = false
                }
            })
        }.isSuccess
        if (!started) busy = false
    }

    private fun measure(result: AccessibilityService.ScreenshotResult, stripHeightPx: Int): Float {
        val buffer = result.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: error("no bitmap")
            val soft = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            try {
                val w = soft.width
                val h = stripHeightPx.coerceIn(1, soft.height)
                val strip = IntArray(w * h)
                soft.getPixels(strip, 0, w, 0, 0, w, h)
                val picked = IntArray(((w + STEP_X - 1) / STEP_X) * ((h + STEP_Y - 1) / STEP_Y))
                var n = 0
                var y = 0
                while (y < h) {
                    var x = 0
                    while (x < w) {
                        picked[n++] = strip[y * w + x]
                        x += STEP_X
                    }
                    y += STEP_Y
                }
                return IconContrast.medianLuminance(picked.copyOf(n))
            } finally {
                soft.recycle()
            }
        } finally {
            buffer.close()
        }
    }

    fun close() {
        worker.shutdown()
    }

    private companion object {
        const val MIN_INTERVAL_MS = 400L
        const val STEP_X = 8
        const val STEP_Y = 4
    }
}
