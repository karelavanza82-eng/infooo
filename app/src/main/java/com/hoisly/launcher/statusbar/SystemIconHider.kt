package com.hoisly.launcher.statusbar

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Optional and experimental: asks SystemUI to hide the slots the custom bar replaces (clock, cellular,
 * battery, ringer) so they do not show twice. Needs WRITE_SECURE_SETTINGS, which Android only lets
 * the user grant over adb. Some vendor SystemUIs ignore the setting. The previous value is saved
 * and put back when this is turned off or the service stops.
 */
object SystemIconHider {
    private const val KEY = "icon_blacklist"
    private val SLOTS = listOf("clock", "mobile", "battery", "volume")
    const val PERMISSION: String = "android.permission.WRITE_SECURE_SETTINGS"

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun apply(context: Context, hide: Boolean) {
        if (!canWrite(context)) return
        val resolver = context.contentResolver
        val store = StatusBarStore.sharedPreferences() ?: return
        runCatching {
            val current = Settings.Secure.getString(resolver, KEY).orEmpty()
            if (hide) {
                if (!store.contains(StatusBarStore.BACKUP_KEY)) {
                    store.edit().putString(StatusBarStore.BACKUP_KEY, current).apply()
                }
                val merged = (current.split(',').filter { it.isNotBlank() } + SLOTS).distinct().joinToString(",")
                if (merged != current) Settings.Secure.putString(resolver, KEY, merged)
            } else {
                val backup = store.getString(StatusBarStore.BACKUP_KEY, null) ?: return
                Settings.Secure.putString(resolver, KEY, backup)
                store.edit().remove(StatusBarStore.BACKUP_KEY).apply()
            }
        }
    }
}
