package com.zfl.launcher.panels.control

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.R
import com.zfl.launcher.island.IslandAccess
import kotlinx.coroutines.delay

/** What the Control Center asks of the host: closing itself and the two other panels. */
internal class ControlCenterActions(
    val close: () -> Unit,
    val openNotifications: () -> Unit,
    val showLockScreen: () -> Unit,
    val notificationCenterEnabled: Boolean,
)

private val Gap = 12.dp
private val SideMargin = 24.dp
private val MaxWidth = 420.dp
private val TopButton = 40.dp
/** Status bar, the buttons above the grid and the navigation bar: what the grid may not use of the screen's height. */
private val ChromeHeight = 170.dp

/**
 * The Control Center, laid out like iOS 26: Bluetooth and Wi-Fi pills beside four round buttons,
 * the Now Playing pill, a row of round buttons, Focus and AirDrop pills beside the volume and
 * brightness pills, then two more rows of round buttons.
 *
 * Tiles reflect [SystemControls.state]. The ones Android does not let a launcher switch open the
 * system's own panel, and the ones that need a grant send the user to the screen where it is given.
 * "+" opens the Notification Center (or Settings when it is off); the power button shows the Lock Screen.
 */
@Composable
internal fun ControlCenterPanel(controls: SystemControls, actions: ControlCenterActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by controls.state.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf<NowPlaying?>(null) }
    var mediaAccess by remember { mutableStateOf(IslandAccess.read(context).notifications) }
    LaunchedEffect(Unit) {
        while (true) {
            mediaAccess = IslandAccess.read(context).notifications
            val fresh = if (mediaAccess) readNowPlaying(context) else null
            // Keep the old object while the song is the same, so the pill does not redraw every second.
            if (!(fresh?.sameAs(playing) ?: (playing == null))) playing = fresh
            delay(MEDIA_POLL_MS)
        }
    }

    // The grid is eight units tall; on a short screen the unit shrinks so everything still fits.
    val configuration = LocalConfiguration.current
    val unitFromWidth = (minOf(configuration.screenWidthDp.dp, MaxWidth) - SideMargin * 2 - Gap * 3) / 4
    val unitFromHeight = (configuration.screenHeightDp.dp - ChromeHeight - Gap * 5) / 8
    val unit = minOf(unitFromWidth, unitFromHeight)
    val width = unit * 4 + Gap * 3
    fun span(cells: Int): Dp = unit * cells + Gap * (cells - 1)
    val circle = Modifier.size(unit)
    var askedForWrite by remember { mutableStateOf(false) }
    LaunchedEffect(state.canWriteSettings) { askedForWrite = false }

    fun open(intent: Intent) {
        if (!context.startQuietly(intent)) Toast.makeText(context, R.string.control_unavailable, Toast.LENGTH_SHORT).show()
    }
    fun needGrant(grant: ControlGrant) {
        val message = if (grant == ControlGrant.WriteSettings) R.string.control_need_write_settings else R.string.control_need_focus_access
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        open(controls.grantIntent(grant))
    }
    fun launchAndClose(intent: Intent) {
        open(intent)
        actions.close()
    }
    /** Opens the first of [packages] that is installed; says so when none is. */
    fun launchApp(vararg packages: String) {
        val launched = packages.any { pkg -> context.packageManager.getLaunchIntentForPackage(pkg)?.let(context::startQuietly) == true }
        if (launched) actions.close() else Toast.makeText(context, R.string.control_unavailable, Toast.LENGTH_SHORT).show()
    }
    val on = stringResource(R.string.control_on)
    val off = stringResource(R.string.control_off)

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(Gap)) {
            // Above the grid: "+" on the left, the power button (Lock Screen) on the right.
            Row(Modifier.width(width), horizontalArrangement = Arrangement.SpaceBetween) {
                GlassCircle(
                    Icons.Rounded.Add, stringResource(R.string.control_add), false, ControlColors.Blue,
                    { if (actions.notificationCenterEnabled) actions.openNotifications() else launchAndClose(Intent(Settings.ACTION_SETTINGS)) },
                    Modifier.size(TopButton), showsState = false, glyphSize = 22.dp,
                )
                GlassCircle(
                    Icons.Rounded.PowerSettingsNew, stringResource(R.string.lock_screen_title), false, ControlColors.Blue,
                    actions.showLockScreen, Modifier.size(TopButton), showsState = false, glyphSize = 22.dp,
                )
            }
            Spacer(Modifier.height(4.dp))

            // Connectivity: two pills, four round buttons.
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                    GlassPill(
                        Icons.Rounded.Bluetooth, stringResource(R.string.control_bluetooth), if (state.bluetooth) on else off,
                        state.bluetooth, ControlColors.Blue, { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        Modifier.size(span(2), unit),
                    )
                    GlassPill(
                        Icons.Rounded.Wifi, stringResource(R.string.control_wifi),
                        if (!state.wifi) off else state.wifiName ?: on,
                        state.wifi, ControlColors.Blue, { open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
                        Modifier.size(span(2), unit),
                        onLongClick = { open(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        GlassCircle(Icons.Rounded.AirplanemodeActive, stringResource(R.string.control_airplane), state.airplane, ControlColors.Orange,
                            { open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }, circle)
                        GlassCircle(Icons.Rounded.SignalCellularAlt, stringResource(R.string.control_mobile_data), state.mobileData, ControlColors.Green,
                            { open(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }, circle,
                            onLongClick = { open(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        // Android lets no launcher switch these: they open the system's own screen.
                        GlassCircle(Icons.Rounded.WifiTethering, stringResource(R.string.control_hotspot), false, ControlColors.Green,
                            { if (!context.startQuietly(Intent("android.settings.TETHER_SETTINGS"))) open(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                            circle, showsState = false)
                        GlassCircle(Icons.Rounded.BatterySaver, stringResource(R.string.control_low_power), state.lowPower, ControlColors.Orange,
                            { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }, circle)
                    }
                }
            }

            // Now Playing.
            NowPlayingPill(
                playing, mediaAccess,
                onAllowAccess = { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                onOutput = { open(Intent(Settings.Panel.ACTION_VOLUME)) },
                modifier = Modifier.size(width, unit),
            )

            // Display, calculator, rotation lock, silent.
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                GlassCircle(Icons.Rounded.DarkMode, stringResource(R.string.control_dark_mode), state.darkMode, ControlColors.Purple,
                    { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }, circle)
                GlassCircle(Icons.Rounded.Calculate, stringResource(R.string.control_calculator), false, ControlColors.Blue,
                    { launchAndClose(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)) }, circle, showsState = false)
                GlassCircle(Icons.Rounded.ScreenLockRotation, stringResource(R.string.control_rotation_lock), state.rotationLocked, ControlColors.Red,
                    { controls.toggleRotationLock()?.let(::needGrant) }, circle)
                GlassCircle(Icons.Rounded.NotificationsOff, stringResource(R.string.control_silent), state.silent, ControlColors.Red,
                    controls::toggleSilent, circle)
            }

            // Focus and AirDrop pills beside the volume and brightness pills.
            Row(horizontalArrangement = Arrangement.spacedBy(Gap), modifier = Modifier.height(span(2))) {
                Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                    GlassPill(
                        Icons.Rounded.Bedtime, stringResource(R.string.control_focus_label), null,
                        state.focus, ControlColors.Purple, { controls.toggleFocus()?.let(::needGrant) },
                        Modifier.size(span(2), unit),
                    )
                    GlassPill(
                        Icons.Rounded.SettingsInputAntenna, stringResource(R.string.control_airdrop), stringResource(R.string.control_airdrop_contacts),
                        true, ControlColors.Blue,
                        { openQuickShare(context) },
                        Modifier.size(span(2), unit), showsState = false,
                    )
                }
                VerticalControlSlider(state.volume, Icons.Rounded.VolumeUp, stringResource(R.string.control_volume),
                    controls::setVolume, Modifier.size(unit, span(2)),
                    iconFor = { level ->
                        when {
                            level <= 0f -> Icons.Rounded.VolumeOff
                            level < 0.34f -> Icons.Rounded.VolumeMute
                            level < 0.67f -> Icons.Rounded.VolumeDown
                            else -> Icons.Rounded.VolumeUp
                        }
                    })
                VerticalControlSlider(state.brightness, Icons.Rounded.WbSunny, stringResource(R.string.control_brightness),
                    { value ->
                        // One request per drag, not one per touch event.
                        if (state.canWriteSettings) controls.setBrightness(value)
                        else if (!askedForWrite) { askedForWrite = true; needGrant(ControlGrant.WriteSettings) }
                    }, Modifier.size(unit, span(2)))
            }

            // Timer, screen recording, alarm, translate.
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                GlassCircle(Icons.Rounded.Timer, stringResource(R.string.control_timer), false, ControlColors.Orange,
                    { launchAndClose(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }, circle, showsState = false)
                GlassCircle(Icons.Rounded.RadioButtonChecked, stringResource(R.string.control_screen_record), false, ControlColors.Red,
                    { launchApp("com.miui.screenrecorder", "com.oplus.screenrecorder", "com.coloros.screenrecorder", "com.hihonor.screenrecorder", "com.huawei.screenrecorder") },
                    circle, showsState = false)
                GlassCircle(Icons.Rounded.Alarm, stringResource(R.string.control_alarm), false, ControlColors.Orange,
                    { launchAndClose(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }, circle, showsState = false)
                GlassCircle(Icons.Rounded.Translate, stringResource(R.string.control_translate), false, ControlColors.Blue,
                    { launchApp("com.google.android.apps.translate") }, circle, showsState = false)
            }

            // Music recognition, accessibility, sound recording, flashlight.
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                GlassCircle(Icons.Rounded.MusicNote, stringResource(R.string.control_shazam), false, ControlColors.Blue,
                    { launchApp("com.shazam.android", "com.google.android.googlequicksearchbox") }, circle, showsState = false)
                GlassCircle(Icons.Rounded.Accessibility, stringResource(R.string.control_accessibility), false, ControlColors.Blue,
                    { launchAndClose(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, circle, showsState = false)
                GlassCircle(Icons.Rounded.GraphicEq, stringResource(R.string.control_sound_recognition), false, ControlColors.Red,
                    { launchAndClose(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)) }, circle, showsState = false)
                GlassCircle(Icons.Rounded.FlashlightOn, stringResource(R.string.control_flashlight), state.torch, ControlColors.Orange,
                    { if (state.torchAvailable) controls.setTorch(!state.torch) else Toast.makeText(context, R.string.control_flashlight_unavailable, Toast.LENGTH_SHORT).show() },
                    circle)
            }
        }
    }
}

/** The nearby-sharing screen ("AirDrop" here): Quick Share when it is there, Wireless settings otherwise. */
private fun openQuickShare(context: Context) {
    val candidates = listOf(
        Intent().setClassName("com.google.android.gms", "com.google.android.gms.nearby.sharing.ReceiveSurfaceActivity"),
        Intent().setClassName("com.samsung.android.app.sharelive", "com.samsung.android.app.sharelive.presentation.main.ShareLiveMainActivity"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )
    candidates.firstOrNull { context.startQuietly(it) }
}

/** Starts [intent] from the launcher; false when no app handles it. */
internal fun Context.startQuietly(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

private const val MEDIA_POLL_MS = 1000L
