package com.akashark.agentbuddy.android.ui.homeshell.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBrandMark
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyWordmark
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** One host in the 「N 台主机在线」 menu. */
data class HomeHostChoice(
    val serverId: String,
    val name: String,
    val isConnected: Boolean,
    val isConnecting: Boolean,
)

/** Entries of the header 「…」 menu; null hides an entry. */
class TasksHeaderActions(
    val onSelectHost: (HomeHostChoice) -> Unit,
    val onClearHost: () -> Unit,
    val onManageHosts: () -> Unit,
    val onPairWithQr: () -> Unit,
    val onShowSettings: () -> Unit,
    val onShowAllTasks: () -> Unit,
    val onShowApps: (() -> Unit)?,
    val onShowTerminal: (() -> Unit)?,
)

/**
 * 任务 header: wordmark, host filter pill (scope + host management), the
 * 「…」 launcher menu and the always-visible settings gear.
 */
@Composable
fun TasksHomeHeader(
    hosts: List<HomeHostChoice>,
    selectedServerId: String?,
    actions: TasksHeaderActions,
    modifier: Modifier = Modifier,
) {
    BuddyChromeTypeLimit {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            // With enlarged chrome text the host pill needs the wordmark's room.
            val enlarged = LocalDensity.current.fontScale > 1.15f
            val narrow = maxWidth < 360.dp || (enlarged && maxWidth < 440.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (narrow) {
                    BuddyBrandMark(
                        size = 32.dp,
                        modifier = Modifier.semantics {
                            contentDescription = "搭子"
                            heading()
                        },
                    )
                } else {
                    BuddyWordmark(markSize = 32.dp)
                }
                Box(
                    modifier = Modifier.weight(1f).padding(start = BuddySpacing.xs),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    HostFilterPill(hosts, selectedServerId, actions)
                }
                MoreMenu(actions)
                BuddyIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    onClick = actions.onShowSettings,
                    iconSize = 22.dp,
                )
            }
        }
    }
}

private fun chipState(hosts: List<HomeHostChoice>): BuddyConnectionState =
    when {
        hosts.isEmpty() -> BuddyConnectionState.DISCONNECTED
        hosts.any { it.isConnected } -> BuddyConnectionState.CONNECTED
        hosts.any { it.isConnecting } -> BuddyConnectionState.CONNECTING
        else -> BuddyConnectionState.DISCONNECTED
    }

private fun chipTitle(hosts: List<HomeHostChoice>, selectedServerId: String?): String {
    if (hosts.isEmpty()) return "尚未连接主机"
    hosts.firstOrNull { it.serverId == selectedServerId }?.let { return it.name }
    return "${hosts.count { it.isConnected }} 台主机在线"
}

@Composable
private fun HostFilterPill(
    hosts: List<HomeHostChoice>,
    selectedServerId: String?,
    actions: TasksHeaderActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = chipTitle(hosts, selectedServerId)
    Box {
        Box(
            modifier =
                Modifier
                    .heightIn(min = BuddySize.minHitTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "选择要显示哪台主机的任务") { expanded = true }
                    .semantics { contentDescription = title },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 36.dp)
                        .background(AgentBuddyTheme.surfaceSoft, CircleShape)
                        .padding(horizontal = BuddySpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(chipState(hosts).dotColor, CircleShape))
                Text(
                    text = title,
                    style = buddyTextStyle(BuddyTextStyle.LABEL),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AgentBuddyTheme.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = AgentBuddyTheme.surface) {
            val close = { expanded = false }
            if (hosts.isEmpty()) {
                HeaderMenuItem("扫码配对", Icons.Outlined.QrCodeScanner) { close(); actions.onPairWithQr() }
            } else {
                Text(
                    text = "显示任务来自",
                    style = buddyTextStyle(BuddyTextStyle.CAPTION),
                    color = AgentBuddyTheme.textSecondary,
                    modifier = Modifier.padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
                )
                HeaderMenuItem("全部主机", if (selectedServerId == null) Icons.Outlined.Check else null) {
                    close()
                    actions.onClearHost()
                }
                hosts.forEach { host ->
                    val icon =
                        when {
                            host.serverId == selectedServerId -> Icons.Outlined.Check
                            !host.isConnected -> Icons.Outlined.Refresh
                            else -> null
                        }
                    val label = if (host.isConnected || host.serverId == selectedServerId) host.name else "重新连接 ${host.name}"
                    HeaderMenuItem(label, icon) {
                        close()
                        actions.onSelectHost(host)
                    }
                }
            }
            HorizontalDivider(color = AgentBuddyTheme.border)
            HeaderMenuItem("管理主机", Icons.Outlined.Laptop) { close(); actions.onManageHosts() }
        }
    }
}

@Composable
private fun MoreMenu(actions: TasksHeaderActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BuddyIconButton(
            icon = Icons.Outlined.MoreHoriz,
            contentDescription = "更多",
            onClick = { expanded = true },
            iconSize = 22.dp,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = AgentBuddyTheme.surface) {
            HeaderMenuItem("全部任务", Icons.AutoMirrored.Outlined.List) {
                expanded = false
                actions.onShowAllTasks()
            }
            actions.onShowApps?.let { showApps ->
                HeaderMenuItem("应用", Icons.Outlined.Apps) {
                    expanded = false
                    showApps()
                }
            }
            actions.onShowTerminal?.let { showTerminal ->
                HeaderMenuItem("终端", Icons.Outlined.Terminal) {
                    expanded = false
                    showTerminal()
                }
            }
        }
    }
}

@Composable
private fun HeaderMenuItem(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, style = buddyTextStyle(BuddyTextStyle.BODY), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { if (icon != null) Icon(icon, contentDescription = null) else Spacer(Modifier.size(24.dp)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = AgentBuddyTheme.textPrimary, leadingIconColor = AgentBuddyTheme.textPrimary),
    )
}
