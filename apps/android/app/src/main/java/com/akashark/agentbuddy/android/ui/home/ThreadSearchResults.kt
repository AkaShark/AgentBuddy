package com.akashark.agentbuddy.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentIconView
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.common.runtimeSortIndex
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey

/**
 * Every thread across connected hosts, newest first, filtered by the query
 * and the partner pills. Tapping a row toggles whether it is pinned to the
 * home list; forks of one thread collapse into a cluster row. Pull down to
 * re-check the hosts (force repair).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadSearchResults(
    sessions: List<AppSessionSummary>,
    pinnedKeys: Set<PinnedThreadKey>,
    query: String,
    runtimeKinds: List<AgentRuntimeKind>,
    selectedRuntimeKind: AgentRuntimeKind?,
    isRefreshing: Boolean,
    onRuntimeSelected: (AgentRuntimeKind?) -> Unit,
    onRefresh: () -> Unit,
    onPin: (AppSessionSummary) -> Unit,
    onUnpin: (AppSessionSummary) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = run {
        val needle = query.trim().lowercase()
        sessions.filter { session ->
            (selectedRuntimeKind == null || session.agentRuntimeKind == selectedRuntimeKind) &&
                (needle.isEmpty() ||
                    session.displayTitle.lowercase().contains(needle) ||
                    session.cwd.lowercase().contains(needle) ||
                    session.serverDisplayName.lowercase().contains(needle) ||
                    session.preview.lowercase().contains(needle))
        }
    }

    // Lineage over the *unfiltered* sessions, so a fork's parent (possibly
    // dropped by the filter) still anchors its cluster. Mirrors iOS clusters.
    val lineageMap = remember(sessions) { HomeDashboardSupport.computeLineageMap(sessions) }
    val clusters = remember(filtered, lineageMap) {
        val bucket = LinkedHashMap<ThreadKey, MutableList<AppSessionSummary>>()
        for (session in filtered) {
            val root = lineageMap[session.key]?.rootKey ?: session.key
            bucket.getOrPut(root) { mutableListOf() }.add(session)
        }
        bucket.map { (rootKey, members) ->
            ThreadSearchCluster(rootKey = rootKey, members = members.sortedByDescending { it.updatedAt ?: 0L })
        }
    }
    var expandedClusters by remember { mutableStateOf<Set<ThreadKey>>(emptySet()) }
    val hasFilter = query.isNotBlank() || selectedRuntimeKind != null

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BuddySpacing.xl)) {
            if (runtimeKinds.size > 1) {
                item(key = "runtime-filters") {
                    RuntimeFilterRow(
                        runtimeKinds = runtimeKinds.sortedBy { it.runtimeSortIndex },
                        selectedRuntimeKind = selectedRuntimeKind,
                        onRuntimeSelected = onRuntimeSelected,
                    )
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    if (sessions.isEmpty() || !hasFilter) {
                        BuddyEmptyState(
                            icon = Icons.Outlined.ViewAgenda,
                            title = "暂无任务",
                            message = "已连接主机上的任务会显示在这里。下拉可重新检查。",
                            actionTitle = "重新检查",
                            actionIcon = Icons.Outlined.Refresh,
                            onAction = onRefresh,
                            modifier = Modifier.padding(top = BuddySpacing.md),
                        )
                    } else {
                        BuddyEmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "没有匹配的任务",
                            message = "换个关键词，或清除搜索查看全部任务。",
                            actionTitle = "清除搜索",
                            onAction = onClearSearch,
                            modifier = Modifier.padding(top = BuddySpacing.md),
                        )
                    }
                }
            } else {
                items(clusters, key = { "${it.rootKey.serverId}/${it.rootKey.threadId}" }) { cluster ->
                    Column {
                        if (cluster != clusters.first()) BuddyDivider()
                        if (cluster.members.size == 1) {
                            val only = cluster.members.first()
                            val isPinned = pinnedKeys.contains(only.pinKey)
                            ThreadSearchRow(
                                session = only,
                                isPinned = isPinned,
                                onToggle = { if (isPinned) onUnpin(only) else onPin(only) },
                            )
                        } else {
                            ThreadSearchClusterRow(
                                cluster = cluster,
                                pinnedKeys = pinnedKeys,
                                isExpanded = cluster.rootKey in expandedClusters,
                                onToggleExpanded = {
                                    expandedClusters =
                                        if (cluster.rootKey in expandedClusters) expandedClusters - cluster.rootKey
                                        else expandedClusters + cluster.rootKey
                                },
                                onPin = onPin,
                                onUnpin = onUnpin,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val AppSessionSummary.pinKey: PinnedThreadKey
    get() = PinnedThreadKey(serverId = key.serverId, threadId = key.threadId)

@Composable
private fun RuntimeFilterRow(
    runtimeKinds: List<AgentRuntimeKind>,
    selectedRuntimeKind: AgentRuntimeKind?,
    onRuntimeSelected: (AgentRuntimeKind?) -> Unit,
) {
    BuddyChromeTypeLimit {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            RuntimeFilterPill(label = "全部", kind = null, isActive = selectedRuntimeKind == null) { onRuntimeSelected(null) }
            runtimeKinds.forEach { kind ->
                RuntimeFilterPill(label = kind.runtimeLabel, kind = kind, isActive = selectedRuntimeKind == kind) {
                    onRuntimeSelected(kind)
                }
            }
        }
    }
}

@Composable
private fun RuntimeFilterPill(
    label: String,
    kind: AgentRuntimeKind?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = BuddySize.minHitTarget)
                .clip(CircleShape)
                .clickable(role = Role.Tab, onClick = onClick)
                .semantics { selected = isActive },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = BuddySize.compactPill)
                    .background(if (isActive) AgentBuddyTheme.brand else AgentBuddyTheme.surfaceSoft, CircleShape)
                    .padding(horizontal = BuddySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (kind != null) AgentIconView(kind = kind, sizeDp = 16)
            Text(
                text = label,
                style = buddyTextStyle(BuddyTextStyle.LABEL, if (isActive) FontWeight.SemiBold else FontWeight.Normal),
                color = if (isActive) AgentBuddyTheme.onBrand else AgentBuddyTheme.textPrimary,
                maxLines = 1,
            )
        }
    }
}

private fun searchSubtitle(host: String, cwd: String, updatedAt: Long?): String =
    buildList {
        add(host)
        add(HomeDashboardSupport.workspaceLabel(cwd))
        updatedAt?.takeIf { it > 0 }?.let { add(HomeTaskPresentation.relativeTime(it * 1000)) }
    }.joinToString(" · ")

@Composable
private fun ThreadSearchRow(
    session: AppSessionSummary,
    isPinned: Boolean,
    onToggle: () -> Unit,
    indent: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (isPinned) "取消固定" else "固定到首页", onClick = onToggle)
                .semantics { stateDescription = if (isPinned) "已固定到首页" else "未固定" }
                .heightIn(min = BuddySize.listRow)
                .padding(start = if (indent) BuddySpacing.xl else 0.dp, top = BuddySpacing.sm, bottom = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuntimeTile(session.agentRuntimeKind)
        SearchRowText(
            title = session.displayTitle,
            subtitle = searchSubtitle(session.serverDisplayName, session.cwd, session.updatedAt),
            modifier = Modifier.weight(1f),
        )
        PinGlyph(isPinned)
    }
}

@Composable
private fun RuntimeTile(kind: AgentRuntimeKind) {
    Box(
        modifier = Modifier.size(BuddySize.rowTile).background(AgentBuddyTheme.surfaceSoft, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AgentIconView(kind = kind, sizeDp = 22)
    }
}

@Composable
private fun SearchRowText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.BODY, FontWeight.Medium),
            color = AgentBuddyTheme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PinGlyph(isPinned: Boolean) {
    Icon(
        imageVector = if (isPinned) Icons.Outlined.CheckCircle else Icons.Outlined.Add,
        contentDescription = null,
        tint = if (isPinned) AgentBuddyTheme.link else AgentBuddyTheme.textSecondary,
        modifier = Modifier.size(BuddySize.iconLarge),
    )
}

/**
 * One lineage's worth of search rows. Mirrors iOS `ThreadSearchCluster`.
 * `members` is sorted by `updatedAt` desc, so `members.first()` is the head
 * (most recently active branch).
 */
