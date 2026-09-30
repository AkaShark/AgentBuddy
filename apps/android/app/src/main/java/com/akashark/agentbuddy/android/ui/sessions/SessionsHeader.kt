package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionPill
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyPageHeader
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Back, 分叉当前任务, refresh and host info. Compact chrome, so its type is capped. */
@Composable
internal fun SessionsTopBar(
    state: SessionsViewState,
    actions: SessionsCallbacks,
    modifier: Modifier = Modifier,
) {
    BuddyChromeTypeLimit {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BuddyIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                onClick = actions.onBack,
                iconSize = 22.dp,
            )
            Box(Modifier.weight(1f))
            state.canForkCurrent?.let { canFork ->
                BuddyButton(
                    text = "分叉当前任务",
                    onClick = actions.onForkCurrent,
                    kind = BuddyButtonKind.QUIET,
                    enabled = canFork && !state.isForkingCurrent,
                    isLoading = state.isForkingCurrent,
                    fullWidth = false,
                )
            }
            if (state.isLoading && state.hasLoadedInitialSessions) {
                Box(Modifier.size(BuddySize.minHitTarget), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
            } else {
                BuddyIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "刷新任务",
                    onClick = actions.onRefresh,
                    enabled = !state.isLoading && state.connectedHostCount > 0,
                    iconSize = 22.dp,
                )
            }
            if (state.showsInfo) {
                BuddyIconButton(
                    icon = Icons.Outlined.Info,
                    contentDescription = "主机详情",
                    onClick = { actions.onInfo?.invoke() },
                    iconSize = 22.dp,
                )
            }
        }
    }
}

/** Page title with count, host summary, 新建任务, search field and filter chips. */
@Composable
internal fun SessionsHeaderBlock(
    state: SessionsViewState,
    actions: SessionsCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuddySpacing.md)) {
        BuddyPageHeader(
            title = state.title,
            titleStyle = BuddyTextStyle.TITLE,
            subtitle = sessionsCountLine(state),
        )
        BuddyChromeTypeLimit {
            BuddyConnectionPill(
                state = if (state.connectedHostCount > 0) BuddyConnectionState.CONNECTED else BuddyConnectionState.DISCONNECTED,
                title = if (state.connectedHostCount > 0) "${state.connectedHostCount} 台主机在线" else "未连接",
            )
        }
        if (state.canCreateTask) {
            BuddyButton(
                text = "新建任务",
                onClick = { actions.onNewTask?.invoke() },
                icon = Icons.Outlined.Add,
            )
        }
        if (state.totalCount > 0) {
            SessionsSearchField(query = state.searchQuery, onQueryChange = actions.onSearchQueryChange)
            SessionsFilterRow(state = state, actions = actions)
        }
        if (state.isLoading && state.totalCount > 0) {
            Row(
                modifier = Modifier
                    .heightIn(min = BuddySize.minHitTarget)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AgentBuddyTheme.textSecondary,
                )
                Text(
                    text = "正在加载更多任务…",
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
        }
    }
}

private fun sessionsCountLine(state: SessionsViewState): String =
    if (state.filteredCount == state.totalCount) {
        "共 ${state.totalCount} 个任务"
    } else {
        "显示 ${state.filteredCount} 个，共 ${state.totalCount} 个任务"
    }

/** Host menu, 只看分叉, sort menu and 清除筛选 in one horizontally scrolling row. */
@Composable
private fun SessionsFilterRow(state: SessionsViewState, actions: SessionsCallbacks) {
    var showServerMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val serverTitle = state.serverOptions.firstOrNull { it.id == state.serverFilterId }?.name ?: "全部主机"

    BuddyChromeTypeLimit {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.serverOptions.isNotEmpty()) Box {
                SessionsFilterChip(
                    title = serverTitle,
                    icon = Icons.Outlined.Laptop,
                    selected = state.serverFilterId != null,
                    opensMenu = true,
                    onClickLabel = "选择显示哪台主机的任务",
                    onClick = { showServerMenu = true },
                )
                DropdownMenu(
                    expanded = showServerMenu,
                    onDismissRequest = { showServerMenu = false },
                    containerColor = AgentBuddyTheme.surface,
                ) {
                    SessionsMenuItem("全部主机", selected = state.serverFilterId == null) {
                        showServerMenu = false
                        actions.onSelectServer(null)
                    }
                    state.serverOptions.forEach { option ->
                        SessionsMenuItem(option.name, selected = state.serverFilterId == option.id) {
                            showServerMenu = false
                            actions.onSelectServer(option.id)
                        }
                    }
                }
            }
            SessionsFilterChip(
                title = "只看分叉",
                icon = Icons.AutoMirrored.Outlined.CallSplit,
                selected = state.showOnlyForks,
                onClick = actions.onToggleForksOnly,
            )
            Box {
                SessionsFilterChip(
                    title = state.sortMode.title,
                    icon = Icons.Outlined.SwapVert,
                    selected = state.sortMode != WorkspaceSortMode.RECENT,
                    opensMenu = true,
                    onClickLabel = "排序",
                    onClick = { showSortMenu = true },
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    containerColor = AgentBuddyTheme.surface,
                ) {
                    WorkspaceSortMode.entries.forEach { mode ->
                        SessionsMenuItem(mode.title, selected = state.sortMode == mode) {
                            showSortMenu = false
                            actions.onSelectSort(mode)
                        }
                    }
                }
            }
            if (state.hasActiveFilters) {
                BuddyButton(
                    text = "清除筛选",
                    onClick = actions.onClearFilters,
                    kind = BuddyButtonKind.QUIET,
                    fullWidth = false,
                )
            }
        }
    }
}

@Composable
private fun SessionsMenuItem(title: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.BODY, if (selected) FontWeight.SemiBold else null),
                color = AgentBuddyTheme.textPrimary,
            )
        },
        trailingIcon = if (selected) {
            { SessionsCheckIcon() }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier.background(AgentBuddyTheme.surface),
    )
}

@Composable
private fun SessionsCheckIcon() {
    Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = "已选",
        tint = AgentBuddyTheme.action,
        modifier = Modifier.size(BuddySize.icon),
    )
}
