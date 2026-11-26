package com.hoisly.launcher.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.R
import com.hoisly.launcher.island.IslandAccess
import com.hoisly.launcher.settings.PanelPreferences

/**
 * Notification Center. The Notification Center is the only feature that
 * reads notification text, so it is off until the user turns it on and grants access.
 */
@Composable
fun PanelsSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, onBack: () -> Unit): Unit {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val grants by IslandAccess.state.collectAsStateWithLifecycle()
    val panels = settings.panels
    fun save(change: (PanelPreferences) -> PanelPreferences) = vm.settings.update { it.copy(panels = change(it.panels)) }
    SettingsPage(stringResource(R.string.panels_title), onBack) {
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.control_center_title), panels.controlCenter, { value -> save { it.copy(controlCenter = value) } })
            }
            SectionFooter(stringResource(R.string.panels_control_footer))
        }
        item { SectionGap() }
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.notification_center_title), panels.notificationCenter, { value ->
                    save { it.copy(notificationCenter = value) }
                    if (value) actions.reconnectIsland()
                })
                SwitchRow(stringResource(R.string.panels_hide_content), panels.hideNotificationContent, { value -> save { it.copy(hideNotificationContent = value) } })
                NavRow(Icons.Rounded.Notifications, stringResource(R.string.panels_notification_access), actions::requestIslandMusicAccess,
                    value = stringResource(if (grants.notifications) R.string.island_allowed else R.string.island_allow), tileColor = SettingsStyle.Tile.Red)
            }
            SectionFooter(stringResource(R.string.panels_notifications_footer))
        }
    }
}