data class ThreadSearchCluster(
    val rootKey: ThreadKey,
    val members: List<AppSessionSummary>,
)

/**
 * Cluster row that collapses sibling threads into one unit. The branches
 * pill expands the children inline; each child has its own pin toggle.
 */
@Composable
private fun ThreadSearchClusterRow(
    cluster: ThreadSearchCluster,
    pinnedKeys: Set<PinnedThreadKey>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPin: (AppSessionSummary) -> Unit,
    onUnpin: (AppSessionSummary) -> Unit,
) {
    // Prefer the root for the head row identity (forks come and go); fall
    // back to the most recent member when the root is not loaded.
    val head = cluster.members.firstOrNull { it.key == cluster.rootKey } ?: cluster.members.first()
    val headLatestUpdatedAt = cluster.members.maxOf { it.updatedAt ?: 0L }
    val headPinned = pinnedKeys.contains(head.pinKey)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = BuddySize.listRow).padding(vertical = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuntimeTile(head.agentRuntimeKind)
            SearchRowText(
                title = head.displayTitle,
                subtitle = searchSubtitle(head.serverDisplayName, head.cwd, headLatestUpdatedAt),
                modifier = Modifier.weight(1f),
            )
            BuddyChromeTypeLimit {
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = BuddySize.minHitTarget)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = if (isExpanded) "收起分叉" else "展开分叉", onClick = onToggleExpanded),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .heightIn(min = BuddySize.compactPill)
                                .background(AgentBuddyTheme.surfaceSoft, CircleShape)
                                .padding(horizontal = BuddySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("${cluster.members.size} 个分支", style = buddyTextStyle(BuddyTextStyle.LABEL), color = AgentBuddyTheme.textPrimary)
                        Icon(
                            imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AgentBuddyTheme.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            BuddyIconButton(
                icon = if (headPinned) Icons.Outlined.CheckCircle else Icons.Outlined.Add,
                contentDescription = if (headPinned) "取消固定" else "固定到首页",
                onClick = { if (headPinned) onUnpin(head) else onPin(head) },
                tint = if (headPinned) AgentBuddyTheme.link else AgentBuddyTheme.textSecondary,
                iconSize = BuddySize.iconLarge,
            )
        }
        val reduceMotion = buddyReduceMotion
        AnimatedVisibility(
            visible = isExpanded,
            enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandVertically(),
            exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkVertically(),
        ) {
            Column {
                cluster.members.forEach { member ->
                    val isPinned = pinnedKeys.contains(member.pinKey)
                    ThreadSearchRow(
                        session = member,
                        isPinned = isPinned,
                        onToggle = { if (isPinned) onUnpin(member) else onPin(member) },
                        indent = true,
                    )
                }
            }
        }
    }
}
