package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import com.akashark.agentbuddy.android.ui.common.isBeta
import com.akashark.agentbuddy.android.ui.common.metadata
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.common.runtimeSortIndex
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AgentAvailabilityStatus
import uniffi.codex_mobile_client.RemoteAgentAvailability

internal data class SshBridgeAgentContext(
    val server: SavedServer,
    val sessionId: String,
    val host: String,
    val availability: List<RemoteAgentAvailability>,
    val credential: SavedSshCredential,
)

/**
 * 「远程智能体」 sheet shown after an SSH login found SSH-bridge agents. Pick
 * the agents to start over the probed session, or fall back to Codex over
 * SSH. The sheet cannot be dismissed while connecting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SSHAgentPickerSheet(
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
    val connecting by rememberUpdatedState(isConnecting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !connecting },
    )
    val options = context.availability.map { agent ->
        val bridgeKind = isSshBridgeKind(agent.kind)
        DiscoveryAgentOption(
            key = agent.kind,
            kind = agent.kind,
            title = sshRuntimeLabel(agent.kind),
            detail = when {
                agent.status != AgentAvailabilityStatus.AVAILABLE -> sshAgentStatusLabel(agent)
                !bridgeKind -> "不能通过 SSH 桥接启动"
                else -> sshAgentStatusLabel(agent)
            },
            isBeta = agent.kind.isBeta,
            selectable = bridgeKind && agent.status == AgentAvailabilityStatus.AVAILABLE,
            selected = agent.kind in selectedKinds,
        )
    }

    BuddyBottomSheet(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        sheetState = sheetState,
    ) {
        SshAgentPickerContent(
            serverName = context.server.name.ifBlank { context.host },
            host = context.host,
            options = options,
            isConnecting = isConnecting,
            canConnect = !isConnecting && selectedKinds.isNotEmpty(),
            errorMessage = errorMessage,
            onToggle = { option, checked ->
                selectedKinds = if (checked) selectedKinds + option.kind else selectedKinds - option.kind
            },
            onConnect = {
                scope.launch {
                    isConnecting = true
                    errorMessage = onConnect(selectedKinds.sortedBy(::sshRuntimeSortRank))
                    isConnecting = false
                }
            },
            onUseCodex = onUseCodex,
            onCancel = onDismiss,
        )
    }
}

/** Stateless body of [SSHAgentPickerSheet] (also rendered by the gallery). */
@Composable
internal fun SshAgentPickerContent(
    serverName: String,
    host: String,
    options: List<DiscoveryAgentOption>,
    isConnecting: Boolean,
    canConnect: Boolean,
    errorMessage: String?,
    onToggle: (DiscoveryAgentOption, Boolean) -> Unit,
    onConnect: () -> Unit,
    onUseCodex: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DiscoverySheetScaffold(
        modifier = modifier,
        header = {
            DiscoverySheetHeader(
                title = "远程智能体",
                actionTitle = "取消",
                onAction = onCancel,
                actionEnabled = !isConnecting,
                subtitle = "选择要通过 SSH 在这台电脑上启动的智能体。",
            )
        },
        bottomBar = {
            Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                BuddyButton(
                    text = "连接",
                    onClick = onConnect,
                    enabled = canConnect,
                    isLoading = isConnecting,
                )
                BuddyButton(
                    text = "改用 Codex SSH",
                    onClick = onUseCodex,
                    kind = BuddyButtonKind.SECONDARY,
                    enabled = !isConnecting,
                )
            }
        },
    ) {
        DiscoveryHostSummary(icon = Icons.Outlined.Terminal, name = serverName, address = host)
        DiscoveryFormSection(title = "智能体") {
            DiscoveryAgentList(options = options, onToggle = onToggle, enabled = !isConnecting)
        }
        if (errorMessage != null) {
            BuddyBanner(
                tone = BuddyBannerTone.DANGER,
                message = "$errorMessage\n可以少选几个智能体再试，或改用 Codex SSH。",
            )
        }
    }
}

internal fun availableSshBridgeKinds(agents: List<RemoteAgentAvailability>): List<AgentRuntimeKind> =
    agents
        .filter { isSshBridgeKind(it.kind) && it.status == AgentAvailabilityStatus.AVAILABLE }
        .map { it.kind }
        .sortedBy(::sshRuntimeSortRank)

// Same rule as iOS: the SSH bridge bootstrap can launch claude / pi /
// opencode on the remote; Codex (and anything else) reaches the host through
// the guided Codex connect or the alleycat pairing path.
private fun isSshBridgeKind(kind: AgentRuntimeKind): Boolean {
    kind.metadata?.capabilities?.supportsSshBridge?.let { supports ->
        return supports && kind != "codex"
    }
    return kind == "claude" || kind == "pi" || kind == "opencode"
}

private fun sshRuntimeLabel(kind: AgentRuntimeKind): String = kind.runtimeLabel

private fun sshRuntimeSortRank(kind: AgentRuntimeKind): Int = kind.runtimeSortIndex

private fun sshAgentStatusLabel(agent: RemoteAgentAvailability): String = when (agent.status) {
    AgentAvailabilityStatus.AVAILABLE -> "可用"
    AgentAvailabilityStatus.AGENT_CLI_MISSING -> "缺少 CLI"
    AgentAvailabilityStatus.WINDOWS_NOT_YET_SUPPORTED -> "不支持 Windows"
}
