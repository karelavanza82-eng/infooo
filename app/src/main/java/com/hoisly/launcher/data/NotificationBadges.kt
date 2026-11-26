package com.hoisly.launcher.data

import android.app.Notification

/**
 * What badge counting needs from one posted notification. No title, text or other content:
 * only who posted it, how many items it stands for and how the system classifies it.
 */
data class BadgeNotification(
    val key: String,
    val packageName: String,
    /** [profileId] of the user that posted it, so personal and work apps stay apart. */
    val profile: String,
    /** [Notification.number]; 0 when the app didn't set it. */
    val number: Int = 0,
    /** [Notification.flags]. */
    val flags: Int = 0,
    /** A media player notification (MediaStyle or a media session). */
    val media: Boolean = false,
    /** [android.service.notification.NotificationListenerService.Ranking.canShowBadge]: the channel allows badges. */
    val canShowBadge: Boolean = true,
    /** [android.service.notification.StatusBarNotification.getGroupKey]. */
    val groupKey: String? = null,
)

/** Badge key of an app: package + profile, the same pair [AppInfo.badgeKey] gives every launcher activity of it. */
fun badgeKey(packageName: String, profile: String): String = "$packageName#$profile"

/**
 * Badge counts per [badgeKey]. A notification counts [Notification.number] when set, 1 otherwise.
 * Not counted: [ownPackage]'s notifications, channels without badges, ongoing and foreground
 * service notifications, media players, and a group summary whose group has other notifications
 * (the summary only stands for them; alone, the shade shows it as a notification of its own).
 * The same key is counted once.
 */
fun countBadges(notifications: Collection<BadgeNotification>, ownPackage: String): Map<String, Long> {
    val unique = LinkedHashMap<String, BadgeNotification>(notifications.size)
    notifications.forEach { unique[it.key] = it }
    val groupsWithMembers = unique.values
        .filter { it.groupKey != null && it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        .mapTo(HashSet()) { it.groupKey }
    val counts = HashMap<String, Long>()
    for (notification in unique.values) {
        if (!notification.badged(ownPackage, groupsWithMembers)) continue
        val amount = if (notification.number > 0) notification.number.toLong() else 1L
        counts.merge(badgeKey(notification.packageName, notification.profile), amount) { a, b ->
            if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
        }
    }
    return counts
}

private fun BadgeNotification.badged(ownPackage: String, groupsWithMembers: Set<String?>): Boolean = when {
    packageName == ownPackage || !canShowBadge || media -> false
    flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0 -> false
    flags and Notification.FLAG_GROUP_SUMMARY != 0 -> groupKey !in groupsWithMembers
    else -> true
}

/** A folder's badge: the sum over its apps, each package + profile once (two activities of one app don't double it). */
fun badgeSum(keys: Iterable<String>, counts: Map<String, Long>): Long {
    if (counts.isEmpty()) return 0L
    var sum = 0L
    keys.toSet().forEach { key -> val value = counts[key] ?: 0L; sum = if (Long.MAX_VALUE - sum < value) Long.MAX_VALUE else sum + value }
    return sum
}

/** Full cumulative count using Indonesian thousands separators; the stored value remains numeric. */
fun badgeText(count: Long): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("id", "ID")).format(count)
