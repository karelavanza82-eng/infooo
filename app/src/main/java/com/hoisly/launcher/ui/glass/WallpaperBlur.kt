package com.hoisly.launcher.ui.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.hoisly.launcher.ui.theme.LiquidGlassTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Wallpaper images for glass surfaces, laid out exactly like the system draws the wallpaper
 * on screen but downscaled. Glass surfaces sample their region from them.
 *
 * @param image blurred and saturated copy, [downscale] times smaller: the Frosted glass and
 *   the body of Liquid glass.
 * @param sharp unblurred copy, [sharpDownscale] times smaller: the refracting rim of Liquid
 *   glass. The same bitmap as the home blur backdrop, so it costs no extra memory.
 * @param luminance brightness of [image] for the adaptive tone of Liquid glass.
 * @param body the flat middle of Liquid glass, [sharpDownscale] times smaller: the sharp
 *   wallpaper mixed with a light blur of itself and saturated, all baked in once so the
 *   shader needs a single lookup there. Only made when Liquid glass is possible (Android 13+
 *   and [sharp] given).
 */
class GlassSource(
    val image: ImageBitmap,
    val downscale: Int,
    val sharp: ImageBitmap? = null,
    val sharpDownscale: Int = WallpaperBlur.PREVIEW_DOWNSCALE,
    val luminance: LuminanceMap? = null,
    val body: ImageBitmap? = null,
)

/**
 * Loads and blurs the system wallpaper for glass surfaces.
 *
 * The wallpaper is drawn by the system behind our transparent window, so we can't blur
 * "what's behind a view" directly. Instead we fetch the bitmap once, blur a small copy
 * and let every glass element draw its own slice of it (see [glass]).
 */
object WallpaperBlur {
    const val DOWNSCALE = 8
    /** Sharp wallpaper shared by previews, the full home backdrop and the Liquid glass rim; bounds bitmap memory. */
    const val PREVIEW_DOWNSCALE: Int = 3
    /** Blur radius in downscaled pixels (~56 screen px). */
    private const val RADIUS = 7
    /** Liquid glass scattering: a light blur (~13 screen px) that keeps the wallpaper recognisable. */
    private const val FROST_RADIUS = 4

    /** Saturation boost of the blurred copy; Liquid glass applies it to the sharp copy too. */
    const val SATURATION: Float = 1.6f

    /** True once a wallpaper was read successfully through WallpaperManager in this process. */
    @Volatile
    private var loadedOnce = false

    /** Why the last read failed (shown in logcat, tag HoislyGlass); null when it worked. */
    @Volatile
    var lastFailure: String? = null
        private set

    /**
     * Whether the system lets us read the wallpaper bitmap. Since Android 11 (and for good from
     * Android 13) `WallpaperManager.getDrawable()` throws SecurityException for a normal app, so
     * the only grant a launcher can ask for is All files access (MANAGE_EXTERNAL_STORAGE).
     * Some ROMs let the read through without it, so a read that already worked also counts.
     * UI uses this to decide whether to show the "allow wallpaper access" prompt.
     */
    fun hasAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || loadedOnce || Environment.isExternalStorageManager()

    /** Changes whenever the user sets a new wallpaper; -1 if unknown. */
    fun wallpaperId(context: Context): Int = runCatching {
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }.getOrDefault(-1)

    /**
     * Blurs the wallpaper for glass surfaces; [sharp] is the [loadPreview] image of the same
     * wallpaper, if any. Returns `null` only when WallpaperManager cannot provide an image.
     */
    suspend fun load(context: Context, sharp: ImageBitmap? = null): GlassSource? = withContext(Dispatchers.Default) {
        val small = render(context, DOWNSCALE, SATURATION) ?: return@withContext null
        val pixels = boxBlur(small, RADIUS, passes = 3)
        val luminance = LuminanceMap.fromArgb(pixels, small.width, small.height)
        val body = if (sharp != null && isLiquidGlassSupported(Build.VERSION.SDK_INT)) renderBody(context) else null
        GlassSource(small.asImageBitmap(), DOWNSCALE, sharp, PREVIEW_DOWNSCALE, luminance, body)
    }

    /**
     * Sharp wallpaper as it looks on the home screen, [downscale] times smaller.
     * Used by home backdrop blur and the live preview in settings. `null` when not readable.
     */
    suspend fun loadPreview(context: Context, downscale: Int = PREVIEW_DOWNSCALE): ImageBitmap? = withContext(Dispatchers.Default) {
        render(context, downscale, saturation = 1f)?.asImageBitmap()
    }

