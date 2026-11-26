package com.hoisly.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.island.ui.Perms
import com.hoisly.launcher.R
import com.hoisly.launcher.statusbar.StatusBarColorMode
import com.hoisly.launcher.statusbar.StatusBarPreferences
import com.hoisly.launcher.statusbar.StatusBarState
import com.hoisly.launcher.statusbar.StatusBarStore
import com.hoisly.launcher.statusbar.CleanBarController
import com.hoisly.launcher.statusbar.StatusBarView
import com.hoisly.launcher.statusbar.SystemIconHider
import kotlin.math.roundToInt

private val PreviewState = StatusBarState(time = "1:55", silent = true, signalLevel = 3, networkLabel = "", cellularData = true, wifiConnected = true, wifiLevel = 3, batteryPercent = 69, charging = true)
private const val GRANT_COMMAND = "adb shell pm grant com.hoisly.launcher android.permission.WRITE_SECURE_SETTINGS"

/** Status bar settings: switch, live preview, colour mode, visible elements, size and spacing. */
@Composable
fun StatusBarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { StatusBarStore.init(context) }
    val prefs by StatusBarStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var shizukuRevision by remember { mutableIntStateOf(0) }
    var cleanBarMessage by remember { mutableStateOf<String?>(null) }
    var cleanBarBusy by remember { mutableStateOf(false) }
    var accessibility by remember { mutableStateOf(Perms.accessibility(context)) }
    var secureGranted by remember { mutableStateOf(SystemIconHider.canWrite(context)) }
    LifecycleResumeEffect(Unit) {
        accessibility = Perms.accessibility(context)
        secureGranted = SystemIconHider.canWrite(context)
        shizukuRevision++
        onPauseOrDispose { }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { shizukuRevision++ }
        val dead = Shizuku.OnBinderDeadListener { shizukuRevision++ }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> shizukuRevision++ }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
    val shizukuRunning = remember(shizukuRevision) { CleanBarController.shizukuRunning() }
    val shizukuAllowed = remember(shizukuRevision) { CleanBarController.hasPermission() }
    fun save(change: (StatusBarPreferences) -> StatusBarPreferences) = StatusBarStore.update(change)
    fun setGlobalStatusBarHidden(hidden: Boolean) {
        if (cleanBarBusy) return
        if (!shizukuRunning) {
            cleanBarMessage = "Jalankan Shizuku terlebih dahulu."
            return
        }
        if (!shizukuAllowed) {
            cleanBarMessage = "Berikan izin Shizuku untuk Hoisly."
            return
        }
        cleanBarBusy = true
        cleanBarMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { CleanBarController.apply(context, hidden) }
            cleanBarBusy = false
            if (result.isSuccess) {
                save { it.copy(hideSystemStatusBar = hidden) }
                cleanBarMessage = if (hidden) "Status bar sistem disembunyikan secara global." else "Status bar sistem dipulihkan."
            } else {
                cleanBarMessage = "Gagal: ${result.exceptionOrNull()?.message ?: "perintah Shizuku gagal"}"
            }
        }
    }

    // Sliders keep the dragged value locally and save once released.
    var scale by remember(prefs.scale) { mutableFloatStateOf(prefs.scale) }
    var padding by remember(prefs.sidePadding) { mutableFloatStateOf(prefs.sidePadding) }
    var offset by remember(prefs.verticalOffset) { mutableFloatStateOf(prefs.verticalOffset) }

    val status = when {
        !prefs.enabled -> R.string.statusbar_off
        !accessibility -> R.string.statusbar_need_service
        else -> R.string.statusbar_ready
    }

    SettingsPage(stringResource(R.string.statusbar_title), onBack) {
        item { StatusBarPreview(prefs.copy(scale = scale, sidePadding = padding, verticalOffset = offset)) }
        item { SectionGap() }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.statusbar_enable), prefs.enabled, { value -> save { it.copy(enabled = value) } })
                NavRow(
                    Icons.Rounded.Accessibility,
                    stringResource(R.string.statusbar_service),
                    onClick = { Perms.openAccessibility(context) },
                    value = stringResource(if (accessibility) R.string.statusbar_allowed else R.string.statusbar_allow),
                    tileColor = SettingsStyle.Tile.Purple,
                )
            }
            SectionFooter(stringResource(status))
        }
        item { SectionHeader(stringResource(R.string.statusbar_section_color)) }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp)) {
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.statusbar_color_auto),
                            stringResource(R.string.statusbar_color_black),
                            stringResource(R.string.statusbar_color_white),
                        ),
                        selectedIndex = prefs.colorMode.ordinal,
                        onSelect = { index -> save { it.copy(colorMode = StatusBarColorMode.entries[index]) } },
                    )
                }
            }
            SectionFooter(stringResource(R.string.statusbar_color_footer))
        }
        item { SectionHeader(stringResource(R.string.statusbar_section_elements)) }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.statusbar_show_silent), prefs.showSilent, { v -> save { it.copy(showSilent = v) } })
                SwitchRow(stringResource(R.string.statusbar_show_signal), prefs.showSignal, { v -> save { it.copy(showSignal = v) } })
                SwitchRow(stringResource(R.string.statusbar_show_network), prefs.showNetworkLabel, { v -> save { it.copy(showNetworkLabel = v) } })
                SwitchRow(stringResource(R.string.statusbar_show_wifi), prefs.showWifi, { v -> save { it.copy(showWifi = v) } })
                SwitchRow(stringResource(R.string.statusbar_show_battery), prefs.showBattery, { v -> save { it.copy(showBattery = v) } })
            }
        }
        item { SectionHeader(stringResource(R.string.statusbar_section_look)) }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp)) {
                    RowTitle(stringResource(R.string.statusbar_clock_weight))
                    Box(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.statusbar_weight_regular),
                            stringResource(R.string.statusbar_weight_medium),
                            stringResource(R.string.statusbar_weight_semibold),
                            stringResource(R.string.statusbar_weight_bold),
                        ),
                        selectedIndex = StatusBarPreferences.CLOCK_WEIGHTS.indexOf(prefs.clockWeight).coerceAtLeast(0),
                        onSelect = { index -> save { it.copy(clockWeight = StatusBarPreferences.CLOCK_WEIGHTS[index]) } },
                    )
                }
                SliderRow(
                    title = stringResource(R.string.statusbar_size),
                    value = scale,
                    valueText = "${(scale * 100).roundToInt()}%",
                    onValueChange = { scale = it },
                    onValueChangeFinished = { save { it.copy(scale = (scale * 20).roundToInt() / 20f) } },
                    valueRange = StatusBarPreferences.SCALE_RANGE,
                )
                SliderRow(
                    title = stringResource(R.string.statusbar_side_padding),
                    value = padding,
                    valueText = "${padding.roundToInt()} dp",
                    onValueChange = { padding = it },
                    onValueChangeFinished = { save { it.copy(sidePadding = padding.roundToInt().toFloat()) } },
                    valueRange = StatusBarPreferences.SIDE_PADDING_RANGE,
                )
                SliderRow(
                    title = stringResource(R.string.statusbar_vertical_offset),
                    value = offset,
                    valueText = "${offset.roundToInt()} dp",
                    onValueChange = { offset = it },
                    onValueChangeFinished = { save { it.copy(verticalOffset = offset.roundToInt().toFloat()) } },
                    valueRange = StatusBarPreferences.VERTICAL_OFFSET_RANGE,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.statusbar_icon_styles)) }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp)) {
                    RowTitle(stringResource(R.string.statusbar_battery_style))
                    Box(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf("iOS 16", "iOS 17", "iOS 18", "iOS 26", "Compact"),
                        selectedIndex = prefs.batteryTheme,
                        onSelect = { index -> save { it.copy(batteryTheme = index) } },
                    )
                    Box(Modifier.height(14.dp))
                    RowTitle(stringResource(R.string.statusbar_wifi_style))
                    Box(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf("iOS 16", "iOS 17", "iOS 18", "iOS 26", "Compact"),
                        selectedIndex = prefs.wifiTheme,
                        onSelect = { index -> save { it.copy(wifiTheme = index) } },
                    )
                    Box(Modifier.height(14.dp))
                    RowTitle(stringResource(R.string.statusbar_signal_style))
                    Box(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf("iOS 16", "iOS 17", "iOS 18", "iOS 26", "Compact"),
                        selectedIndex = prefs.signalTheme,
                        onSelect = { index -> save { it.copy(signalTheme = index) } },
                    )
                    Box(Modifier.height(14.dp))
                    RowTitle(stringResource(R.string.statusbar_clock_style))
                    Box(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf("iOS 16", "iOS 17", "iOS 18", "iOS 26", "Compact"),
                        selectedIndex = prefs.clockTheme,
                        onSelect = { index -> save { it.copy(clockTheme = index) } },
                    )
                    Box(Modifier.height(14.dp))
                    RowTitle(stringResource(R.string.statusbar_airplane_style))
                    Box(Modifier.height(8.dp))
                    SegmentedControl(
                        options = listOf("iOS 16", "iOS 17", "iOS 18", "iOS 26", "Compact"),
                        selectedIndex = prefs.airplaneTheme,
                        onSelect = { index -> save { it.copy(airplaneTheme = index) } },
                    )
                }
            }
            SectionFooter(stringResource(R.string.statusbar_mix_styles_footer))
        }
        item { SectionHeader(stringResource(R.string.statusbar_section_system)) }
        item {
            SettingsCard {
                SwitchRow(
                    "Hide System Status Bar",
                    prefs.hideSystemStatusBar,
                    ::setGlobalStatusBarHidden,
                )
                Text(
                    when {
                        !shizukuRunning -> "Shizuku: tidak aktif. Jalankan Shizuku terlebih dahulu."
                        !shizukuAllowed -> "Shizuku aktif, tetapi izin Hoisly belum diberikan."
                        else -> "Shizuku terhubung dan izin telah diberikan."
                    },
                    modifier = Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 6.dp),
                )
                if (!shizukuRunning || !shizukuAllowed) {
                    TextButton(onClick = {
                        if (shizukuRunning) CleanBarController.requestPermission()
                        else runCatching {
                            val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (launch != null) context.startActivity(launch)
                            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")))
                        }
                        shizukuRevision++
                    }) { Text(if (shizukuRunning) "Berikan izin Shizuku" else "Buka Shizuku") }
                }
                cleanBarMessage?.let { Text(it, modifier = Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 4.dp)) }
                SwitchRow(
                    stringResource(R.string.statusbar_hide_system),
                    prefs.hideSystemIcons && secureGranted,
                    { v -> if (secureGranted) save { it.copy(hideSystemIcons = v) } },
                )
            }
            SectionFooter("Memerlukan Shizuku. Pengaturan ini menggunakan kebijakan immersive Android global, bukan hanya menyembunyikan status bar di jendela Hoisly.")
            SectionFooter(
                if (secureGranted) stringResource(R.string.statusbar_hide_system_footer)
                else stringResource(R.string.statusbar_hide_system_grant, GRANT_COMMAND),
            )
        }
        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.RestartAlt,
                    stringResource(R.string.statusbar_reset),
                    onClick = { save { StatusBarPreferences(enabled = it.enabled, hideSystemIcons = it.hideSystemIcons, hideSystemStatusBar = it.hideSystemStatusBar) } },
                    tileColor = SettingsStyle.Tile.Red,
                    showChevron = false,
                )
            }
        }
    }
}

/** Light and dark sample of the bar with the current options applied, so changes are visible at once. */
@Composable
private fun StatusBarPreview(prefs: StatusBarPreferences) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        PreviewStrip(prefs, background = Color(0xFFE3DAD0), black = true)
        PreviewStrip(prefs, background = Color(0xFF0B0B0F), black = false)
    }
}

@Composable
private fun PreviewStrip(prefs: StatusBarPreferences, background: Color, black: Boolean) {
    Box(Modifier.fillMaxWidth().height(44.dp).background(background)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            factory = { StatusBarView(it).also { view -> view.state = PreviewState } },
            update = { view ->
                view.applyPrefs(prefs)
                view.setBlackIcons(black, animate = false)
            },
        )
    }
}
