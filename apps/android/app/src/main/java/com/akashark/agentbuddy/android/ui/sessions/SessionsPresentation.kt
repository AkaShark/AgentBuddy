package com.akashark.agentbuddy.android.ui.sessions

import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.ThreadKey

/** One task row of 全部任务, already shaped for rendering. */
internal data class SessionRowUi(
    val key: ThreadKey,
    val title: String,
    /** 「状态 · 项目 · 主机 · 更新时间 · 模型」. */
    val subtitle: String,
    /** 「来自 …」 for a sub-agent's parent, 「分叉自 …」 for a fork source. */
    val relationLine: String?,
    val state: BuddyTaskState,
    val isActive: Boolean,
    val isFork: Boolean,
    /** Sub-agent label when the row is a sub-agent. */
    val subagentLabel: String?,
    val depth: Int,
    val hasChildren: Boolean,
    val isCollapsed: Boolean,
)

/** A project (host + folder) section of 全部任务. */
internal data class SessionsGroupUi(
    val key: String,
    val title: String,
    val hostName: String,
    val path: String,
    val taskCount: Int,
    val isCollapsed: Boolean,
    val rows: List<SessionRowUi>,
)

internal data class SessionsServerOption(val id: String, val name: String)

/** Everything the stateless 全部任务 content renders. */
internal data class SessionsViewState(
    val title: String,
    val totalCount: Int,
    val filteredCount: Int,
    val connectedHostCount: Int,
    val groups: List<SessionsGroupUi>,
    val isLoading: Boolean,
    val hasLoadedInitialSessions: Boolean,
    val searchQuery: String,
    val serverOptions: List<SessionsServerOption>,
    val serverFilterId: String?,
    val showOnlyForks: Boolean,
    val sortMode: WorkspaceSortMode,
    /** Null when no task is open; otherwise whether 分叉当前任务 is enabled. */
    val canForkCurrent: Boolean?,
    val isForkingCurrent: Boolean,
    val showsInfo: Boolean,
    val canCreateTask: Boolean,
) {
    val hasActiveFilters: Boolean get() = serverFilterId != null || showOnlyForks
}

/** Render-only projection of a summary to the Mint task state (mirrors iOS rows). */
internal fun sessionTaskState(summary: AppSessionSummary): BuddyTaskState = when {
    summary.hasActiveTurn -> BuddyTaskState.RUNNING
    summary.isSubagent -> when (summary.agentStatus) {
        AppSubagentStatus.COMPLETED -> BuddyTaskState.COMPLETED
        AppSubagentStatus.ERRORED -> BuddyTaskState.FAILED
        AppSubagentStatus.SHUTDOWN, AppSubagentStatus.INTERRUPTED -> BuddyTaskState.INTERRUPTED
        AppSubagentStatus.PENDING_INIT, AppSubagentStatus.RUNNING, AppSubagentStatus.UNKNOWN -> BuddyTaskState.IDLE
    }
    summary.lastTurnEndMs == null -> BuddyTaskState.IDLE
    else -> BuddyTaskState.COMPLETED
}

/** The stored "This Device" sentinel is shown as 本设备; never translated in storage. */
internal fun sessionsHostLabel(name: String): String = if (name == "This Device") "本设备" else name

/** 「刚刚」「18 分钟前」「3 小时前」「昨天」「4 天前」「2 周前」 from epoch seconds. */
internal fun sessionsRelativeTime(epochSeconds: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
    if (epochSeconds == null || epochSeconds <= 0L) return null
    val seconds = (nowMillis / 1000L - epochSeconds).coerceAtLeast(0L)
    return when {
        seconds < 60L -> "刚刚"
        seconds < 3_600L -> "${seconds / 60L} 分钟前"
        seconds < 86_400L -> "${seconds / 3_600L} 小时前"
        seconds < 2 * 86_400L -> "昨天"
        seconds < 7 * 86_400L -> "${seconds / 86_400L} 天前"
        else -> "${seconds / (7 * 86_400L)} 周前"
    }
}

/**
 * Shapes the derived session tree into rows and sections. [pathLabel] turns a
 * group's cwd into its display path (e.g. `~/…` on this device).
 */
internal fun buildSessionsGroups(
    derived: SessionsDerivedData,
    allSummaries: List<AppSessionSummary>,
    activeKey: ThreadKey?,
    collapsedGroupKeys: Set<String>,
    collapsedNodeKeys: Set<ThreadKey>,
    pathLabel: (serverId: String, cwd: String) -> String,
    nowMillis: Long = System.currentTimeMillis(),
): List<SessionsGroupUi> {
    val byThread = allSummaries.associateBy { it.key.serverId to it.key.threadId }
    return derived.groups.map { group ->
        val key = SessionsDerivation.workspaceGroupKey(group.serverId, group.cwd)
        val rows = visibleSessionRows(group.nodes, collapsedNodeKeys).map { node ->
            val summary = node.summary
            val parent = derived.parentByKey[summary.key]
            val forkSource = summary.forkedFromId?.let { byThread[summary.key.serverId to it] }
            SessionRowUi(
                key = summary.key,
                title = summary.displayTitle,
                subtitle = sessionRowSubtitle(summary, group.workspaceLabel, nowMillis),
                relationLine = when {
                    parent != null -> "来自 ${parent.displayTitle}"
                    forkSource != null -> "分叉自 ${forkSource.displayTitle}"
                    else -> null
                },
                state = sessionTaskState(summary),
                isActive = summary.key == activeKey,
                isFork = summary.isFork,
                subagentLabel = if (summary.isSubagent) summary.agentDisplayLabel ?: "子代理" else null,
                depth = node.depth,
                hasChildren = node.children.isNotEmpty(),
                isCollapsed = summary.key in collapsedNodeKeys,
            )
        }
        SessionsGroupUi(
            key = key,
            title = group.workspaceLabel,
            hostName = sessionsHostLabel(group.serverName),
            path = pathLabel(group.serverId, group.cwd),
            taskCount = countNodes(group.nodes),
            isCollapsed = key in collapsedGroupKeys,
            rows = if (key in collapsedGroupKeys) emptyList() else rows,
        )
    }
}

private fun sessionRowSubtitle(summary: AppSessionSummary, projectLabel: String, nowMillis: Long): String {
    val state = sessionTaskState(summary)
    return buildList {
        when (state) {
            BuddyTaskState.RUNNING -> add("进行中")
            BuddyTaskState.FAILED -> add("失败")
            BuddyTaskState.INTERRUPTED -> add("已停止")
            BuddyTaskState.COMPLETED -> if (summary.isSubagent) add("已完成")
            else -> Unit
        }
        add(projectLabel)
        add(sessionsHostLabel(summary.serverDisplayName))
        sessionsRelativeTime(summary.updatedAt, nowMillis)?.let { add("${it}更新") }
        summary.model.takeIf { it.isNotBlank() }?.let { add(it.substringAfterLast('/')) }
    }.joinToString(" · ")
}

private fun countNodes(nodes: List<SessionTreeNode>): Int =
    nodes.sumOf { 1 + countNodes(it.children) }
