package com.akashark.agentbuddy.android.ui.homeshell.hosts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionPill
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Host management actions, shared by the 「…」 button and long-press. */
class HostActionHandlers(
    val onTap: (HostSummary) -> Unit,
    val onStartTask: (HostSummary) -> Unit,
    val onReconnect: (HostSummary) -> Unit,
    val onRestart: (HostSummary) -> Unit,
    val onRename: (HostSummary) -> Unit,
    val onEdit: (HostSummary) -> Unit,
    val onRemove: (HostSummary) -> Unit,
    val onTerminal: (HostSummary) -> Unit,
) {
    companion object {
        val None = HostActionHandlers({}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Host card. The featured style shows partners and 「在这台主机开始任务」;
 * the compact style is one summary block. Both expose the host menu through
 * a visible 「…」 button and a long-press.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HostCard(
    host: HostSummary,
    featured: Boolean,
    handlers: HostActionHandlers,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tile = BuddyTileContent.Symbol(if (host.isLocal) Icons.Outlined.PhoneAndroid else Icons.Outlined.Laptop)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .buddyCard(BuddySurfaceTone.SURFACE, padding = null)
                .combinedClickable(
                    onClickLabel = if (host.canStartTask) "显示这台主机的任务" else "重新连接这台主机",
                    onLongClickLabel = "主机操作",
                    onClick = { handlers.onTap(host) },
                    onLongClick = { menuOpen = true },
                ).semantics {
                    stateDescription = host.connection.title
                    customActions = listOf(CustomAccessibilityAction("主机操作") { menuOpen = true; true })
                }.padding(start = BuddySpacing.lg, end = BuddySpacing.lg, top = BuddySpacing.lg, bottom = BuddySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        if (featured) {
            Row(verticalAlignment = Alignment.Top) {
                BuddyIconTile(content = tile, size = 52.dp)
                Spacer(Modifier.weight(1f))
                BuddyConnectionPill(state = host.connection)
                HostMenuButton(host, handlers, menuOpen, onExpandedChange = { menuOpen = it })
            }
            Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
                HostName(host.name, BuddyTextStyle.TITLE)
                Text(
                    text = host.sourceTitle,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
            if (host.partners.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                ) {
                    host.partners.forEach { BuddyChip(it) }
                }
            }
            BuddyButton(
                text = "在这台主机开始任务",
                onClick = { handlers.onStartTask(host) },
                kind = BuddyButtonKind.SOFT,
                trailingIcon = Icons.Outlined.NorthEast,
                enabled = host.canStartTask,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BuddyIconTile(content = tile, size = 52.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HostName(host.name, BuddyTextStyle.HEADING)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(host.connection.dotColor, CircleShape))
                        Text(
                            text = "${host.sourceTitle} · ${host.connection.title}",
                            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                            color = AgentBuddyTheme.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HostMenuButton(host, handlers, menuOpen, onExpandedChange = { menuOpen = it })
            }
            Text(
                text = host.runningSummary,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

@Composable
private fun HostName(name: String, style: BuddyTextStyle) {
    Text(
        text = name,
        style = buddyTextStyle(style),
        color = AgentBuddyTheme.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HostMenuButton(
    host: HostSummary,
    handlers: HostActionHandlers,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Box(Modifier.offset(x = BuddySpacing.sm, y = (-BuddySpacing.xs))) {
        BuddyIconButton(
            icon = Icons.Outlined.MoreHoriz,
            contentDescription = "主机操作",
            onClick = { onExpandedChange(true) },
            tint = AgentBuddyTheme.textSecondary,
            iconSize = 20.dp,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }, containerColor = AgentBuddyTheme.surface) {
            val close = { onExpandedChange(false) }
            HostMenuItem("重新连接", Icons.Outlined.Refresh) { close(); handlers.onReconnect(host) }
            HostMenuItem("重启服务", Icons.Outlined.RestartAlt) { close(); handlers.onRestart(host) }
            if (host.canRename) {
                HostMenuItem("重命名", Icons.Outlined.DriveFileRenameOutline) { close(); handlers.onRename(host) }
            }
            if (host.canEditConnection) {
                HostMenuItem("编辑连接", Icons.Outlined.Settings) { close(); handlers.onEdit(host) }
            }
            if (host.hasTerminal) {
                HostMenuItem("终端", Icons.Outlined.Terminal) { close(); handlers.onTerminal(host) }
            }
            HorizontalDivider(color = AgentBuddyTheme.border)
            HostMenuItem("移除", Icons.Outlined.Delete, destructive = true) { close(); handlers.onRemove(host) }
        }
    }
}

@Composable
private fun HostMenuItem(
    text: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) AgentBuddyTheme.danger else AgentBuddyTheme.textPrimary
    DropdownMenuItem(
        text = { Text(text, style = buddyTextStyle(BuddyTextStyle.BODY)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
    )
}
