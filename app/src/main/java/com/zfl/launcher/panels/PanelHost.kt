package com.zfl.launcher.panels

import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.LauncherViewModel
import com.zfl.launcher.island.IslandAccess
import com.zfl.launcher.panels.control.ControlCenterActions
import com.zfl.launcher.panels.control.ControlCenterPanel
import com.zfl.launcher.panels.control.SystemControls
import com.zfl.launcher.panels.lock.LockScreenOverlay
import com.zfl.launcher.panels.notifications.CenterNotification
import com.zfl.launcher.panels.notifications.NotificationCenterFeed
import com.zfl.launcher.panels.notifications.NotificationCenterPanel
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.panels.control.startQuietly
import kotlinx.coroutines.delay

/** Height of the touch strip under the status bar that starts a panel drag. */
private val EdgeStripHeight = 32.dp
private const val STATE_POLL_MS = 500L

/**
 * Everything the top edge opens: the strip that starts a drag (left and middle: Notification
 * Center, right third: Control Center, like iOS), the scrim, both panels and the Lock Screen.
 * Panels follow the finger, settle with the sheet spring and are interruptible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelHost(controller: PanelController, vm: LauncherViewModel) {
    val context = LocalContext.current
    val prefs = LocalSettingsState.current.panels
    val controls = remember { SystemControls(context) }
    val state by controls.state.collectAsStateWithLifecycle()
    val notifications by NotificationCenterFeed.items.collectAsStateWithLifecycle()
    val wallpaper by vm.homeWallpaper.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val menu by vm.menu.collectAsStateWithLifecycle()
    val folder by vm.openFolder.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val gallery by vm.widgetGallery.collectAsStateWithLifecycle()

    val visible = controller.isOpen
    // Live state is polled only while something is on screen, and the torch callback lives as long.
    DisposableEffect(visible) {
        if (visible) controls.start()
        onDispose { if (visible) controls.stop() }
    }
    LaunchedEffect(visible) {
        while (visible) {
            controls.refresh()
            delay(STATE_POLL_MS)
        }
    }
    val hasNotificationAccess = remember(visible) { IslandAccess.read(context).notifications }

    val stripEnabled = !controller.isOpen && !editMode && menu == null && folder == null && search == null && !gallery
    Box(Modifier.fillMaxSize().onSizeChanged {
        controller.heightPx = it.height.toFloat()
        controller.widthPx = it.width.toFloat()
    }) {
        if (stripEnabled && (prefs.controlCenter || prefs.notificationCenter)) {
            val pending = remember { PendingPanel() }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .fillMaxWidth()
                    .height(EdgeStripHeight)
                    .verticalPanelDrag(
                        accepts = { down ->
                            val right = down.x > controller.widthPx * 2f / 3f
                            pending.panel = when {
                                right && prefs.controlCenter -> Panel.Control
                                !right && prefs.notificationCenter -> Panel.Notifications
                                prefs.controlCenter -> Panel.Control
                                else -> Panel.Notifications
                            }
                            true
                        },
                        onStart = { pending.panel?.let(controller::beginDrag) },
                        onDrag = controller::drag,
                        onEnd = controller::release,
                    ),
            )
        }

        val panel = controller.panel
        if (panel != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = controller.fraction.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = if (panel == Panel.Control) 0.30f else 0.45f))
                    .pointerInput(Unit) { detectTapGestures { controller.close() } },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = controller.fraction
                        if (panel == Panel.Control) {
                            val s = 0.92f + 0.08f * f.coerceIn(0f, 1.05f)
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0.9f, 0f)
                            alpha = f.coerceIn(0f, 1f)
                        } else {
                            translationY = -(1f - f) * size.height
                        }
                    }
                    .then(if (panel == Panel.Notifications) Modifier.background(Brush.verticalGradient(listOf(Color(0xF2101012), Color(0xE6101012)))) else Modifier)
                    .verticalPanelDrag(
                        accepts = { controller.fraction > 0.5f },
                        onStart = {},
                        onDrag = controller::drag,
                        onEnd = controller::release,
                    )
                    .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
            ) {
                when (panel) {
                    Panel.Control -> ControlCenterPanel(
                        controls = controls,
                        actions = ControlCenterActions(
                            close = controller::close,
                            openNotifications = { controller.close(); controller.open(Panel.Notifications) },
                            showLockScreen = controller::showLockScreen,
                            notificationCenterEnabled = prefs.notificationCenter,
                        ),
                    )
                    Panel.Notifications -> NotificationCenterPanel(
                        notifications = notifications,
                        hideContent = prefs.hideNotificationContent,
                        hasAccess = hasNotificationAccess,
                        onAllowAccess = { context.startQuietly(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onOpen = { item -> sendContentIntent(item); controller.close() },
                    )
                }
            }
        }

        if (controller.lockScreen) {
            LockScreenOverlay(
                controller = controller,
                controls = controls,
                controlState = state,
                wallpaper = wallpaper,
                notifications = notifications,
                hideContent = prefs.hideNotificationContent,
            )
        }
    }

    // Back closes a panel; on the Lock Screen it only has to not fall through to the home screen.
    BackHandler(enabled = controller.isOpen) {
        if (controller.panel != null) controller.close() else controller.unlock()
    }
}

private class PendingPanel {
    var panel: Panel? = null
}

private fun sendContentIntent(item: CenterNotification) {
    val intent: PendingIntent = item.intent ?: return
    runCatching { intent.send() }
}
