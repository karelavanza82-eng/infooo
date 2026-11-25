package com.hoisly.launcher.statusbar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Everything the status bar shows. */
data class StatusBarState(
    val time: String = "",
    val silent: Boolean = false,
    val airplane: Boolean = false,
    /** Lit cellular bars, 0..4. */
    val signalLevel: Int = 0,
    /** "5G", "4G", "3G", "2G" or empty when unknown. */
    val networkLabel: String = "",
    /** The default network is mobile data (Wi-Fi is not carrying traffic). */
    val cellularData: Boolean = false,
    val wifiConnected: Boolean = false,
    /** Lit Wi-Fi arcs, 1..3. */
    val wifiLevel: Int = 3,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
)

/**
 * Live device data (clock, ringer, airplane mode, cellular signal and type, Wi-Fi, active data
 * network, battery), delivered on the main thread whenever something changes.
 */
class DeviceStatusMonitor(
    context: Context,
    private val onChange: (StatusBarState) -> Unit,
) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val telephony: TelephonyManager? = app.getSystemService(TelephonyManager::class.java)
    private val connectivity: ConnectivityManager? = app.getSystemService(ConnectivityManager::class.java)
    private val wifiManager: WifiManager? = app.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var state = StatusBarState(time = formatTime())
    private var started = false
    private var telephonyCallback: TelephonyCallback? = null
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var defaultCallback: ConnectivityManager.NetworkCallback? = null
    private var defaultNetwork: Network? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> applyBattery(intent)
                AudioManager.RINGER_MODE_CHANGED_ACTION -> state = state.copy(silent = isSilent())
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> state = state.copy(airplane = isAirplane())
                else -> state = state.copy(time = formatTime())
            }
            publish()
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val sticky = ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        state = state.copy(time = formatTime(), silent = isSilent(), airplane = isAirplane())
        if (sticky?.action == Intent.ACTION_BATTERY_CHANGED) applyBattery(sticky)
        publish()
        startTelephony()
        startConnectivity()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { app.unregisterReceiver(receiver) }
        stopTelephony()
        stopConnectivity()
    }

    /** Call after READ_PHONE_STATE is granted so the 5G/4G label can appear. */
    fun refreshTelephony() {
        if (!started) return
        stopTelephony()
        startTelephony()
    }

    private fun applyBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt().coerceIn(0, 100)
        else state.batteryPercent
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        state = state.copy(
            batteryPercent = percent,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    private fun isSilent(): Boolean = audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL

    private fun isAirplane(): Boolean =
        Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun formatTime(): String {
        val pattern = if (DateFormat.is24HourFormat(app)) "HH:mm" else "h:mm"
        return SimpleDateFormat(pattern, Locale.US).format(Date())
    }

    private fun publish() = onChange(state)

    // ---- Wi-Fi and active data network -----------------------------------------------------

    private fun startConnectivity() {
        val cm = connectivity ?: return
        runCatching {
            val wifi = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    state = state.copy(wifiConnected = true)
                    publish()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    state = state.copy(wifiConnected = true, wifiLevel = wifiArcs(caps.signalStrength))
                    publish()
                }

                override fun onLost(network: Network) {
                    state = state.copy(wifiConnected = false)
                    publish()
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifi, main,
            )
            wifiCallback = wifi

            val default = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    defaultNetwork = network
                    setCellularData(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                }

                override fun onLost(network: Network) {
                    // A newer default network may already be reported; only the current one can clear it.
                    if (network == defaultNetwork) {
                        defaultNetwork = null
                        setCellularData(false)
                    }
                }
            }
            cm.registerDefaultNetworkCallback(default, main)
            defaultCallback = default
        }
    }

    private fun stopConnectivity() {
        val cm = connectivity ?: return
        wifiCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        defaultCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        wifiCallback = null
        defaultCallback = null
        defaultNetwork = null
    }

    private fun setCellularData(active: Boolean) {
        if (state.cellularData == active) return
        state = state.copy(cellularData = active)
        publish()
    }

    /** RSSI (dBm) to 1..3 arcs; unknown strength counts as full. */
    private fun wifiArcs(rssi: Int): Int {
        if (rssi == Int.MIN_VALUE || rssi >= 0) return 3
        val manager = wifiManager ?: return 3
        return when (manager.calculateSignalLevel(rssi)) {
            0, 1 -> 1
            2 -> 2
            else -> 3
        }
    }

    // ---- cellular signal & network type ----------------------------------------------------

    private fun hasPhonePermission() =
        ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun startTelephony() {
        val tm = telephony ?: return
        try {
            val cb: TelephonyCallback = if (hasPhonePermission()) FullCallback() else SignalCallback()
            tm.registerTelephonyCallback(app.mainExecutor, cb)
            telephonyCallback = cb
        } catch (_: SecurityException) {
            // Signal stays at its last value and the network label stays hidden.
        }
    }

    private fun stopTelephony() {
        val cb = telephonyCallback ?: return
        runCatching { telephony?.unregisterTelephonyCallback(cb) }
        telephonyCallback = null
    }

    private fun onSignal(level: Int) {
        state = state.copy(signalLevel = level.coerceIn(0, 4))
        publish()
    }

    private inner class SignalCallback : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onSignal(signalStrength.level)
    }

    private inner class FullCallback : TelephonyCallback(),
        TelephonyCallback.SignalStrengthsListener,
        TelephonyCallback.DisplayInfoListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onSignal(signalStrength.level)

        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            val override = telephonyDisplayInfo.overrideNetworkType
            val label = if (
                override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
            ) "5G" else typeLabel(telephonyDisplayInfo.networkType)
            state = state.copy(networkLabel = label)
            publish()
        }
    }

    private fun typeLabel(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        else -> ""
    }
}
