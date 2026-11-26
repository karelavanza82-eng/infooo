package com.hoisly.launcher.island

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.Context
import com.hoisly.launcher.data.BadgeNotification
import com.hoisly.launcher.data.profileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cumulative per-app notification totals by [com.hoisly.launcher.data.badgeKey], published by the
 * connected [IslandListenerService]. Totals persist locally; the published feed is empty while
 * badges are off or the listener is disconnected.
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
    private val totals = LinkedHashMap<String, Long>()
    private val countedActiveKeys = LinkedHashSet<String>()
    private val prefs by lazy { service.getSharedPreferences("notification_badge_totals", Context.MODE_PRIVATE) }
    private var enabled = false
    private var connected = false
    private val flush = Runnable { publish() }

    /** Badges turned on or off in settings. Off drops everything and ignores events. */
    fun enabled(value: Boolean, active: () -> Array<StatusBarNotification>?): Unit {
        if (enabled == value) return
        enabled = value
        if (value && connected) reset(active()) else clear()
    }

    fun connected(active: Array<StatusBarNotification>?): Unit {
        connected = true
        if (enabled) reset(active)
    }

    fun disconnected(): Unit {
        connected = false
        clear()
    }

    fun posted(sbn: StatusBarNotification): Unit {
        if (!enabled || !connected) return
        val fresh = sbn.key !in countedActiveKeys
        entries[sbn.key] = sbn.badgeNotification()
        if (fresh) {
            countedActiveKeys += sbn.key
            val item = sbn.badgeNotification()
            val currentRanking = runCatching { service.currentRanking }.getOrNull()
            val ranking = NotificationListenerService.Ranking()
            val canBadge = currentRanking == null ||
                !currentRanking.getRanking(item.key, ranking) || ranking.canShowBadge()
            val groupSummary = item.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
            val eligible = item.packageName != service.packageName && canBadge && !item.media &&
                item.flags and (android.app.Notification.FLAG_ONGOING_EVENT or android.app.Notification.FLAG_FOREGROUND_SERVICE) == 0 &&
                !groupSummary
            if (eligible) {
                val key = com.hoisly.launcher.data.badgeKey(sbn.packageName, com.hoisly.launcher.data.profileId(sbn.user))
                val n = 1L
                totals[key] = addSaturated(totals[key] ?: 0L, n)
                persist()
            }
        }
        schedule()
    }

    fun removed(sbn: StatusBarNotification): Unit {
        if (!enabled || !connected) return
        if (entries.remove(sbn.key) != null) {
            countedActiveKeys.remove(sbn.key)
            prefs.edit().putStringSet(ACTIVE_KEYS, countedActiveKeys.toSet()).apply()
            schedule()
        }
    }

    /** A channel's badge setting may have changed: [publish] reads the current ranking. */
    fun rankingChanged(): Unit {
        if (enabled && connected && entries.isNotEmpty()) schedule()
    }

    private fun reset(active: Array<StatusBarNotification>?) {
        entries.clear()
        loadTotals()
        countedActiveKeys.clear()
        active?.forEach {
            entries[it.key] = it.badgeNotification()
            countedActiveKeys += it.key
        }
        prefs.edit().putStringSet(ACTIVE_KEYS, countedActiveKeys.toSet()).apply()
        Log.d(TAG, "rebuilt from ${entries.size} active notifications")
        main.removeCallbacks(flush)
        publish()
    }

    /** Badges disappear at once rather than freezing with old numbers. */
    private fun clear() {
        entries.clear()
        main.removeCallbacks(flush)
        NotificationBadgeFeed.publish(emptyMap())
    }

    private fun schedule() {
        if (!main.hasCallbacks(flush)) main.postDelayed(flush, COALESCE_MS)
    }

    private fun publish() {
        // Keep every accumulated app total visible even after its notifications are dismissed.
        NotificationBadgeFeed.publish(totals.toMap())
    }

    private fun loadTotals() {
        totals.clear()
        prefs.all.forEach { (key, value) ->
            if (value is Long && value > 0L) totals[key] = value
        }
    }

    private fun persist() {
        val editor = prefs.edit().clear()
        totals.forEach { (key, value) -> editor.putLong(key, value) }
        editor.putStringSet(ACTIVE_KEYS, countedActiveKeys.toSet())
        editor.apply()
    }

    private fun addSaturated(a: Long, b: Long): Long =
        if (b > 0L && Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b

    private fun StatusBarNotification.badgeNotification(): BadgeNotification {
        val extras = notification.extras
        return BadgeNotification(
            key = key,
            packageName = packageName,
            profile = profileId(user),
            number = notification.number,
            flags = notification.flags,
            media = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) || extras.getString(Notification.EXTRA_TEMPLATE) in MEDIA_TEMPLATES,
            groupKey = groupKey,
        )
    }

    private companion object {
        const val TAG = "HoislyBadges"
        const val ACTIVE_KEYS = "__active_notification_keys"
        const val COALESCE_MS = 250L
        val MEDIA_TEMPLATES = setOf(
            "android.app.Notification\$MediaStyle",
            "android.app.Notification\$DecoratedMediaCustomViewStyle",
        )
    }
}
