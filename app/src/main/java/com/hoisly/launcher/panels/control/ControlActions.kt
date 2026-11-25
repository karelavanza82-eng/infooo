package com.hoisly.launcher.panels.control

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.net.toUri

/*
 * System state and actions behind the Control Center. Android does not let an app switch
 * Wi-Fi, Bluetooth, mobile data or airplane mode, so those tiles show the state where it can
 * be read and open Android's own panel. Brightness and rotation need the "Modify system
 * settings" special access; without it the control sends the user to that page.
 */

internal fun Context.wifiOn(): Boolean = runCatching {
    getSystemService(WifiManager::class.java)?.isWifiEnabled == true
}.getOrDefault(false)

internal fun Context.bluetoothOn(): Boolean = runCatching {
    getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}.getOrDefault(false)

internal fun Context.airplaneOn(): Boolean =
    Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

internal fun Context.focusOn(): Boolean = runCatching {
    getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        .let { it != null && it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
}.getOrDefault(false)

internal fun Context.rotationLocked(): Boolean =
    Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

internal fun Context.canWriteSystemSettings(): Boolean = Settings.System.canWrite(this)

/** Opens the page where the user grants "Modify system settings". */
internal fun Context.requestWriteSettings(): Boolean =
    startQuietly(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:$packageName".toUri()))

/** Do Not Disturb as "Focus": priority only on, everything back on off. Asks for access first. */
internal fun Context.toggleFocus(): Boolean {
    val manager = getSystemService(NotificationManager::class.java) ?: return false
    if (!manager.isNotificationPolicyAccessGranted) {
        return startQuietly(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }
    val target = if (focusOn()) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
    return runCatching { manager.setInterruptionFilter(target) }.isSuccess
}

/** Rotation lock; returns false when the access to system settings is missing (the page is opened). */
internal fun Context.toggleRotationLock(): Boolean {
    if (!canWriteSystemSettings()) { requestWriteSettings(); return false }
    return runCatching {
        Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotationLocked()) 1 else 0)
    }.isSuccess
}

internal fun Context.brightnessFraction(): Float = brightnessToFraction(
    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS / 2),
)

/** Sets manual brightness; false when the access to system settings is missing. */
internal fun Context.setBrightnessFraction(fraction: Float): Boolean {
    if (!canWriteSystemSettings()) return false
    return runCatching {
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, fractionToBrightness(fraction))
    }.isSuccess
}

internal fun Context.volumeFraction(): Float {
    val audio = getSystemService(AudioManager::class.java) ?: return 0f
    return volumeToFraction(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
}

internal fun Context.setVolumeFraction(fraction: Float) {
    val audio = getSystemService(AudioManager::class.java) ?: return
    runCatching {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, fractionToVolume(fraction, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
    }
}

internal fun Context.openWifiPanel(): Boolean =
    startQuietly(Intent(Settings.Panel.ACTION_WIFI)) || startQuietly(Intent(Settings.ACTION_WIFI_SETTINGS))

internal fun Context.openMobileDataPanel(): Boolean =
    startQuietly(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) || startQuietly(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))

internal fun Context.openBluetoothSettings(): Boolean = startQuietly(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

internal fun Context.openAirplaneSettings(): Boolean = startQuietly(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

internal fun Context.openCalculator(): Boolean =
    startQuietly(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))

internal fun Context.openCamera(): Boolean = startQuietly(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
