package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme

/** Lets the user pick a direct Codex port or SSH for a server that offers both. */
@Composable
internal fun DiscoveryConnectionChoiceDialog(
    server: SavedServer,
    onDismiss: () -> Unit,
    onUseCodexPort: (Int) -> Unit,
    onUseSsh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连接 ${server.name.ifBlank { server.hostname }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    connectionChoiceMessage(server),
                    color = AgentBuddyTheme.textSecondary,
                )
                server.availableDirectCodexPorts.forEach { port ->
                    TextButton(
                        onClick = { onUseCodexPort(port) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("使用 Codex ($port)")
                    }
                }
                if (server.canConnectViaSsh) {
                    TextButton(
                        onClick = onUseSsh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("通过 SSH 连接", color = AgentBuddyTheme.accent)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        dismissButton = {},
    )
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