    /**
     * The wallpaper at full screen resolution, for the moving wallpaper. `null` when it is not
     * provided by WallpaperManager or too big for memory.
     */
    suspend fun loadFull(context: Context): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching { render(context, 1, saturation = 1f)?.asImageBitmap() }.getOrNull()
    }

    /** The body of Liquid glass: the saturated wallpaper with [LiquidGlassTokens.Frost] of a light blur mixed in. */
    private fun renderBody(context: Context): ImageBitmap? {
        val bitmap = render(context, PREVIEW_DOWNSCALE, SATURATION) ?: return null
        val w = bitmap.width
        val h = bitmap.height
        val sharp = IntArray(w * h)
        bitmap.getPixels(sharp, 0, w, 0, 0, w, h)
        val blurred = boxBlur(bitmap, FROST_RADIUS, passes = 3)
        val t = (LiquidGlassTokens.Frost * 256).toInt().coerceIn(0, 256)
        for (i in blurred.indices) blurred[i] = mixArgb(sharp[i], blurred[i], t)
        bitmap.setPixels(blurred, 0, w, 0, 0, w, h)
        return bitmap.asImageBitmap()
    }

    /** a + (b - a) × t / 256 per channel. */
    private fun mixArgb(a: Int, b: Int, t: Int): Int {
        var out = 0
        for (shift in 0..24 step 8) {
            val ca = (a ushr shift) and 0xFF
            val cb = (b ushr shift) and 0xFF
            out = out or ((ca + ((cb - ca) * t shr 8)) shl shift)
        }
        return out
    }

    /**
     * Reads the current wallpaper with every route the platform offers, newest first, and logs
     * the reason when none works instead of failing silently (the glass then stays Frosted).
     */
    private fun readWallpaper(context: Context): Drawable? {
        val manager = WallpaperManager.getInstance(context)
        val attempts = listOf<Pair<String, () -> Drawable?>>(
            "drawable" to { manager.drawable },
            "peekDrawable" to { manager.peekDrawable() },
        )
        for ((name, read) in attempts) {
            try {
                val d = read() ?: continue
                return d.toSoftwareDrawable(context)
            } catch (e: SecurityException) {
                lastFailure = "$name: SecurityException (all-files access missing?) ${e.message}"
            } catch (e: Throwable) {
                lastFailure = "$name: ${e.javaClass.simpleName} ${e.message}"
            }
        }
        if (lastFailure == null) lastFailure = "WallpaperManager returned no drawable (live wallpaper / vendor ROM)"
        Log.w(GLASS_TAG, "Wallpaper unreadable on API ${Build.VERSION.SDK_INT}: $lastFailure")
        return null
    }

    /** Some platform builds hand out HARDWARE bitmaps, which a software Canvas refuses to draw. */
    private fun Drawable.toSoftwareDrawable(context: Context): Drawable {
        val bitmap = (this as? BitmapDrawable)?.bitmap ?: return this
        if (bitmap.config != Bitmap.Config.HARDWARE) return this
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return this
        return BitmapDrawable(context.resources, copy)
    }

    /** Draws the wallpaper center-cropped to the screen, like the system does, [downscale] times smaller. */
    private fun render(context: Context, downscale: Int, saturation: Float): Bitmap? {
        val drawable = readWallpaper(context) ?: return null
        loadedOnce = true
        lastFailure = null

        val screen = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val sw = screen.width()
        val sh = screen.height()
        val ww = drawable.intrinsicWidth.takeIf { it > 0 } ?: sw
        val wh = drawable.intrinsicHeight.takeIf { it > 0 } ?: sh

        val outW = sw / downscale
        val outH = sh / downscale
        val scale = max(sw.toFloat() / ww, sh.toFloat() / wh) / downscale
        val dx = (outW - ww * scale) / 2f
        val dy = (outH - wh * scale) / 2f

        val small = createBitmap(outW, outH)
        Canvas(small).apply {
            translate(dx, dy)
            scale(scale, scale)
            drawable.setBounds(0, 0, ww, wh)
            if (saturation != 1f) {
                drawable.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
            }
            drawable.draw(this)
            drawable.colorFilter = null
        }
        return small
    }

    /** Three box blur passes approximate a gaussian. Cheap because the bitmap is tiny. Returns the blurred pixels. */
    private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        val tmp = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurLine(src, tmp, w, h, radius, horizontal = true)
            blurLine(tmp, src, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
        return src
    }

    private fun blurLine(input: IntArray, output: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = input[idx(i)]
                sa += p ushr 24; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                output[idx(i)] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = input[idx(i + r + 1)]
                val rem = input[idx(i - r)]
                sa += (add ushr 24) - (rem ushr 24)
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
