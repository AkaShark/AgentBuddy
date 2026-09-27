package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.currentConnectionStep
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppConnectionStepKind
import uniffi.codex_mobile_client.AppConnectionStepState
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppServerTransportState

/** Render-only projection of a server snapshot for the settings list. */
internal data class SettingsServerItem(
    val id: String,
    val name: String,
    val isLocal: Boolean,
    val connection: BuddyConnectionState,
    val statusText: String,
)

/** Display name with the shared "This Device" sentinel mapped at render time. */
internal fun settingsServerDisplayName(server: AppServerSnapshot): String =
    if (server.displayName == "This Device") "本设备" else server.displayName

/** Maps the typed transport / connection-step state to a Mint connection pill. */
internal fun AppServerSnapshot.toSettingsServerItem(): SettingsServerItem {
    val step = currentConnectionStep
    val (state, title) =
        when {
            step?.state == AppConnectionStepState.FAILED -> BuddyConnectionState.FAILED to "连接失败"
            step?.state == AppConnectionStepState.AWAITING_USER_INPUT -> BuddyConnectionState.CONNECTING to "等待你操作"
            step != null ->
                when (step.kind) {
                    AppConnectionStepKind.CONNECTING_TO_SSH -> BuddyConnectionState.CONNECTING to "正在连接 SSH…"
                    AppConnectionStepKind.FINDING_CODEX -> BuddyConnectionState.CONNECTING to "正在查找 Codex…"
                    AppConnectionStepKind.INSTALLING_CODEX -> BuddyConnectionState.CONNECTING to "正在安装 Codex…"
                    AppConnectionStepKind.STARTING_APP_SERVER -> BuddyConnectionState.CONNECTING to "正在启动服务…"
                    AppConnectionStepKind.OPENING_TUNNEL -> BuddyConnectionState.CONNECTING to "正在建立隧道…"
                    AppConnectionStepKind.CONNECTED -> BuddyConnectionState.CONNECTED to "已连接"
                }
            transportState == AppServerTransportState.CONNECTED && !isLocal && account == null ->
                BuddyConnectionState.CONNECTING to "需要登录"
            else ->
                when (transportState) {
                    AppServerTransportState.CONNECTED -> BuddyConnectionState.CONNECTED to "已连接"
                    AppServerTransportState.CONNECTING -> BuddyConnectionState.CONNECTING to "正在连接…"
                    AppServerTransportState.UNRESPONSIVE -> BuddyConnectionState.FAILED to "无响应"
                    AppServerTransportState.DISCONNECTED -> BuddyConnectionState.DISCONNECTED to "已断开"
                    AppServerTransportState.UNKNOWN -> BuddyConnectionState.DISCONNECTED to "状态未知"
                }
        }
    return SettingsServerItem(
        id = serverId,
        name = settingsServerDisplayName(this),
        isLocal = isLocal,
        connection = state,
        statusText = "$title · ${if (isLocal) "本地" else "远程"}",
    )
}

/** Server row: device icon, name, connection pill and an actions menu (编辑 / 重命名 / 移除). */
@Composable
internal fun SettingsServerRow(
    item: SettingsServerItem,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    SettingsRow(
        title = item.name,
        icon = if (item.isLocal) Icons.Outlined.PhoneAndroid else Icons.Outlined.Dns,
        below = { SettingsConnectionLine(item.connection, item.statusText) },
        trailing = {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                BuddyIconButton(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = "${item.name} 的服务器操作",
                    onClick = { showMenu = true },
                    iconSize = BuddySize.icon,
                    tint = AgentBuddyTheme.textSecondary,
                )
                SettingsDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    SettingsMenuItem("编辑", Icons.Outlined.Edit) {
                        showMenu = false
                        onEdit()
                    }
                    SettingsMenuItem("重命名", Icons.Outlined.DriveFileRenameOutline) {
                        showMenu = false
                        onRename()
                    }
                    SettingsMenuItem("移除", Icons.Outlined.DeleteOutline, color = AgentBuddyTheme.danger) {
                        showMenu = false
                        onRemove()
                    }
                }
            }
        },
    )
}

/** Dot + wrapping status text (a pill would truncate at large font sizes). */
@Composable
private fun SettingsConnectionLine(state: BuddyConnectionState, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(state.dotColor, CircleShape))
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.CAPTION),
            color = AgentBuddyTheme.textSecondary,
        )
    }
}

/** Mint-themed dropdown: surface container, hairline border, 16dp corners. */
@Composable
internal fun SettingsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = BuddyShapes.detailCard,
        containerColor = AgentBuddyTheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, AgentBuddyTheme.border),
        content = content,
    )
}

@Composable
internal fun SettingsMenuItem(
    text: String,
    icon: ImageVector,
    color: Color = AgentBuddyTheme.textPrimary,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, style = buddyTextStyle(BuddyTextStyle.BODY)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        colors =
            MenuDefaults.itemColors(
                textColor = color,
                leadingIconColor = if (color == AgentBuddyTheme.textPrimary) AgentBuddyTheme.textSecondary else color,
            ),
    )
}
