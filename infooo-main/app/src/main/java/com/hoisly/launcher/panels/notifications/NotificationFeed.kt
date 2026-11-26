package com.hoisly.launcher.panels.notifications

import android.app.Notification
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One notification as the Notification Center shows it. [title] and [text] are null while
 * content is hidden by the user or by the app ([secret]); they are never copied in that case.
 */
data class CenterNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val clearable: Boolean,
    val secret: Boolean,
    val intent: PendingIntent?,
)

/**
 * Notifications published by the connected [com.hoisly.launcher.island.IslandListenerService] while
 * the Notification Center is on. Process memory only: empty when the center is off, the listener
 * is not connected or access was revoked, and rebuilt from the active notifications on connect.
 */
object NotificationCenterFeed {
    private val mutable = MutableStateFlow<List<CenterNotification>>(emptyList())
    val items: StateFlow<List<CenterNotification>> = mutable

    /** Cancels notifications in the system; set by the listener while it is connected. */
    internal interface Remover {
        fun cancel(key: String)
        fun cancelAll(keys: Collection<String>)
    }

    @Volatile
    internal var remover: Remover? = null

    internal fun publish(value: List<CenterNotification>) {
        mutable.value = value
    }

    /** Removed from the list at once; the system confirms through the listener. */
    fun dismiss(key: String) {
        mutable.value = mutable.value.filterNot { it.key == key }
        remover?.cancel(key)
    }

    fun dismissAll(keys: Collection<String>) {
        val set = keys.toSet()
        mutable.value = mutable.value.filterNot { it.key in set }
        remover?.cancelAll(set)
    }
}

/**
 * The listener's bookkeeping for the Notification Center, independent of badges and the Island.
 * Called on the main thread like every listener callback. Ongoing, foreground-service, group
 * summary and media notifications are left out, as on iOS. With [content] off no title or text
 * is read at all.
 */
internal class CenterTracker(private val service: NotificationListenerService) {
    private val main = Handler(Looper.getMainLooper())
    private val entries = LinkedHashMap<String, CenterNotification>()
    private val labels = HashMap<String, String>()
    private var enabled = false
    private var content = true
    private var connected = false
    private val flush = Runnable { publish() }

    private val remover = object : NotificationCenterFeed.Remover {
        override fun cancel(key: String) {
            runCatching { service.cancelNotification(key) }
        }

        override fun cancelAll(keys: Collection<String>) {
            runCatching { service.cancelNotifications(keys.toTypedArray()) }
        }
    }

    /** Settings changed: off drops everything; a content change rebuilds without or with text. */
    fun configure(enabled: Boolean, content: Boolean, active: () -> Array<StatusBarNotification>?) {
        if (this.enabled == enabled && this.content == content) return
        this.enabled = enabled
        this.content = content
        if (enabled && connected) reset(active()) else clear()
    }

    fun connected(active: Array<StatusBarNotification>?) {
        connected = true
        NotificationCenterFeed.remover = remover
        if (enabled) reset(active)
    }

    fun disconnected() {
        connected = false
        NotificationCenterFeed.remover = null
        clear()
    }

    fun posted(sbn: StatusBarNotification) {
        if (!enabled || !connected) return
        val entry = sbn.toEntry()
        val changed = if (entry != null) {
            entries[sbn.key] = entry
            true
        } else entries.remove(sbn.key) != null
        if (changed) schedule()
    }

    fun removed(sbn: StatusBarNotification) {
        if (!enabled || !connected) return
        if (entries.remove(sbn.key) != null) schedule()
    }

    private fun reset(active: Array<StatusBarNotification>?) {
        entries.clear()
        active?.forEach { sbn -> sbn.toEntry()?.let { entries[sbn.key] = it } }
        main.removeCallbacks(flush)
        publish()
    }

    private fun clear() {
        entries.clear()
        main.removeCallbacks(flush)
        NotificationCenterFeed.publish(emptyList())
    }

    private fun schedule() {
        if (!main.hasCallbacks(flush)) main.postDelayed(flush, COALESCE_MS)
    }

    private fun publish() {
        NotificationCenterFeed.publish(entries.values.sortedByDescending { it.postTime }.take(MAX_ITEMS))
    }

    private fun StatusBarNotification.toEntry(): CenterNotification? {
        if (packageName == service.packageName) return null
        val skipped = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY
        if (notification.flags and skipped != 0) return null
        val extras = notification.extras
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        val secret = notification.visibility == Notification.VISIBILITY_SECRET
        val readable = content && !secret
        return CenterNotification(
            key = key,
            packageName = packageName,
            appLabel = label(packageName),
            title = if (readable) extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(MAX_TITLE) else null,
            text = if (readable) extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(MAX_TEXT) else null,
            postTime = postTime,
            clearable = isClearable,
            secret = secret,
            intent = notification.contentIntent,
        )
    }

    private fun label(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            val packages = service.packageManager
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    private companion object {
        const val COALESCE_MS = 200L
        const val MAX_ITEMS = 100
        const val MAX_TITLE = 200
        const val MAX_TEXT = 400
    }
}
