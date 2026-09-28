package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import uniffi.codex_mobile_client.AppServerHealth

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

/**
 * The check that runs after a QR pairing (添加主机 and home 「扫码连接」): watches
 * the paired host until it connects ([onConnected]) or Rust reports a terminal
 * connection error, which is shown as 「连接失败」 ([onFinished] once dismissed).
 */
@Composable
internal fun AlleycatPairingWatcher(
    serverId: String,
    onConnected: () -> Unit,
    onFinished: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val connected by rememberUpdatedState(onConnected)
    var failure by remember(serverId) { mutableStateOf<String?>(null) }

    LaunchedEffect(snapshot, serverId, failure) {
        if (failure != null) return@LaunchedEffect
        val server = snapshot?.servers?.firstOrNull { it.serverId == serverId } ?: return@LaunchedEffect
        if (server.isConnected) {
            connected()
        } else if (server.health == AppServerHealth.DISCONNECTED) {
            server.connectionProgress?.terminalMessage?.let { failure = it }
        }
    }

    failure?.let { message -> DiscoveryConnectErrorDialog(message = message, onDismiss = onFinished) }
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
