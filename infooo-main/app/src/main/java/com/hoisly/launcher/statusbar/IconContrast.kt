package com.hoisly.launcher.statusbar

/** Pure helpers that choose black or white glyphs for a measured background. No Android types, so they are unit-tested. */
object IconContrast {
    /** Contrast ratio change that has to be exceeded before the colour flips again (prevents flicker on mid-greys). */
    const val HYSTERESIS: Float = 1.15f

    private val linear = FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

    /** WCAG relative luminance (0..1) of an ARGB colour. */
    fun luminance(argb: Int): Float {
        val r = linear[(argb shr 16) and 0xFF]
        val g = linear[(argb shr 8) and 0xFF]
        val b = linear[argb and 0xFF]
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Median luminance of [pixels]. The median ignores the thin glyphs of the status bar itself and
     * the camera island, which would skew an average.
     */
    fun medianLuminance(pixels: IntArray): Float {
        if (pixels.isEmpty()) return 1f
        val values = FloatArray(pixels.size) { luminance(pixels[it]) }
        values.sort()
        return values[values.size / 2]
    }

    /**
     * True when BLACK glyphs read better on a background of [luminance]. Picks whichever of black
     * and white has the higher WCAG contrast; [previousBlack] adds hysteresis.
     */
    fun blackIconsOn(luminance: Float, previousBlack: Boolean?): Boolean {
        val l = luminance.coerceIn(0f, 1f)
        val contrastBlack = (l + 0.05f) / 0.05f
        val contrastWhite = 1.05f / (l + 0.05f)
        return when (previousBlack) {
            null -> contrastBlack >= contrastWhite
            true -> contrastWhite <= contrastBlack * HYSTERESIS
            false -> contrastBlack > contrastWhite * HYSTERESIS
        }
    }
}
