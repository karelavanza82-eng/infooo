package com.hoisly.launcher.settings

import android.content.SharedPreferences

/**
 * Intent of the top-edge panels and the Lock Screen. Access to notifications is never stored
 * here: it is read live from Android each time.
 */
data class PanelPreferences(
    /** Off by default: it is the only feature that reads the text of notifications. */
    val notificationCenter: Boolean = false,
    /** Cards show only the app name; titles and text are never copied while this is on. */
    val hideNotificationContent: Boolean = false,
    /** The Control Center, opened from the right part of the top edge. It reads no private data. */
    val controlCenter: Boolean = true,
    /** Lock Screen clock weight: 0 thin, 1 light, 2 regular, 3 bold. */
    val lockClockWeight: Int = 1,
    /** Lock Screen clock size relative to the default. */
    val lockClockScale: Float = 1f,
    /** Clock in the middle of the screen instead of the top. */
    val lockClockCentered: Boolean = false,
    val lockShowBattery: Boolean = true,
    /** Notification cards on the Lock Screen; they need the Notification Center turned on. */
    val lockShowNotifications: Boolean = true,
    /** The system wallpaper behind the Lock Screen (needs wallpaper access); otherwise a gradient. */
    val lockWallpaper: Boolean = true,
    /** 0 = sharp wallpaper, 1 = strongest dimming and softening. */
    val lockWallpaperBlur: Float = 0.25f,
) {
    /** Values outside their ranges are pulled back in. */
    fun sanitized(): PanelPreferences = copy(
        lockClockWeight = lockClockWeight.coerceIn(0, MAX_WEIGHT),
        lockClockScale = lockClockScale.coerceIn(MIN_SCALE, MAX_SCALE),
        lockWallpaperBlur = lockWallpaperBlur.coerceIn(0f, 1f),
    )

    companion object {
        const val MAX_WEIGHT: Int = 3
        const val MIN_SCALE: Float = 0.7f
        const val MAX_SCALE: Float = 1.3f

        private const val NOTIFICATION_CENTER = "panels_notification_center"
        private const val HIDE_CONTENT = "panels_hide_content"
        private const val CONTROL_CENTER = "panels_control_center"
        private const val LOCK_WEIGHT = "lock_clock_weight"
        private const val LOCK_SCALE = "lock_clock_scale"
        private const val LOCK_CENTERED = "lock_clock_centered"
        private const val LOCK_BATTERY = "lock_show_battery"
        private const val LOCK_NOTIFICATIONS = "lock_show_notifications"
        private const val LOCK_WALLPAPER = "lock_wallpaper"
        private const val LOCK_BLUR = "lock_wallpaper_blur"

        /** Missing or wrongly typed values fall back to the defaults. */
        fun fromStored(values: Map<String, *>): PanelPreferences {
            val defaults = PanelPreferences()
            fun flag(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
            fun number(key: String, default: Int): Int = values[key] as? Int ?: default
            fun fraction(key: String, default: Float): Float = values[key] as? Float ?: default
            return PanelPreferences(
                notificationCenter = flag(NOTIFICATION_CENTER, defaults.notificationCenter),
                hideNotificationContent = flag(HIDE_CONTENT, defaults.hideNotificationContent),
                controlCenter = flag(CONTROL_CENTER, defaults.controlCenter),
                lockClockWeight = number(LOCK_WEIGHT, defaults.lockClockWeight),
                lockClockScale = fraction(LOCK_SCALE, defaults.lockClockScale),
                lockClockCentered = flag(LOCK_CENTERED, defaults.lockClockCentered),
                lockShowBattery = flag(LOCK_BATTERY, defaults.lockShowBattery),
                lockShowNotifications = flag(LOCK_NOTIFICATIONS, defaults.lockShowNotifications),
                lockWallpaper = flag(LOCK_WALLPAPER, defaults.lockWallpaper),
                lockWallpaperBlur = fraction(LOCK_BLUR, defaults.lockWallpaperBlur),
            ).sanitized()
        }
    }

    internal fun storedValues(): Map<String, Any> = mapOf(
        NOTIFICATION_CENTER to notificationCenter,
        HIDE_CONTENT to hideNotificationContent,
        CONTROL_CENTER to controlCenter,
        LOCK_WEIGHT to lockClockWeight,
        LOCK_SCALE to lockClockScale,
        LOCK_CENTERED to lockClockCentered,
        LOCK_BATTERY to lockShowBattery,
        LOCK_NOTIFICATIONS to lockShowNotifications,
        LOCK_WALLPAPER to lockWallpaper,
        LOCK_BLUR to lockWallpaperBlur,
    )
}

internal fun SharedPreferences.Editor.putPanels(value: PanelPreferences) {
    value.sanitized().storedValues().forEach { (key, stored) ->
        when (stored) {
            is Boolean -> putBoolean(key, stored)
            is Int -> putInt(key, stored)
            is Float -> putFloat(key, stored)
        }
    }
}
