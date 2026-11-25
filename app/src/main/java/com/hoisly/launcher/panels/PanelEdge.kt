package com.hoisly.launcher.panels

/** Share of the screen width, from the right, where a top-edge drag opens the Control Center. */
internal const val CONTROL_EDGE_SHARE: Float = 0.4f

/**
 * Which panel a drag from the top edge opens, like iOS: the right part of the edge opens the
 * Control Center, the rest the Notification Center. When only one panel is turned on, the
 * whole edge opens it. Null when both are off.
 */
internal fun panelForEdgeTouch(x: Float, width: Float, controlEnabled: Boolean, notificationsEnabled: Boolean): Panel? {
    val onRight = width > 0f && x >= width * (1f - CONTROL_EDGE_SHARE)
    return when {
        controlEnabled && notificationsEnabled -> if (onRight) Panel.Control else Panel.Notifications
        controlEnabled -> Panel.Control
        notificationsEnabled -> Panel.Notifications
        else -> null
    }
}
