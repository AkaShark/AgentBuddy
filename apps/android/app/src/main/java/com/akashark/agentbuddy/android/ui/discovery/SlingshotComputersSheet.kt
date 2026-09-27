package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionPill
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppSlingshotEnvironment

/** 「已连接电脑」 sheet of [SlingshotConnectFlow]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlingshotComputersSheet(
    environments: List<AppSlingshotEnvironment>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (AppSlingshotEnvironment) -> Unit,
) {
    BuddyBottomSheet(onDismissRequest = onDismiss) {
        SlingshotComputersContent(
            environments = environments,
            loading = loading,
            error = error,
            onCancel = onDismiss,
            onRefresh = onRefresh,
            onSelect = onSelect,
        )
    }
}

/** Stateless body of [SlingshotComputersSheet] (also rendered by the gallery). */
@Composable
internal fun SlingshotComputersContent(
    environments: List<AppSlingshotEnvironment>,
    loading: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (AppSlingshotEnvironment) -> Unit,
    modifier: Modifier = Modifier,
) {
    DiscoverySheetScaffold(
        modifier = modifier,
        contentSpacing = BuddySpacing.md,
        header = {
            DiscoverySheetHeader(
                title = "已连接电脑",
                actionTitle = "取消",
                onAction = onCancel,
                subtitle = "这些电脑来自使用你已登录账号的 ChatGPT。请先在电脑上启动 Codex，它才会显示在此处。",
                trailing = {
                    BuddyIconButton(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        onClick = onRefresh,
                        enabled = !loading,
                    )
                },
            )
        },
    ) {
        when {
            loading && environments.isEmpty() -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BuddySize.control)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AgentBuddyTheme.textSecondary,
                )
                Text(
                    text = "正在加载已连接电脑...",
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = AgentBuddyTheme.textSecondary,
                )
            }

            error != null -> BuddyEmptyState(
                icon = Icons.Outlined.WarningAmber,
                title = "无法加载已连接电脑",
                message = "$error\n确认已用 ChatGPT 登录，并在电脑上启动了 Codex，然后重试。",
                actionTitle = "重试",
                actionIcon = Icons.Outlined.Refresh,
                actionKind = BuddyButtonKind.SECONDARY,
                onAction = onRefresh,
            )

            environments.isEmpty() -> BuddyEmptyState(
                icon = Icons.Outlined.DesktopWindows,
                title = "没有找到已连接的电脑",
                message = "未找到此账号下已连接的电脑。在电脑上用同一 ChatGPT 账号启动 Codex，稍等片刻后刷新。",
                actionTitle = "刷新",
                actionIcon = Icons.Outlined.Refresh,
                actionKind = BuddyButtonKind.SECONDARY,
                onAction = onRefresh,
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AgentBuddyTheme.action,
                        trackColor = AgentBuddyTheme.border,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .buddyCard(BuddySurfaceTone.SURFACE, padding = null),
                ) {
                    environments.forEachIndexed { index, environment ->
                        if (index > 0) BuddyDivider(startIndent = BuddySpacing.md + BuddySize.rowTile + BuddySpacing.md)
                        ConnectedComputerRow(environment = environment, onClick = { onSelect(environment) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedComputerRow(
    environment: AppSlingshotEnvironment,
    onClick: () -> Unit,
) {
    val status = slingshotEnvironmentStatus(environment)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuddyShapes.card)
            .clickable(enabled = environment.online, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = status }
            .heightIn(min = BuddySize.listRow)
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuddyIconTile(
            content = BuddyTileContent.Symbol(slingshotEnvironmentIcon(environment)),
            foreground = if (environment.online) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = environment.displayName,
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = if (environment.online) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
                maxLines = 2,
            )
            Text(
                text = slingshotEnvironmentSubtitle(environment),
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 2,
            )
        }
        BuddyConnectionPill(
            state = when {
                !environment.online -> BuddyConnectionState.DISCONNECTED
                environment.busy -> BuddyConnectionState.CONNECTING
                else -> BuddyConnectionState.CONNECTED
            },
            title = status,
        )
    }
}

private fun slingshotEnvironmentSubtitle(environment: AppSlingshotEnvironment): String {
    val parts = buildList {
        environment.hostName?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        listOfNotNull(
            environment.operatingSystem.trim().takeIf { it.isNotEmpty() },
            environment.architecture?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").takeIf { it.isNotEmpty() }?.let(::add)
        environment.appServerVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Codex $it") }
    }
    return parts.ifEmpty { listOf(environment.id) }.joinToString(" - ")
}

private fun slingshotEnvironmentStatus(environment: AppSlingshotEnvironment): String =
    when {
        !environment.online -> "离线"
        environment.busy -> "忙碌"
        else -> "在线"
    }

private fun slingshotEnvironmentIcon(environment: AppSlingshotEnvironment): ImageVector =
    when (environment.operatingSystem.lowercase()) {
        "linux" -> Icons.Outlined.Dns
        "windows" -> Icons.Outlined.DesktopWindows
        "macos", "darwin" -> Icons.Outlined.DesktopWindows
        else -> Icons.Outlined.Laptop
    }
