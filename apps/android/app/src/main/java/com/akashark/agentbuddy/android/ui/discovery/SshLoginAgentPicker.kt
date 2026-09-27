package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentIconView
import com.akashark.agentbuddy.android.ui.common.BetaBadge
import com.akashark.agentbuddy.android.ui.common.isBeta
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AgentAvailabilityStatus
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import com.akashark.agentbuddy.android.ui.common.metadata
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.common.runtimeSortIndex
import uniffi.codex_mobile_client.RemoteAgentAvailability

internal data class SshBridgeAgentContext(
    val server: SavedServer,
    val sessionId: String,
    val host: String,
    val availability: List<RemoteAgentAvailability>,
    val credential: SavedSshCredential,
)

@Composable
internal fun SSHAgentPickerDialog(
    context: SshBridgeAgentContext,
    onDismiss: () -> Unit,
    onUseCodex: () -> Unit,
    onConnect: suspend (List<AgentRuntimeKind>) -> String?,
) {
    val scope = rememberCoroutineScope()
    val availableKinds = remember(context.sessionId) {
        availableSshBridgeKinds(context.availability)
    }
    var selectedKinds by remember(context.sessionId) {
        mutableStateOf(availableKinds.filterNot { it.isBeta }.toSet())
    }
    var isConnecting by remember(context.sessionId) { mutableStateOf(false) }
    var errorMessage by remember(context.sessionId) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("远程智能体") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "${context.server.name.ifBlank { context.host }}\n${context.host}",
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = 13.sp,
                )
                context.availability.forEach { agent ->
                    val enabled = isSshBridgeKind(agent.kind) &&
                        agent.status == AgentAvailabilityStatus.AVAILABLE &&
                        !isConnecting
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                selectedKinds = if (agent.kind in selectedKinds) {
                                    selectedKinds - agent.kind
                                } else {
                                    selectedKinds + agent.kind
                                }
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        AgentIconView(
                            kind = agent.kind,
                            sizeDp = 22,
                            modifier = Modifier.alpha(
                                if (agent.status == AgentAvailabilityStatus.AVAILABLE) 1f else 0.45f,
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sshRuntimeLabel(agent.kind),
                                    color = if (agent.status == AgentAvailabilityStatus.AVAILABLE) {
                                        AgentBuddyTheme.textPrimary
                                    } else {
                                        AgentBuddyTheme.textSecondary
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (agent.kind.isBeta) {
                                    Spacer(Modifier.width(6.dp))
                                    BetaBadge()
                                }
                            }
                            Text(
                                text = sshAgentStatusLabel(agent),
                                color = AgentBuddyTheme.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        if (agent.kind in selectedKinds) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = AgentBuddyTheme.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = AgentBuddyTheme.danger,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isConnecting && selectedKinds.isNotEmpty(),
                onClick = {
                    scope.launch {
                        isConnecting = true
                        errorMessage = onConnect(selectedKinds.sortedBy(::sshRuntimeSortRank))
                        isConnecting = false
                    }
                },
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AgentBuddyTheme.accent,
                    )
                } else {
                    Text("连接")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onUseCodex, enabled = !isConnecting) {
                    Text("使用 Codex SSH")
                }
                TextButton(onClick = onDismiss, enabled = !isConnecting) {
                    Text("取消")
                }
            }
        },
    )
}

internal fun availableSshBridgeKinds(agents: List<RemoteAgentAvailability>): List<AgentRuntimeKind> =
    agents
        .filter { isSshBridgeKind(it.kind) && it.status == AgentAvailabilityStatus.AVAILABLE }
        .map { it.kind }
        .sortedBy(::sshRuntimeSortRank)

private fun isSshBridgeKind(kind: AgentRuntimeKind): Boolean =
    kind.metadata?.capabilities?.supportsSshBridge ?: false

private fun sshRuntimeLabel(kind: AgentRuntimeKind): String = kind.runtimeLabel

private fun sshRuntimeSortRank(kind: AgentRuntimeKind): Int = kind.runtimeSortIndex

private fun sshAgentStatusLabel(agent: RemoteAgentAvailability): String = when (agent.status) {
    AgentAvailabilityStatus.AVAILABLE -> "可用"
    AgentAvailabilityStatus.AGENT_CLI_MISSING -> "缺少 CLI"
    AgentAvailabilityStatus.WINDOWS_NOT_YET_SUPPORTED -> "不支持 Windows"
}
