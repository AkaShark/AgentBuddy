package com.akashark.agentbuddy.android.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.akashark.agentbuddy.android.MainActivity
import com.akashark.agentbuddy.android.util.LLog
import uniffi.codex_mobile_client.AppClient

/** Android side of turn completion notifications: channel, posting, registration sync. */
object PushNotifications {
    /**
     * Must exist before the system displays a background FCM notification
     * that names it, so it is created at app start and in the FCM service.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            TURN_COMPLETE_CHANNEL_ID,
            "任务完成通知",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "远程主机上的任务完成或失败时通知你"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** App notifications allowed, POST_NOTIFICATIONS granted (33+), and the channel not blocked. */
    fun areEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(TURN_COMPLETE_CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /** Hand Rust the current registration; Rust dedupes an unchanged token. */
    fun syncRegistration(context: Context, client: AppClient, token: String?) {
        val registration = pushRegistrationFor(token, areEnabled(context))
        LLog.i("PushNotifications", "setPushRegistration registered=${registration != null}")
        client.setPushRegistration(registration)
    }

    /**
     * Post (or replace) a notification on [TURN_COMPLETE_CHANNEL_ID]. Id 0 +
     * [tag] matches how the FCM SDK posts the system-displayed copy, so the
     * two replace each other.
     */
    fun post(
        context: Context,
        tag: String,
        title: String,
        body: String,
        serverId: String?,
        threadId: String?,
    ) {
        ensureChannel(context)
        if (!areEnabled(context)) {
            LLog.i("PushNotifications", "notifications disabled; dropping tag=$tag")
            return
        }
        val notification = NotificationCompat.Builder(context, TURN_COMPLETE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, tag, serverId, threadId))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 0, notification)
        } catch (e: SecurityException) {
            LLog.w("PushNotifications", "notify rejected: ${e.message}")
        }
    }

    private fun contentIntent(
        context: Context,
        tag: String,
        serverId: String?,
        threadId: String?,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!serverId.isNullOrBlank() && !threadId.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_NOTIFICATION_SERVER_ID, serverId)
                putExtra(MainActivity.EXTRA_NOTIFICATION_THREAD_ID, threadId)
            }
        }
        return PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
