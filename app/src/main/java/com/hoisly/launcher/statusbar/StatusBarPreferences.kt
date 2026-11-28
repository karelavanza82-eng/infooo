package com.hoisly.launcher.statusbar

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How the status bar picks the colour of its glyphs. */
enum class StatusBarColorMode { Auto, Black, White }

/**
 * Everything the user can change about the custom status bar. Only intent and look are
 * persisted; whether the overlay can actually be shown depends on live grants.
 */
data class StatusBarPreferences(
    val enabled: Boolean = false,
    val colorMode: StatusBarColorMode = StatusBarColorMode.Auto,
    val showSilent: Boolean = true,
    val showSignal: Boolean = true,
    val showNetworkLabel: Boolean = true,
    val showWifi: Boolean = true,
    val showBattery: Boolean = true,
    val hideInLandscape: Boolean = true,
    /** Experimental: asks SystemUI to hide its own clock/signal/battery slots (needs WRITE_SECURE_SETTINGS). */
    val hideSystemIcons: Boolean = false,
    val scale: Float = DEFAULT_SCALE,
    val sidePadding: Float = DEFAULT_SIDE_PADDING,
    val verticalOffset: Float = 0f,
    /** 400 regular, 500 medium, 600 semibold, 700 bold. */
    val clockWeight: Int = DEFAULT_CLOCK_WEIGHT,
    /** Legacy theme values are retained only to read older preferences; the UI now uses one fixed icon family. */
    val batteryTheme: Int = 2,
    val wifiTheme: Int = 2,
    val signalTheme: Int = 2,
    val clockTheme: Int = 2,
    val airplaneTheme: Int = 2,
) {
    fun storedValues(): Map<String, Any> = mapOf(
        ENABLED to enabled, COLOR_MODE to colorMode.name, SHOW_SILENT to showSilent,
        SHOW_SIGNAL to showSignal, SHOW_NETWORK to showNetworkLabel, SHOW_WIFI to showWifi, SHOW_BATTERY to showBattery,
        HIDE_LANDSCAPE to hideInLandscape, HIDE_SYSTEM to hideSystemIcons, SCALE to scale,
        SIDE_PADDING to sidePadding, VERTICAL_OFFSET to verticalOffset, CLOCK_WEIGHT to clockWeight,
        BATTERY_THEME to batteryTheme, WIFI_THEME to wifiTheme, SIGNAL_THEME to signalTheme, CLOCK_THEME to clockTheme, AIRPLANE_THEME to airplaneTheme,
    )

    companion object {
        const val DEFAULT_SCALE: Float = 1f
        const val DEFAULT_SIDE_PADDING: Float = 22f
        const val DEFAULT_CLOCK_WEIGHT: Int = 600
        val SCALE_RANGE: ClosedFloatingPointRange<Float> = 0.8f..1.4f
        val SIDE_PADDING_RANGE: ClosedFloatingPointRange<Float> = 8f..48f
        val VERTICAL_OFFSET_RANGE: ClosedFloatingPointRange<Float> = -10f..10f
        val CLOCK_WEIGHTS: List<Int> = listOf(400, 500, 600, 700)

        private const val ENABLED = "sb_enabled"
        private const val COLOR_MODE = "sb_color_mode"
        private const val SHOW_SILENT = "sb_show_silent"
        private const val SHOW_SIGNAL = "sb_show_signal"
        private const val SHOW_NETWORK = "sb_show_network"
        private const val SHOW_WIFI = "sb_show_wifi"
        private const val SHOW_BATTERY = "sb_show_battery"
        private const val HIDE_LANDSCAPE = "sb_hide_landscape"
        private const val HIDE_SYSTEM = "sb_hide_system"
        private const val SCALE = "sb_scale"
        private const val SIDE_PADDING = "sb_side_padding"
        private const val VERTICAL_OFFSET = "sb_vertical_offset"
        private const val CLOCK_WEIGHT = "sb_clock_weight"
        private const val BATTERY_THEME = "sb_battery_theme"
        private const val WIFI_THEME = "sb_wifi_theme"
        private const val SIGNAL_THEME = "sb_signal_theme"
        private const val CLOCK_THEME = "sb_clock_theme"
        private const val AIRPLANE_THEME = "sb_airplane_theme"
        val ICON_THEMES: List<Int> = (0..4).toList()

        /** Missing, legacy or wrongly typed values fall back to defaults; the overlay is never on by accident. */
        fun fromStored(values: Map<String, *>): StatusBarPreferences {
            val d = StatusBarPreferences()
            fun flag(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
            fun number(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float =
                (values[key] as? Float)?.takeIf { it.isFinite() }?.coerceIn(range) ?: default
            return StatusBarPreferences(
                enabled = flag(ENABLED, false),
                colorMode = (values[COLOR_MODE] as? String)
                    ?.let { name -> StatusBarColorMode.entries.firstOrNull { it.name == name } } ?: d.colorMode,
                showSilent = flag(SHOW_SILENT, d.showSilent),
                showSignal = flag(SHOW_SIGNAL, d.showSignal),
                showNetworkLabel = flag(SHOW_NETWORK, d.showNetworkLabel),
                showWifi = flag(SHOW_WIFI, d.showWifi),
                showBattery = flag(SHOW_BATTERY, d.showBattery),
                hideInLandscape = flag(HIDE_LANDSCAPE, d.hideInLandscape),
                hideSystemIcons = flag(HIDE_SYSTEM, false),
                scale = number(SCALE, d.scale, SCALE_RANGE),
                sidePadding = number(SIDE_PADDING, d.sidePadding, SIDE_PADDING_RANGE),
                verticalOffset = number(VERTICAL_OFFSET, d.verticalOffset, VERTICAL_OFFSET_RANGE),
                clockWeight = (values[CLOCK_WEIGHT] as? Int)?.takeIf { it in CLOCK_WEIGHTS } ?: d.clockWeight,
                batteryTheme = (values[BATTERY_THEME] as? Int)?.takeIf { it in ICON_THEMES } ?: d.batteryTheme,
                wifiTheme = (values[WIFI_THEME] as? Int)?.takeIf { it in ICON_THEMES } ?: d.wifiTheme,
                signalTheme = (values[SIGNAL_THEME] as? Int)?.takeIf { it in ICON_THEMES } ?: d.signalTheme,
                clockTheme = (values[CLOCK_THEME] as? Int)?.takeIf { it in ICON_THEMES } ?: d.clockTheme,
                airplaneTheme = (values[AIRPLANE_THEME] as? Int)?.takeIf { it in ICON_THEMES } ?: d.airplaneTheme,
            )
        }
    }
}

/**
 * Process-wide holder shared by the settings screen and the overlay (both live in the launcher's
 * process). Changes made in settings reach the overlay through [state] immediately.
 */
object StatusBarStore {
    private const val FILE = "status_bar_prefs"
    internal const val BACKUP_KEY = "sb_icon_blacklist_backup"

    private val mutable = MutableStateFlow(StatusBarPreferences())
    val state: StateFlow<StatusBarPreferences> = mutable

    // A landscape pull-down is the only time the custom bar should be revealed when
    // hideInLandscape is enabled. ShadeService updates this transient process-wide signal.
    private val mutableLandscapeGesture = MutableStateFlow(false)
    val landscapeGesture: StateFlow<Boolean> = mutableLandscapeGesture

    fun setLandscapeGestureActive(active: Boolean) {
        mutableLandscapeGesture.value = active
    }
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        mutable.value = StatusBarPreferences.fromStored(p.all)
    }

    @Synchronized
    fun update(change: (StatusBarPreferences) -> StatusBarPreferences) {
        val next = change(mutable.value)
        mutable.value = next
        prefs?.edit()?.apply {
            next.storedValues().forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
        }?.apply()
    }

    internal fun sharedPreferences(): SharedPreferences? = prefs
}
