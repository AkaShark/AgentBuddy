package com.akashark.agentbuddy.android.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.VisibleThreadTracker
import com.akashark.agentbuddy.android.util.LLog

/**
 * Host-reported turn completion notifications (host push design §7.4, §8.3).
 *
 * In the background the system displays the Worker's notification on
 * [TURN_COMPLETE_CHANNEL_ID] itself and a tap launches MainActivity with the
 * data keys as extras; only foreground messages (and data-only debug pushes)
 * reach [onMessageReceived].
 */
class AgentBuddyFirebaseMessagingService : FirebaseMessagingService() {
    private companion object {
        const val TAG = "AgentBuddyFCM"
    }

    override fun onCreate() {
        super.onCreate()
        // A cold process started only to display a background notification
        // must still have the channel the Worker names.
        PushNotifications.ensureChannel(this)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val notification = remoteMessage.notification
        when (val payload = parsePushPayload(remoteMessage.data, hasNotification = notification != null)) {
            is PushPayload.TurnTerminal -> {
                if (shouldSuppressTurnNotification(
                        serverId = payload.serverId,
                        threadId = payload.threadId,
                        appInForeground = VisibleThreadTracker.appInForeground,
                        visibleThread = VisibleThreadTracker.visibleThread,
                    )
                ) {
                    LLog.i(TAG, "turn ${payload.kind} for the visible thread; not notifying event=${payload.eventId}")
                    return
                }
                val (fallbackTitle, fallbackBody) = turnNotificationFallbackText(payload.kind)
                PushNotifications.post(
                    context = this,
                    tag = notification?.tag?.takeIf { it.isNotBlank() }
                        ?: turnNotificationTag(payload.serverId, payload.threadId, payload.turnId),
                    title = notification?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                    body = notification?.body?.takeIf { it.isNotBlank() } ?: fallbackBody,
                    serverId = payload.serverId,
                    threadId = payload.threadId,
                )
            }
            is PushPayload.DebugAlert -> {
                val tag = notification?.tag?.takeIf { it.isNotBlank() }
                    ?: if (payload.serverId != null && payload.threadId != null) {
                        turnNotificationTag(payload.serverId, payload.threadId, payload.turnId)
                    } else {
                        DEBUG_NOTIFICATION_TAG
                    }
                LLog.i(TAG, "debug alert received; posting tag=$tag")
                PushNotifications.post(
                    context = this,
                    tag = tag,
                    title = notification?.title?.takeIf { it.isNotBlank() } ?: "调试通知",
                    body = notification?.body.orEmpty(),
                    serverId = payload.serverId,
                    threadId = payload.threadId,
                )
            }
            PushPayload.DebugBackground ->
                LLog.i(TAG, "debug background push received id=${remoteMessage.messageId}")
            PushPayload.Ignored ->
                LLog.d(TAG, "ignoring push keys=${remoteMessage.data.keys}")
        }
    }

    override fun onNewToken(token: String) {
        getSharedPreferences(PUSH_PREFS, MODE_PRIVATE)
            .edit()
            .putString(PUSH_PREFS_FCM_TOKEN, token)
            .apply()
        // A live process hands the rotated token to Rust now so active turns
        // re-subscribe with it. A cold process has no connections; MainActivity
        // reads the persisted token when it starts.
        val model = AppModel.sharedOrNull ?: return
        PushNotifications.syncRegistration(applicationContext, model.client, token)
    }
}
