package com.hoisly.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.R
import com.hoisly.launcher.settings.PanelPreferences
import com.hoisly.launcher.ui.lockscreen.hashPattern
import java.security.SecureRandom
import kotlin.math.roundToInt

/** Lock Screen: what the launcher's decorative lock screen shows and how the clock looks. */
@Composable
fun LockScreenSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val panels = settings.panels
    fun save(change: (PanelPreferences) -> PanelPreferences) = vm.settings.update { it.copy(panels = change(it.panels)) }
    var scale by remember(panels.lockClockScale) { mutableFloatStateOf(panels.lockClockScale) }
    var blur by remember(panels.lockWallpaperBlur) { mutableFloatStateOf(panels.lockWallpaperBlur) }
    var settingPattern by remember { mutableStateOf(false) }

    if (settingPattern) {
        PatternSetupDialog(
            onDone = { nodes ->
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                save { p -> p.copy(lockPattern = hashPattern(nodes, salt)) }
                settingPattern = false
            },
            onCancel = { settingPattern = false },
        )
    }

    SettingsPage(stringResource(R.string.lock_title), onBack) {
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.lock_enable), settings.showLockScreenAfterSleep, { value ->
                    vm.settings.update { it.copy(showLockScreenAfterSleep = value) }
                })
            }
        }
        item { SectionGap() }
        item {
            SettingsCard {
                NavRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(if (panels.lockPattern.isEmpty()) R.string.lock_pattern_set else R.string.lock_pattern_change),
                    onClick = { settingPattern = true },
                )
                if (panels.lockPattern.isNotEmpty()) {
                    NavRow(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.lock_pattern_remove),
                        onClick = { save { p -> p.copy(lockPattern = "") } },
                        titleColor = Color(0xFFFF453A),
                        showChevron = false,
                    )
                }
            }
        }
        item { SectionGap() }
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp)) {
                    RowTitle(stringResource(R.string.lock_clock_weight))
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.lock_weight_thin), stringResource(R.string.lock_weight_light),
                            stringResource(R.string.lock_weight_regular), stringResource(R.string.lock_weight_bold),
                        ),
                        selectedIndex = panels.lockClockWeight,
                        onSelect = { index -> save { p -> p.copy(lockClockWeight = index) } },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                SliderRow(
                    title = stringResource(R.string.lock_clock_size),
                    value = scale,
                    valueText = "${(scale * 100).roundToInt()}%",
                    onValueChange = { scale = it },
                    onValueChangeFinished = { save { p -> p.copy(lockClockScale = scale) } },
                    valueRange = PanelPreferences.MIN_SCALE..PanelPreferences.MAX_SCALE,
                )
                SwitchRow(stringResource(R.string.lock_clock_centered), panels.lockClockCentered, { value -> save { p -> p.copy(lockClockCentered = value) } })
            }
        }
        item { SectionGap() }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.lock_show_battery), panels.lockShowBattery, { value -> save { p -> p.copy(lockShowBattery = value) } })
                SwitchRow(stringResource(R.string.lock_show_notifications), panels.lockShowNotifications, { value -> save { p -> p.copy(lockShowNotifications = value) } })
                SwitchRow(stringResource(R.string.lock_use_wallpaper), panels.lockWallpaper, { value -> save { p -> p.copy(lockWallpaper = value) } })
                SliderRow(
                    title = stringResource(R.string.lock_wallpaper_blur),
                    value = blur,
                    valueText = "${(blur * 100).roundToInt()}%",
                    onValueChange = { blur = it },
                    onValueChangeFinished = { save { p -> p.copy(lockWallpaperBlur = blur) } },
                    valueRange = 0f..1f,
                    enabled = panels.lockWallpaper,
                )
            }
            SectionFooter(stringResource(R.string.lock_footer))
        }
    }
}
