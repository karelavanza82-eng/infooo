package com.hoisly.launcher

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.hoisly.launcher.data.AppInfo

/** Android's public conversation-bubble API, adapted from Launchii for Hoisly's AppInfo model. */
object AppBubbles {
    const val REQUEST_NOTIFICATIONS = 4203
    private const val CHANNEL = "hoisly_app_bubbles"
    private const val NOTIFICATION_ID = 1

    fun supports(app: AppInfo): Boolean = app.user == Process.myUserHandle()

    fun openOrSetup(activity: Activity, app: AppInfo, fallback: () -> Unit) {
        if (!supports(app)) { fallback(); return }
        val nm = activity.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        if (!nm.areNotificationsEnabled()) {
            openSettings(activity, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            return
        }
        if (!nm.areBubblesEnabled() || nm.bubblePreference == NotificationManager.BUBBLE_PREFERENCE_NONE) {
            openSettings(activity, Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
            return
        }
        show(activity, app, fallback)
    }

    private fun openSettings(activity: Activity, action: String) {
        runCatching {
            activity.startActivity(Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
        }.onFailure {
            runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", activity.packageName, null))) }
        }
    }

    private fun show(context: Context, app: AppInfo, fallback: () -> Unit) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val shortcutId = "hoisly-bubble:" + app.component.flattenToShortString()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val icon = adaptiveIcon(context, app)
            val person = Person.Builder().setName(app.label).setIcon(icon).setKey(shortcutId).build()
            context.getSystemService(ShortcutManager::class.java).pushDynamicShortcut(
                ShortcutInfo.Builder(context, shortcutId)
                    .setShortLabel(app.label.take(40))
                    .setIcon(icon)
                    .setIntent(launch)
                    .setPerson(person)
                    .setLongLived(true)
                    .build()
            )
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hoisly App Bubbles", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Aplikasi yang dibuka dalam jendela bubble"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    setAllowBubbles(true)
                })
            }
            val pending = PendingIntent.getActivity(context, shortcutId.hashCode(), launch,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val metadata = Notification.BubbleMetadata.Builder(pending, icon)
                .setDesiredHeight(context.resources.displayMetrics.heightPixels)
                .setAutoExpandBubble(true)
                .setSuppressNotification(true)
                .build()
            val me = Person.Builder().setName("Hoisly").build()
            nm.notify(shortcutId, NOTIFICATION_ID,
                Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(app.label)
                    .setContentText("Buka dalam jendela mengambang")
                    .setContentIntent(pending)
                    .setShortcutId(shortcutId)
                    .setStyle(Notification.MessagingStyle(me).addMessage("Buka dalam bubble", System.currentTimeMillis(), person))
                    .setBubbleMetadata(metadata)
                    .setOnlyAlertOnce(true)
                    .build())
        } catch (_: Exception) {
            fallback()
        }
    }

    private fun adaptiveIcon(context: Context, app: AppInfo): Icon {
        val size = (108 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = app.info.getIcon(context.resources.displayMetrics.densityDpi)
        if (drawable is AdaptiveIconDrawable) {
            drawable.background?.let { it.setBounds(0, 0, size, size); it.draw(canvas) }
            drawable.foreground?.let { it.setBounds(0, 0, size, size); it.draw(canvas) }
        } else {
            canvas.drawColor(Color.WHITE)
            val inset = size * 22 / 108
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.draw(canvas)
        }
        return Icon.createWithAdaptiveBitmap(bitmap)
    }
}
