package com.zfl.launcher.panels.control

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * What the Control Center shows. Every value is read from Android; nothing is remembered
 * here, so a tile can never show a state the system does not have.
 */
data class ControlState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val airplane: Boolean = false,
    /** The mobile data switch of the system; read without any permission. */
    val mobileData: Boolean = false,
    val darkMode: Boolean = false,
    val torch: Boolean = false,
    val torchAvailable: Boolean = false,
    val rotationLocked: Boolean = false,
    val focus: Boolean = false,
    val brightness: Float = 0.5f,
    val volume: Float = 0f,
    /** "Modify system settings" is granted: brightness and rotation lock can be written. */
    val canWriteSettings: Boolean = false,
    /** "Do Not Disturb access" is granted: Focus can be switched. */
    val canChangeFocus: Boolean = false,
    /** Name of the connected Wi-Fi network; null when unknown (Android hides it without a location grant). */
    val wifiName: String? = null,
    /** The ringer is off or on vibrate. */
    val silent: Boolean = false,
    /** Battery Saver is on. */
    val lowPower: Boolean = false,
)

/** What a control needs from the user before it can work. */
enum class ControlGrant { WriteSettings, FocusAccess }

/**
 * Reads and changes the system state behind the Control Center tiles, within what Android lets
 * a launcher do. Wi-Fi, Bluetooth and Airplane mode cannot be switched by an app since Android
 * 10/13, so they are read live and opened in the system's own panel instead of being faked.
 */
class SystemControls(context: Context) {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val audio = this.context.getSystemService(AudioManager::class.java)
    private val camera = this.context.getSystemService(CameraManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)
    private val wifiManager = this.context.getSystemService(WifiManager::class.java)
    private val bluetoothAdapter = this.context.getSystemService(BluetoothManager::class.java)?.adapter
    private val powerManager = this.context.getSystemService(PowerManager::class.java)
    private val torchId: String? by lazy { findTorchCamera() }
    private var torchOn = false

    private val mutable = MutableStateFlow(ControlState())
    val state: StateFlow<ControlState> = mutable

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != torchId) return
            torchOn = enabled
            refresh()
        }
    }

    /** The torch has no readable state, only callbacks: register while a panel is visible. */
    fun start() {
        runCatching { camera.registerTorchCallback(torchCallback, null) }
        refresh()
    }

    fun stop() {
        runCatching { camera.unregisterTorchCallback(torchCallback) }
    }

    fun refresh() {
        mutable.value = ControlState(
            wifi = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false),
            bluetooth = runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false),
            airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            mobileData = Settings.Global.getInt(resolver, "mobile_data", 1) == 1,
            darkMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
            torch = torchOn,
            torchAvailable = torchId != null,
            rotationLocked = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
            focus = notifications.currentInterruptionFilter.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            },
            brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) / MAX_BRIGHTNESS,
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            canWriteSettings = Settings.System.canWrite(context),
            canChangeFocus = notifications.isNotificationPolicyAccessGranted,
            wifiName = readWifiName(),
            silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL,
            lowPower = runCatching { powerManager.isPowerSaveMode }.getOrDefault(false),
        )
    }

    @Suppress("DEPRECATION")
    private fun readWifiName(): String? = runCatching {
        if (!wifiManager.isWifiEnabled) return null
        wifiManager.connectionInfo?.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }.getOrNull()

    /** Silent mode: ringer off the way an iPhone's switch does it, here as vibrate (always allowed). */
    fun toggleSilent() {
        val next = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        runCatching { audio.ringerMode = next }.onFailure { Log.w(TAG, "ringer failed", it) }
        refresh()
    }

    fun setTorch(on: Boolean) {
        val id = torchId ?: return
        runCatching { camera.setTorchMode(id, on) }.onFailure { Log.w(TAG, "torch failed", it) }
    }

    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt().coerceIn(0, max), 0)
        refresh()
    }

    /** Returns the missing grant instead of writing when "Modify system settings" is off. */
    fun setBrightness(fraction: Float): ControlGrant? {
        if (!Settings.System.canWrite(context)) return ControlGrant.WriteSettings
        // A manual level only sticks when adaptive brightness is not overriding it.
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (fraction * MAX_BRIGHTNESS).roundToInt().coerceIn(1, MAX_BRIGHTNESS.toInt()))
        refresh()
        return null
    }

    fun toggleRotationLock(): ControlGrant? {
        if (!Settings.System.canWrite(context)) return ControlGrant.WriteSettings
        val locked = mutable.value.rotationLocked
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0)
        refresh()
        return null
    }

    fun toggleFocus(): ControlGrant? {
        if (!notifications.isNotificationPolicyAccessGranted) return ControlGrant.FocusAccess
        notifications.setInterruptionFilter(
            if (mutable.value.focus) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        refresh()
        return null
    }

    /** The screen where the user grants [grant]; the grant itself can only be given there. */
    fun grantIntent(grant: ControlGrant): Intent = when (grant) {
        ControlGrant.WriteSettings -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
        ControlGrant.FocusAccess -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    private fun findTorchCamera(): String? = runCatching {
        camera.cameraIdList.firstOrNull { id ->
            val c = camera.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private companion object {
        const val TAG = "ZflControls"
        const val MAX_BRIGHTNESS = 255f
    }
}
