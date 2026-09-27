package com.akashark.agentbuddy.android.ui.conversation

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import uniffi.codex_mobile_client.AppServerTransportState

/** Task title for the header: the thread title, else its preview, else 「未命名任务」. */
internal fun conversationHeaderTitle(title: String?, preview: String?): String =
    title?.trim()?.takeIf { it.isNotEmpty() }
        ?: preview?.trim()?.takeIf { it.isNotEmpty() }
        ?: "未命名任务"

/**
 * Connection state shown in the header, from the server transport (the same
 * source as the composer's send gate). A failed connection step wins over a
 * plain "disconnected".
 */
internal fun conversationConnectionState(
    transportState: AppServerTransportState?,
    connectionFailed: Boolean,
): BuddyConnectionState =
    when {
        transportState == AppServerTransportState.CONNECTED -> BuddyConnectionState.CONNECTED
        connectionFailed -> BuddyConnectionState.FAILED
        transportState == AppServerTransportState.CONNECTING ||
            transportState == AppServerTransportState.UNRESPONSIVE -> BuddyConnectionState.CONNECTING
        else -> BuddyConnectionState.DISCONNECTED
    }

/**
 * 「搭档 · 主机」, with the connection state appended when not connected so
 * it reads without relying on the dot colour.
 */
internal fun conversationHeaderSubtitle(
    partner: String,
    hostName: String?,
    connection: BuddyConnectionState,
): String {
    val base = listOfNotNull(
        partner.trim().takeIf { it.isNotEmpty() },
        hostName?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
    if (connection == BuddyConnectionState.CONNECTED) return base
    return if (base.isEmpty()) connection.title else "$base · ${connection.title}"
}
