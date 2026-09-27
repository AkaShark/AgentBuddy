package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind

/** Remembers a freshly paired alleycat host in [SavedServerStore]. */
internal suspend fun saveAlleycatPairing(context: Context, result: AlleycatConnectedTarget) {
    SavedServerStore.rememberAlleycat(
        context = context,
        serverId = result.serverId,
        displayName = result.displayName,
        nodeId = result.nodeId,
        relay = result.params.relay,
        agentName = result.agentName,
        agentWire = alleycatWireStorageValue(result.agentWire),
    )
}

/** 「连接失败」 alert with the Rust message and one way out. */
@Composable
internal fun DiscoveryConnectErrorDialog(message: String, onDismiss: () -> Unit) {
    MintAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.ErrorOutline,
        iconTint = AgentBuddyTheme.danger,
        title = "连接失败",
        message = "$message\n\n确认电脑已开机并联网，然后重新选择连接方式。",
        confirmTitle = "确定",
        confirmKind = BuddyButtonKind.SECONDARY,
        onConfirm = onDismiss,
        dismissTitle = null,
    )
}
