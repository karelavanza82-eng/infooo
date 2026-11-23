package com.zfl.launcher.settings

import android.content.SharedPreferences

/**
 * Intent of the Control Center, Notification Center and Lock Screen. Access to notifications and
 * system settings is never stored here: it is read live from Android each time.
 */
data class PanelPreferences(
    /** Drag down from the top-right edge, or the bound home gesture, opens the Control Center. */
    val controlCenter: Boolean = true,
    /** Off by default: it is the only feature that reads the text of notifications. */
    val notificationCenter: Boolean = false,
    /** Cards show only the app name; titles and text are never copied while this is on. */
    val hideNotificationContent: Boolean = false,
) {
    companion object {
        private const val CONTROL_CENTER = "panels_control_center"
        private const val NOTIFICATION_CENTER = "panels_notification_center"
        private const val HIDE_CONTENT = "panels_hide_content"

        /** Missing or wrongly typed values fall back to the defaults. */
        fun fromStored(values: Map<String, *>): PanelPreferences {
            val defaults = PanelPreferences()
            fun flag(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
            return PanelPreferences(
                controlCenter = flag(CONTROL_CENTER, defaults.controlCenter),
                notificationCenter = flag(NOTIFICATION_CENTER, defaults.notificationCenter),
                hideNotificationContent = flag(HIDE_CONTENT, defaults.hideNotificationContent),
            )
        }
    }

    internal fun storedValues(): Map<String, Boolean> = mapOf(
        CONTROL_CENTER to controlCenter,
        NOTIFICATION_CENTER to notificationCenter,
        HIDE_CONTENT to hideNotificationContent,
    )
}

internal fun SharedPreferences.Editor.putPanels(value: PanelPreferences) {
    value.storedValues().forEach { (key, stored) -> putBoolean(key, stored) }
}
