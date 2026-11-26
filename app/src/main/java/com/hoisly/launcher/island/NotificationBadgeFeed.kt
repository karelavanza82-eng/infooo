package com.hoisly.launcher.island

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hoisly.launcher.data.BadgeNotification
import com.hoisly.launcher.data.profileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-app counts of currently active notifications, published by the connected
 * [IslandListenerService]. Dismissed notifications stop contributing to the badge.
 */
object NotificationBadgeFeed {
    private val mutable = MutableStateFlow<Map<String, Long>>(emptyMap())
    val counts: StateFlow<Map<String, Long>> = mutable

    internal fun publish(value: Map<String, Long>): Unit {
        mutable.value = value
    }
}

/**
 * The listener's badge bookkeeping, independent of the Island window. Keeps the minimal
 * [BadgeNotification] of each active notification by key (no text or extras are copied), adds
 * one to the persistent app total only for a new notification key, and coalesces UI updates.
 * Called on the main thread, like every listener callback.
 */
internal class BadgeTracker(private val service: NotificationListenerService) {
    private val main = Handler(Looper.getMainLooper())
    private val entries = LinkedHashMap<String, BadgeNotification>()
    private var enabled = false
    private var connected = false
    private val flush = Runnable { publish() }

    fun enabled(value: Boolean, active: () -> Array<StatusBarNotification>?) {
        if (enabled == value) return
        enabled = value
        if (value && connected) reset(active()) else clear()
    }

    fun connected(active: Array<StatusBarNotification>?) {
        connected = true
        if (enabled) reset(active)
    }

    fun disconnected() {
        connected = false
        clear()
    }

    fun posted(sbn: StatusBarNotification) {
        if (!enabled || !connected) return
        entries[sbn.key] = sbn.badgeNotification()
        schedule()
    }

    fun removed(sbn: StatusBarNotification) {
        if (!enabled || !connected) return
        if (entries.remove(sbn.key) != null) schedule()
    }

    fun rankingChanged() {
        if (enabled && connected && entries.isNotEmpty()) schedule()
    }

    private fun reset(active: Array<StatusBarNotification>?) {
        entries.clear()
        active?.forEach { sbn -> entries[sbn.key] = sbn.badgeNotification() }
        main.removeCallbacks(flush)
        publish()
    }

    private fun clear() {
        entries.clear()
        main.removeCallbacks(flush)
        NotificationBadgeFeed.publish(emptyMap())
    }

    private fun schedule() {
        if (!main.hasCallbacks(flush)) main.postDelayed(flush, COALESCE_MS)
    }

    /**
     * Badges reflect notifications that are currently active in Android's notification shade.
     * Dismissed notifications are removed from the count; no lifetime/cumulative total is kept.
     * Notification.number is honored when the app supplies a grouped unread count.
     */
    private fun publish() {
        val rankingMap = runCatching { service.currentRanking }.getOrNull()
        val ranked = entries.values.map { item ->
            val ranking = NotificationListenerService.Ranking()
            val canBadge = rankingMap == null ||
                !rankingMap.getRanking(item.key, ranking) || ranking.canShowBadge()
            item.copy(canShowBadge = canBadge)
        }
        NotificationBadgeFeed.publish(
            com.hoisly.launcher.data.countBadges(ranked, service.packageName)
        )
    }

    private fun StatusBarNotification.badgeNotification(): BadgeNotification {
        val extras = notification.extras
        return BadgeNotification(
            key = key,
            packageName = packageName,
            profile = com.hoisly.launcher.data.profileId(user),
            number = notification.number,
            flags = notification.flags,
            media = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                extras.getString(Notification.EXTRA_TEMPLATE) in MEDIA_TEMPLATES,
            groupKey = groupKey,
        )
    }

    private companion object {
        const val COALESCE_MS = 250L
        val MEDIA_TEMPLATES = setOf(
            "android.app.Notification\$MediaStyle",
            "android.app.Notification\$DecoratedMediaCustomViewStyle",
        )
    }
}
