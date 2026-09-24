package com.akashark.agentbuddy.android.push

import java.security.MessageDigest
import uniffi.codex_mobile_client.AppPushPlatform
import uniffi.codex_mobile_client.AppPushRegistration
import uniffi.codex_mobile_client.ThreadKey

/**
 * Pure (Android-free) pieces of host-reported turn completion notifications
 * (host push design §7.4, §7.5, §8.3), kept separate so they are unit
 * testable. Subscribing / revoking is owned by the shared Rust PushManager.
 */

/** FCM data keys; identical to the Worker's APNs custom keys (§7.3). */
object PushDataKeys {
    const val SERVER_ID = "agentbuddy.notification.serverId"
    const val THREAD_ID = "agentbuddy.notification.threadId"
    const val TURN_ID = "agentbuddy.notification.turnId"
    const val KIND = "agentbuddy.notification.kind"
    const val EVENT_ID = "agentbuddy.notification.eventId"
    const val TYPE = "type"
    const val TYPE_DEBUG_BACKGROUND = "debug_background"
}

/** Used by Rust only for the best-effort direct revoke when a host is unreachable. */
const val PUSH_WORKER_BASE_URL = "https://agentbuddy-push-proxy.aaksharker.workers.dev"

/** Must match the Worker's `android.notification.channel_id`. */
const val TURN_COMPLETE_CHANNEL_ID = "turn_complete"

/** Tag for a debug alert that carries no routing keys. */
const val DEBUG_NOTIFICATION_TAG = "agentbuddy-debug"

/** SharedPreferences holding the last FCM token (read at cold start). */
const val PUSH_PREFS = "agentbuddy_push"
const val PUSH_PREFS_FCM_TOKEN = "fcm_token"

private const val ALLEYCAT_SERVER_PREFIX = "alleycat:"

enum class TurnPushKind { COMPLETED, FAILED }

sealed interface PushPayload {
    /** A host-reported terminal state (`kind` = completed | failed). */
    data class TurnTerminal(
        val serverId: String,
        val threadId: String,
        val turnId: String?,
        val kind: TurnPushKind,
        val eventId: String?,
    ) : PushPayload

    /** `/debug/push` alert mode: a notification without `kind`; routing keys optional. */
    data class DebugAlert(
        val serverId: String?,
        val threadId: String?,
        val turnId: String?,
    ) : PushPayload

    /** `/debug/push` background mode: data-only `type: debug_background`. */
    data object DebugBackground : PushPayload

    /** Anything else (legacy keepalive payloads, malformed or unknown kinds). */
    data object Ignored : PushPayload
}

fun parsePushPayload(data: Map<String, String>, hasNotification: Boolean): PushPayload {
    if (data[PushDataKeys.TYPE] == PushDataKeys.TYPE_DEBUG_BACKGROUND) {
        return PushPayload.DebugBackground
    }
    val serverId = data.nonBlank(PushDataKeys.SERVER_ID)
    val threadId = data.nonBlank(PushDataKeys.THREAD_ID)
    val turnId = data.nonBlank(PushDataKeys.TURN_ID)
    val rawKind = data.nonBlank(PushDataKeys.KIND)
    if (rawKind != null) {
        val kind = when (rawKind) {
            "completed" -> TurnPushKind.COMPLETED
            "failed" -> TurnPushKind.FAILED
            else -> return PushPayload.Ignored
        }
        if (serverId == null || threadId == null) return PushPayload.Ignored
        return PushPayload.TurnTerminal(
            serverId = serverId,
            threadId = threadId,
            turnId = turnId,
            kind = kind,
            eventId = data.nonBlank(PushDataKeys.EVENT_ID),
        )
    }
    if (hasNotification) {
        return PushPayload.DebugAlert(serverId = serverId, threadId = threadId, turnId = turnId)
    }
    return PushPayload.Ignored
}

/**
 * Same key as the Worker's `android.notification.tag` / APNs collapse-id:
 * `"t-" + first 32 hex of sha256(hostId|threadId|turnId)`, so a locally
 * posted notification and a system-displayed duplicate replace each other.
 */
fun turnNotificationTag(serverId: String, threadId: String, turnId: String?): String {
    val hostId = serverId.removePrefix(ALLEYCAT_SERVER_PREFIX)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$hostId|$threadId|${turnId.orEmpty()}".toByteArray(Charsets.UTF_8))
    return "t-" + digest.joinToString("") { "%02x".format(it) }.take(32)
}

/** Title / body when the message carries no notification block. */
fun turnNotificationFallbackText(kind: TurnPushKind): Pair<String, String> = when (kind) {
    TurnPushKind.COMPLETED -> "任务已完成" to "点击查看结果"
    TurnPushKind.FAILED -> "任务未完成" to "任务失败或已中断，点击查看详情"
}

/** Foreground only: skip the notification for the conversation already on screen. */
fun shouldSuppressTurnNotification(
    serverId: String,
    threadId: String,
    appInForeground: Boolean,
    visibleThread: ThreadKey?,
): Boolean = appInForeground &&
    visibleThread != null &&
    visibleThread.serverId == serverId &&
    visibleThread.threadId == threadId

/**
 * Registration handed to Rust. `null` (no token, or notifications disabled /
 * permission denied) makes Rust revoke this device's subscriptions.
 */
fun pushRegistrationFor(token: String?, notificationsEnabled: Boolean): AppPushRegistration? {
    val trimmed = token?.trim().orEmpty()
    if (!notificationsEnabled || trimmed.isEmpty()) return null
    return AppPushRegistration(
        platform = AppPushPlatform.ANDROID,
        token = trimmed,
        apnsEnvironment = null,
        workerBaseUrl = PUSH_WORKER_BASE_URL,
    )
}

private fun Map<String, String>.nonBlank(key: String): String? =
    this[key]?.trim()?.takeIf { it.isNotEmpty() }
