package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind

/** Lets the user pick a direct Codex port or SSH for a server that offers both. */
@Composable
internal fun DiscoveryConnectionChoiceDialog(
    server: SavedServer,
    onDismiss: () -> Unit,
    onUseCodexPort: (Int) -> Unit,
    onUseSsh: () -> Unit,
) {
    MintAlertDialog(
        onDismissRequest = onDismiss,
        title = "连接 ${server.name.ifBlank { server.hostname }}",
        message = connectionChoiceMessage(server),
        confirmTitle = "取消",
        confirmKind = BuddyButtonKind.SECONDARY,
        onConfirm = onDismiss,
        dismissTitle = null,
    ) {
        server.availableDirectCodexPorts.forEach { port ->
            BuddyButton(
                text = "使用 Codex ($port)",
                onClick = { onUseCodexPort(port) },
                kind = BuddyButtonKind.SOFT,
                icon = Icons.Outlined.Lan,
            )
        }
        if (server.canConnectViaSsh) {
            BuddyButton(
                text = "通过 SSH 连接",
                onClick = onUseSsh,
                kind = BuddyButtonKind.SOFT,
                icon = Icons.Outlined.Terminal,
            )
        }
    }
}

private fun connectionChoiceMessage(server: SavedServer): String {
    val directPorts = server.availableDirectCodexPorts.map(Int::toString)
    if (directPorts.isEmpty()) {
        return "通过 SSH 在 ${server.hostname} 上引导启动 Codex。"
    }
    if (server.canConnectViaSsh) {
        return "Codex 可在端口 ${directPorts.joinToString(", ")} 上使用，SSH 也可在端口 ${server.resolvedSshPort} 上使用。"
    }
    return "在 ${server.hostname} 上选择一个 Codex app-server 端口。"
}
