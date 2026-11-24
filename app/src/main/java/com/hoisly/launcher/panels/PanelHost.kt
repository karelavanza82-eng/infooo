package com.hoisly.launcher.panels

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.LauncherViewModel
import com.hoisly.launcher.island.IslandAccess
import com.hoisly.launcher.panels.notifications.CenterNotification
import com.hoisly.launcher.panels.notifications.NotificationCenterFeed
import com.hoisly.launcher.panels.notifications.NotificationCenterPanel
import com.hoisly.launcher.settings.LocalSettingsState
import com.hoisly.launcher.panels.control.ControlCenterPanel
import com.hoisly.launcher.panels.control.startQuietly

/** Height of the touch strip under the status bar that starts a panel drag. */
private val EdgeStripHeight = 32.dp

/**
 * Everything the top edge opens: the strip that starts a drag, the scrim and the
 * Notification Center. The panel follows the finger, settles with the sheet spring and is
 * interruptible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelHost(controller: PanelController, vm: LauncherViewModel) {
    val context = LocalContext.current
    val prefs = LocalSettingsState.current.panels
    val notifications by NotificationCenterFeed.items.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val menu by vm.menu.collectAsStateWithLifecycle()
    val folder by vm.openFolder.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val gallery by vm.widgetGallery.collectAsStateWithLifecycle()

    val visible = controller.isOpen
    val hasNotificationAccess = remember(visible) { IslandAccess.read(context).notifications }

    val stripEnabled = !controller.isOpen && !editMode && menu == null && folder == null && search == null && !gallery
    Box(Modifier.fillMaxSize().onSizeChanged {
        controller.heightPx = it.height.toFloat()
        controller.widthPx = it.width.toFloat()
    }) {
        if (stripEnabled && (prefs.notificationCenter || prefs.controlCenter)) {
            val pending = remember { PendingPanel() }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .fillMaxWidth()
                    .height(EdgeStripHeight)
                    .verticalPanelDrag(
                        accepts = { touch ->
                            pending.panel = panelForEdgeTouch(touch.x, controller.widthPx, prefs.controlCenter, prefs.notificationCenter)
                            pending.panel != null
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
                    .background(Color.Black.copy(alpha = 0.45f))
                    .pointerInput(Unit) { detectTapGestures { controller.close() } },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = -(1f - controller.fraction) * size.height
                    }
                    .background(Brush.verticalGradient(listOf(Color(0xF2101012), Color(0xE6101012))))
                    .verticalPanelDrag(
                        accepts = { controller.fraction > 0.5f },
                        onStart = {},
                        onDrag = controller::drag,
                        onEnd = controller::release,
                    )
                    .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
            ) {
                when (panel) {
                    Panel.Control -> ControlCenterPanel(onClose = controller::close)
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
    }

    BackHandler(enabled = controller.isOpen) { controller.close() }
}

private class PendingPanel {
    var panel: Panel? = null
}

private fun sendContentIntent(item: CenterNotification) {
    val intent: PendingIntent = item.intent ?: return
    runCatching { intent.send() }
}
