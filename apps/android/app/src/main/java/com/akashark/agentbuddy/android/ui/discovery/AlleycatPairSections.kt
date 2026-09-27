package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentIconView
import com.akashark.agentbuddy.android.ui.common.BetaBadge
import com.akashark.agentbuddy.android.ui.common.isBetaAgentName
import uniffi.codex_mobile_client.AppAlleycatAgentInfo
import uniffi.codex_mobile_client.AppAlleycatAgentWire
import uniffi.codex_mobile_client.AppAlleycatPairPayload

/** Collapsible paste-JSON fallback of [AlleycatAddServerSheet]. */
@Composable
internal fun AlleycatPastePairJsonSection(
    showPaste: Boolean,
    onTogglePaste: () -> Unit,
    pasteJson: String,
    onPasteJsonChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    parsedParams: AppAlleycatPairPayload?,
    onParse: () -> Unit,
) {
    DisclosureRow(
        expanded = showPaste,
        label = "粘贴配对 JSON",
        onToggle = onTogglePaste,
    )
    if (showPaste) {
        OutlinedTextField(
            value = pasteJson,
            onValueChange = onPasteJsonChange,
            placeholder = {
                Text(
                    text = "{\"v\":1,\"node_id\":\"...\",\"token\":\"...\",\"relay\":\"https://...\"}",
                    color = AgentBuddyTheme.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onPasteFromClipboard,
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = AgentBuddyTheme.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("从剪贴板粘贴", color = AgentBuddyTheme.accent)
            }
            TextButton(
                onClick = onParse,
                enabled = pasteJson.trim().isNotEmpty(),
            ) {
                Text(
                    text = if (parsedParams == null) "解析 JSON" else "重新解析 JSON",
                    color = AgentBuddyTheme.accent,
                )
            }
        }
    }
}

/** Scanned-host preview, display name and agent multi-select of [AlleycatAddServerSheet]. */
@Composable
internal fun AlleycatScannedHostSection(
    params: AppAlleycatPairPayload,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    agents: List<AppAlleycatAgentInfo>,
    availableAgents: List<AppAlleycatAgentInfo>,
    selectedAgents: List<AppAlleycatAgentInfo>,
    selectedAgentNames: Set<String>,
    isLoadingAgents: Boolean,
    onToggleAllAgents: () -> Unit,
    onAgentCheckedChange: (AppAlleycatAgentInfo, Boolean) -> Unit,
) {
    SectionHeader(label = "扫描到的主机")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PreviewRow("node", shortNodeId(params.nodeId))
        PreviewRow("protocol", "v${params.v.toInt()}")
        params.relay?.takeIf { it.isNotBlank() }?.let {
            PreviewRow("relay", it)
        }
        params.hostName?.takeIf { it.isNotBlank() }?.let {
            PreviewRow("host", it)
        }
    }

    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text("显示名称（可选）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(label = "智能体", modifier = Modifier.weight(1f))
        if (availableAgents.isNotEmpty()) {
            TextButton(
                onClick = onToggleAllAgents,
            ) {
                Text(
                    text = if (selectedAgents.size == availableAgents.size) "无" else "全部",
                    color = AgentBuddyTheme.accent,
                    fontSize = 12.sp,
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        when {
            isLoadingAgents -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AgentBuddyTheme.accent,
                )
                Spacer(Modifier.width(8.dp))
                Text("正在加载智能体", color = AgentBuddyTheme.textSecondary, fontSize = 12.sp)
            }
            agents.isEmpty() -> Text(
                text = "此主机上没有可用的智能体。",
                color = AgentBuddyTheme.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp),
            )
            else -> agents.forEach { agent ->
                AgentRow(
                    agent = agent,
                    selected = agent.name in selectedAgentNames,
                    onCheckedChange = { checked -> onAgentCheckedChange(agent, checked) },
                )
            }
        }
    }
}

@Composable
private fun AgentRow(
    agent: AppAlleycatAgentInfo,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Plain clickable Row instead of TextButton — TextButton injects
    // Material's minimum touch target (~48dp) plus internal content
    // padding, which made each agent row much taller than the actual
    // text content needed and forced the agent list to take far more
    // vertical space than necessary on small screens.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (agent.available) {
                    Modifier.clickable { onCheckedChange(!selected) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        AgentIconView(
            kind = agent.name,
            sizeDp = 22,
            modifier = Modifier.alpha(if (agent.available) 1f else 0.45f),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = agent.displayName,
                    color = if (agent.available) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (isBetaAgentName(agent.name, agent.displayName)) {
                    Spacer(Modifier.width(6.dp))
                    BetaBadge()
                }
            }
            Text(
                text = wireLabel(agent.wire),
                color = AgentBuddyTheme.textSecondary,
                fontSize = 11.sp,
            )
        }
        if (!agent.available) {
            Text("不可用", color = AgentBuddyTheme.textMuted, fontSize = 11.sp)
        } else {
            Checkbox(
                checked = selected,
                onCheckedChange = onCheckedChange,
                enabled = true,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
internal fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        color = AgentBuddyTheme.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = AgentBuddyTheme.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = AgentBuddyTheme.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DisclosureRow(
    expanded: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = (if (expanded) "▾ " else "▸ ") + label,
            color = AgentBuddyTheme.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun wireLabel(wire: AppAlleycatAgentWire): String = when (wire) {
    AppAlleycatAgentWire.WEBSOCKET -> "websocket"
    AppAlleycatAgentWire.JSONL -> "jsonl"
}
