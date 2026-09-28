package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconToggleButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.discovery.MintButtonPair

/// Title, server switcher, hidden-folder toggle, folder search and the
/// up / go-to-path / breadcrumb row.
@Composable
internal fun DirectoryPickerHeader(
    state: DirectoryPickerViewState,
    actions: DirectoryPickerCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        BuddyChromeTypeLimit {
            Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
                Text(
                    text = "选择目录",
                    style = buddyTextStyle(BuddyTextStyle.TITLE),
                    color = AgentBuddyTheme.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.serverLabel?.let { "已连接：$it" } ?: "未选择服务器",
                        style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                        color = AgentBuddyTheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        BuddyContextChip(
                            text = "更改",
                            trailingIcon = Icons.Outlined.KeyboardArrowDown,
                            onClick = { actions.onShowServerMenuChange(true) },
                            enabled = state.servers.isNotEmpty(),
                            onClickLabel = "更改服务器",
                        )
                        DropdownMenu(
                            expanded = state.showServerMenu,
                            onDismissRequest = { actions.onShowServerMenuChange(false) },
                            containerColor = AgentBuddyTheme.surface,
                        ) {
                            state.servers.forEach { server ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${server.name} • ${server.sourceLabel}",
                                            style = buddyTextStyle(
                                                BuddyTextStyle.BODY,
                                                if (server.id == state.selectedServerId) FontWeight.SemiBold else null,
                                            ),
                                            color = AgentBuddyTheme.textPrimary,
                                        )
                                    },
                                    onClick = {
                                        actions.onShowServerMenuChange(false)
                                        actions.onSelectServer(server.id)
                                    },
                                )
                            }
                        }
                    }
                    BuddyIconToggleButton(
                        icon = Icons.Outlined.Visibility,
                        contentDescription = "显示隐藏文件夹",
                        checked = state.showHiddenDirectories,
                        onCheckedChange = { actions.onToggleHiddenDirectories() },
                    )
                }
            }
        }

        SessionsSearchField(
            query = state.searchQuery,
            onQueryChange = actions.onSearchQueryChange,
            placeholder = "搜索文件夹",
        )

        // Keep the current folder (the last crumb) in view as the path changes.
        val crumbScroll = rememberScrollState()
        LaunchedEffect(state.currentPath, crumbScroll.maxValue) {
            crumbScroll.scrollTo(crumbScroll.maxValue)
        }
        BuddyChromeTypeLimit {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                    BuddyContextChip(
                        text = "上一级",
                        icon = Icons.Outlined.ArrowUpward,
                        onClick = actions.onNavigateUp,
                        enabled = state.canGoUp,
                    )
                    BuddyContextChip(
                        text = "前往路径",
                        icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                        onClick = actions.onOpenGoToPath,
                        enabled = state.canBrowse,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(crumbScroll),
                    horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.segments.forEach { segment ->
                        BreadcrumbChip(
                            label = segment.label,
                            isCurrent = segment.path == state.currentPath,
                            onClick = { actions.onOpenPath(segment.path) },
                        )
                    }
                }
            }
        }
    }
}

/** Path crumb: the current folder on the brand fill, the others outlined. */
@Composable
private fun BreadcrumbChip(label: String, isCurrent: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = BuddySize.minHitTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isCurrent },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = buddyTextStyle(BuddyTextStyle.LABEL, if (isCurrent) FontWeight.SemiBold else FontWeight.Medium),
            color = if (isCurrent) AgentBuddyTheme.onBrand else AgentBuddyTheme.textPrimary,
            maxLines = 1,
            modifier = Modifier
                .defaultMinSize(minHeight = BuddySize.compactPill)
                .background(if (isCurrent) AgentBuddyTheme.brand else AgentBuddyTheme.surface, CircleShape)
                .then(if (isCurrent) Modifier else Modifier.border(1.dp, AgentBuddyTheme.border, CircleShape))
                .padding(horizontal = BuddySpacing.sm, vertical = 7.dp),
        )
    }
}

/// Current path + 取消 / 选择此文件夹 buttons.
@Composable
internal fun DirectoryPickerFooter(
    currentPathDisplay: String,
    canSelect: Boolean,
    onDismiss: () -> Unit,
    onSelectCurrentPath: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.background),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        BuddyDivider()
        Text(
            text = currentPathDisplay.ifBlank { "选择一个文件夹以开始新会话。" },
            style = buddyTextStyle(if (currentPathDisplay.isBlank()) BuddyTextStyle.CAPTION else BuddyTextStyle.CODE),
            color = AgentBuddyTheme.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = BuddySpacing.xs),
        )
        MintButtonPair(
            first = { buttonModifier ->
                BuddyButton(
                    text = "取消",
                    onClick = onDismiss,
                    kind = BuddyButtonKind.SECONDARY,
                    modifier = buttonModifier,
                )
            },
            second = { buttonModifier ->
                BuddyButton(
                    text = "选择此文件夹",
                    onClick = onSelectCurrentPath,
                    enabled = canSelect,
                    modifier = buttonModifier,
                )
            },
        )
    }
}
