package com.hoisly.launcher.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.settings.OrientationLock
import com.hoisly.launcher.settings.SettingsState
import com.hoisly.launcher.ui.theme.Tokens
import kotlin.math.abs
import kotlin.math.roundToInt

/** Text sizes offered for the settings app, see [SettingsState.settingsTextScale]. */
private val TextSizeOptions = listOf(1f, 1.2f, 1.4f)

/**
 * UI Lab: ten tools for people who like to tinker with the interface. Every control here is
 * saved in the normal settings (and therefore in backups) and applies immediately.
 */
@Composable
fun UiLabScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val indo = language == "in" || language == "id"
    fun t(id: String, en: String) = if (indo) id else en

    var wallpaperDim by remember(settings.wallpaperDim) { mutableFloatStateOf(settings.wallpaperDim) }
    var settingsDim by remember(settings.settingsDim) { mutableFloatStateOf(settings.settingsDim) }
    var confirmReset by remember { mutableStateOf(false) }
    val percent = { v: Float -> "${(v * 100).roundToInt()}%" }

    SettingsPage(title = "UI Lab", onBack = onBack) {
        // ── 1-2: home screen ─────────────────────────────────
        item { SectionHeader(t("Layar Home", "Home screen")) }
        item {
            SettingsCard {
                SliderRow(
                    title = t("Gelapkan wallpaper", "Dim wallpaper"),
                    value = wallpaperDim,
                    valueText = percent(wallpaperDim),
                    onValueChange = { wallpaperDim = it },
                    onValueChangeFinished = { vm.settings.update { it.copy(wallpaperDim = wallpaperDim) } },
                    onValueChangeCancelled = { wallpaperDim = settings.wallpaperDim },
                    valueRange = 0f..SettingsState.MAX_WALLPAPER_DIM,
                )
                SwitchRow(
                    title = t("Meter FPS", "FPS meter"),
                    description = t(
                        "Tampilkan FPS dan frame terlama di pojok Home. Berguna untuk menilai kehalusan animasi.",
                        "Show FPS and the slowest frame in the corner of Home. Handy for judging how smooth animations are.",
                    ),
                    checked = settings.showFps,
                    onCheckedChange = { value -> vm.settings.update { it.copy(showFps = value) } },
                )
            }
            SectionFooter(
                t(
                    "Gelapkan wallpaper hanya memengaruhi latar Home; kaca dan ikon tetap seperti pengaturanmu.",
                    "Dimming only affects the Home background; glass and icons keep your settings.",
                ),
            )
        }

        // ── 3-5: screen and device ───────────────────────────
        item { SectionHeader(t("Layar & perangkat", "Screen & device")) }
        item {
            SettingsCard {
                SwitchRow(
                    title = t("Layar selalu menyala di Home", "Keep screen on at Home"),
                    description = t("Layar tidak mati selama Home di depan. Cocok saat menguji tampilan.", "The screen doesn't time out while Home is in front. Good for testing looks."),
                    checked = settings.keepScreenOn,
                    onCheckedChange = { value -> vm.settings.update { it.copy(keepScreenOn = value) } },
                )
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 14.dp)) {
                    RowTitle(t("Orientasi Home", "Home orientation"))
                    Spacer(Modifier.height(12.dp))
                    SegmentedControl(
                        options = listOf(t("Potret", "Portrait"), t("Otomatis", "Auto"), t("Lanskap", "Landscape")),
                        selectedIndex = OrientationLock.entries.indexOf(settings.orientationLock),
                        onSelect = { index -> vm.settings.update { it.copy(orientationLock = OrientationLock.entries[index]) } },
                    )
                }
                SwitchRow(
                    title = t("Sembunyikan bilah navigasi", "Hide navigation bar"),
                    description = t("Geser dari tepi bawah untuk memunculkannya sebentar.", "Swipe from the bottom edge to show it briefly."),
                    checked = settings.hideNavigationBar,
                    onCheckedChange = { value -> vm.settings.update { it.copy(hideNavigationBar = value) } },
                )
            }
            SectionFooter(
                t(
                    "Orientasi Otomatis mengikuti kunci rotasi sistem. Susunan Home dirancang untuk potret, jadi mode lanskap bersifat eksperimen.",
                    "Auto follows the system rotation lock. Home is designed for portrait, so landscape is experimental.",
                ),
            )
        }

        // ── 6-7: the settings app itself ─────────────────────
        item { SectionHeader(t("Tampilan Setelan", "Settings look")) }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 14.dp)) {
                    RowTitle(t("Ukuran teks Setelan", "Settings text size"))
                    Spacer(Modifier.height(12.dp))
                    SegmentedControl(
                        options = listOf(t("Normal", "Normal"), t("Besar", "Large"), t("Ekstra", "Extra")),
                        selectedIndex = TextSizeOptions.indices.minByOrNull { abs(TextSizeOptions[it] - settings.settingsTextScale) } ?: 0,
                        onSelect = { index -> vm.settings.update { it.copy(settingsTextScale = TextSizeOptions[index]) } },
                    )
                }
                SliderRow(
                    title = t("Kegelapan latar Setelan", "Settings background darkness"),
                    value = settingsDim,
                    valueText = percent(settingsDim),
                    onValueChange = { settingsDim = it },
                    onValueChangeFinished = { vm.settings.update { it.copy(settingsDim = settingsDim) } },
                    onValueChangeCancelled = { settingsDim = settings.settingsDim },
                    valueRange = SettingsState.MIN_SETTINGS_DIM..SettingsState.MAX_SETTINGS_DIM,
                )
            }
            SectionFooter(
                t(
                    "Makin terang latarnya, makin jelas efek liquid glass di kartu; makin gelap, makin mudah dibaca.",
                    "The lighter the background, the more of the liquid glass shows in the cards; the darker, the easier to read.",
                ),
            )
        }

        // ── 8-10: presets ────────────────────────────────────
        item { SectionHeader(t("Preset tampilan", "Look presets")) }
        item {
            SettingsCard {
                NavRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = t("Salin kode tampilan", "Copy look code"),
                    onClick = {
                        val code = vm.settings.exportLookCode()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Hoisly look", code))
                        Toast.makeText(context, t("Kode tampilan disalin", "Look code copied"), Toast.LENGTH_SHORT).show()
                    },
                    showChevron = false,
                )
                NavRow(
                    icon = Icons.Rounded.ContentPaste,
                    title = t("Tempel kode tampilan", "Paste look code"),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        val applied = vm.settings.importLookCode(text)
                        val message = if (applied > 0) {
                            t("Tampilan diterapkan ($applied pengaturan)", "Look applied ($applied settings)")
                        } else {
                            t("Clipboard tidak berisi kode tampilan Hoisly", "Clipboard has no Hoisly look code")
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    showChevron = false,
                )
                NavRow(
                    icon = Icons.Rounded.RestartAlt,
                    title = t("Reset semua tampilan", "Reset the whole look"),
                    onClick = { confirmReset = true },
                    titleColor = Tokens.Destructive,
                    showChevron = false,
                )
            }
            SectionFooter(
                t(
                    "Kode memuat kaca, ikon, animasi, dock, margin grid, dan slider UI Lab. Susunan Home, aplikasi tersembunyi, nama, dan ikon kustom tidak ikut.",
                    "A code holds glass, icons, animation, dock, grid margins and the UI Lab sliders. Home layout, hidden apps, names and custom icons are not included.",
                ),
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Color(0xFF1C1C1E).copy(alpha = 0.94f),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.78f),
            title = { Text(t("Reset semua tampilan?", "Reset the whole look?"), fontSize = 22.sp) },
            text = {
                Text(
                    t(
                        "Kaca, ikon, animasi, dock, dan slider UI Lab kembali ke bawaan. Susunan Home dan aplikasi tidak berubah.",
                        "Glass, icons, animation, dock and the UI Lab sliders go back to defaults. Your Home layout and apps stay as they are.",
                    ),
                    fontSize = 16.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.settings.resetLook()
                    Toast.makeText(context, t("Tampilan direset", "Look reset"), Toast.LENGTH_SHORT).show()
                }) { Text(t("Reset", "Reset"), color = Tokens.Destructive, fontSize = 17.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(t("Batal", "Cancel"), fontSize = 17.sp) }
            },
        )
    }
}
