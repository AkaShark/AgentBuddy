package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyPageBackground
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.discovery.MintContentMaxWidth
import com.akashark.agentbuddy.android.ui.discovery.mintPageGutter
import uniffi.codex_mobile_client.ThreadKey

/** User actions of the 全部任务 screen. */
internal class SessionsCallbacks(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onInfo: (() -> Unit)?,
    val onForkCurrent: () -> Unit,
    val onNewTask: (() -> Unit)?,
    val onConnectHost: (() -> Unit)?,
    val onSearchQueryChange: (String) -> Unit,
    val onSelectServer: (String?) -> Unit,
    val onToggleForksOnly: () -> Unit,
    val onSelectSort: (WorkspaceSortMode) -> Unit,
    val onClearFilters: () -> Unit,
    val onToggleGroup: (String) -> Unit,
    val onToggleNode: (ThreadKey) -> Unit,
    val onOpen: (ThreadKey) -> Unit,
    val onFork: (ThreadKey) -> Unit,
    val onRename: (ThreadKey) -> Unit,
    val onArchive: (ThreadKey) -> Unit,
)

/** Number of list items above the first project section (the header block). */
internal const val SESSIONS_HEADER_ITEM_COUNT = 1

/**
 * Stateless 全部任务 screen: top bar, header block, then tasks grouped by
 * project (collapsible), or the loading / empty / no-match state.
 */
@Composable
internal fun SessionsContent(
    state: SessionsViewState,
    actions: SessionsCallbacks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val gutter = mintPageGutter()
    Column(modifier = modifier.fillMaxSize().buddyPageBackground()) {
        SessionsTopBar(state = state, actions = actions)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = MintContentMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = BuddySpacing.xxl),
            ) {
                item(key = "header") {
                    SessionsHeaderBlock(
                        state = state,
                        actions = actions,
                        modifier = Modifier.padding(horizontal = gutter).padding(bottom = BuddySpacing.xs),
                    )
                }
                when {
                    state.totalCount == 0 -> item(key = "empty") {
                        Box(Modifier.padding(horizontal = gutter, vertical = BuddySpacing.xs)) {
                            SessionsEmptyState(state = state, actions = actions)
                        }
                    }

                    state.filteredCount == 0 -> item(key = "no-match") {
                        Box(Modifier.padding(horizontal = gutter, vertical = BuddySpacing.xs)) {
                            SessionsNoMatchState(state = state, actions = actions)
                        }
                    }

                    else -> state.groups.forEach { group ->
                        item(key = "group-${group.key}") {
                            SessionsGroupHeader(
                                group = group,
                                onToggle = { actions.onToggleGroup(group.key) },
                                modifier = Modifier
                                    .padding(horizontal = gutter - BuddySpacing.sm)
                                    .padding(top = BuddySpacing.sm),
                            )
                        }
                        items(
                            items = group.rows,
                            key = { "${it.key.serverId}/${it.key.threadId}" },
                        ) { row ->
                            SessionRow(
                                row = row,
                                actions = SessionRowActions(
                                    onOpen = { actions.onOpen(row.key) },
                                    onToggleNode = { actions.onToggleNode(row.key) },
                                    onFork = { actions.onFork(row.key) },
                                    onRename = { actions.onRename(row.key) },
                                    onArchive = { actions.onArchive(row.key) },
                                ),
                                modifier = Modifier.padding(horizontal = gutter - BuddySpacing.sm, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Collapsible project header: name + task count, then 「主机 · 路径」. */
@Composable
private fun SessionsGroupHeader(
    group: SessionsGroupUi,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BuddyShapes.detailCard)
            .clickable(onClickLabel = if (group.isCollapsed) "展开" else "折叠", onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = if (group.isCollapsed) "已折叠" else "已展开"
            }
            .heightIn(min = BuddySize.minHitTarget)
            .padding(start = BuddySpacing.sm, top = BuddySpacing.xs, bottom = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.title,
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "%02d".format(group.taskCount),
                    style = buddyTextStyle(BuddyTextStyle.CAPTION),
                    color = AgentBuddyTheme.textSecondary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.hostName,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 1,
                )
                Text(
                    text = "·",
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    text = group.path,
                    style = buddyTextStyle(BuddyTextStyle.CODE),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Box(Modifier.size(BuddySize.minHitTarget), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (group.isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = AgentBuddyTheme.textSecondary,
                modifier = Modifier.size(BuddySize.icon),
            )
        }
    }
}

@Composable
private fun SessionsEmptyState(state: SessionsViewState, actions: SessionsCallbacks) {
    when {
        state.isLoading -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AgentBuddyTheme.textSecondary,
            )
            Text(
                text = "正在加载任务…",
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
            )
        }

        state.connectedHostCount == 0 -> BuddyEmptyState(
            icon = Icons.Outlined.Laptop,
            title = "没有已连接的主机",
            message = "在 Mac 上打开搭子并扫描配对二维码。之后任务在那台电脑上运行，你在这里随时跟进。",
            actionTitle = actions.onConnectHost?.let { "连接一台电脑" },
            actionIcon = Icons.Outlined.Laptop,
            actionKind = BuddyButtonKind.SECONDARY,
            onAction = actions.onConnectHost,
        )

        else -> BuddyEmptyState(
            icon = Icons.Outlined.AutoAwesome,
            title = "还没有任务",
            message = "点「新建任务」，在已连接的主机上开始一个任务。任务会按项目显示在这里。",
        )
    }
    Spacer(Modifier.height(BuddySpacing.xs))
}

@Composable
private fun SessionsNoMatchState(state: SessionsViewState, actions: SessionsCallbacks) {
    val query = state.searchQuery.trim()
    BuddyEmptyState(
        icon = Icons.Outlined.Search,
        title = "没有匹配的任务",
        message = if (query.isEmpty()) "没有符合这些筛选条件的任务。" else "没有与「$query」匹配的任务，换个词试试。",
        actionTitle = "清除搜索和筛选",
        actionIcon = Icons.Outlined.Cancel,
        actionKind = BuddyButtonKind.SECONDARY,
        onAction = {
            actions.onSearchQueryChange("")
            actions.onClearFilters()
        },
    )
}

internal fun visibleSessionRows(
    nodes: List<SessionTreeNode>,
    collapsedSessionNodeKeys: Set<ThreadKey>,
): List<SessionTreeNode> {
    val result = mutableListOf<SessionTreeNode>()
    fun walk(node: SessionTreeNode) {
        result.add(node)
        if (node.summary.key !in collapsedSessionNodeKeys) {
            node.children.forEach { walk(it) }
        }
    }
    nodes.forEach { walk(it) }
    return result
}
