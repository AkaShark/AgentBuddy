package com.akashark.agentbuddy.android.ui.homeshell.tasks

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.home.ThreadLineage
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.PendingUserInputRequest
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey

/**
 * Render-only projection of one home task. Built from Rust-owned snapshot data
 * (`AppSessionSummary` plus pending approvals / user inputs joined by thread
 * key); it never decides task state on its own.
 */
data class HomeTaskItem(
    val session: AppSessionSummary,
    val state: BuddyTaskState,
    val pendingApprovalCount: Int,
    val isPinned: Boolean,
    val isHydrating: Boolean = false,
    val lineage: ThreadLineage? = null,
) {
    val key: ThreadKey get() = session.key
    val id: String get() = HomeTaskPresentation.taskId(session.key)
}

/** The three task sections of the 任务 tab, in display order. */
data class HomeTaskSections(
    val attention: List<HomeTaskItem>,
    val active: List<HomeTaskItem>,
    val recent: List<HomeTaskItem>,
) {
    val isEmpty: Boolean get() = attention.isEmpty() && active.isEmpty() && recent.isEmpty()
}

object HomeTaskPresentation {
    /** "serverId/threadId": the key used for approvals, inputs and stop markers. */
    fun taskId(key: ThreadKey): String = "${key.serverId}/${key.threadId}"

    /**
     * Joins a session with the snapshot's request queues. Order matters: a stop
     * in flight wins over "running", a pending approval wins over a pending
     * input, both win over plain activity. Connection problems never map to a
     * failure: an unreachable host keeps the last known task state.
     */
    fun state(
        taskId: String,
        hasActiveTurn: Boolean,
        lastTurnEndMs: Long?,
        approvalIds: Set<String>,
        inputIds: Set<String>,
        cancellingIds: Set<String>,
    ): BuddyTaskState =
        when {
            hasActiveTurn && taskId in cancellingIds -> BuddyTaskState.STOPPING
            taskId in approvalIds -> BuddyTaskState.AWAITING_APPROVAL
            taskId in inputIds -> BuddyTaskState.AWAITING_INPUT
            hasActiveTurn -> BuddyTaskState.RUNNING
            lastTurnEndMs == null -> BuddyTaskState.IDLE
            else -> BuddyTaskState.COMPLETED
        }

    /** Pending approval counts per task id. MCP elicitations are not task approvals. */
    fun approvalCounts(approvals: List<PendingApproval>): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (approval in approvals) {
            if (approval.kind == ApprovalKind.MCP_ELICITATION) continue
            val threadId = approval.threadId?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val id = "${approval.serverId}/$threadId"
            counts[id] = (counts[id] ?: 0) + 1
        }
        return counts
    }

    fun inputIds(inputs: List<PendingUserInputRequest>): Set<String> =
        inputs.mapNotNullTo(LinkedHashSet()) { input ->
            input.threadId.trim().takeIf { it.isNotEmpty() }?.let { "${input.serverId}/$it" }
        }

    fun items(
        sessions: List<AppSessionSummary>,
        pendingApprovals: List<PendingApproval>,
        pendingInputs: List<PendingUserInputRequest>,
        pinnedKeys: List<PinnedThreadKey>,
        cancellingIds: Set<String>,
        hydratingIds: Set<String> = emptySet(),
        lineageByKey: Map<ThreadKey, ThreadLineage> = emptyMap(),
    ): List<HomeTaskItem> {
        val approvalCounts = approvalCounts(pendingApprovals)
        val approvalIds = approvalCounts.keys
        val inputIds = inputIds(pendingInputs)
        val pinned = pinnedKeys.toSet()
        return sessions.map { session ->
            val id = taskId(session.key)
            HomeTaskItem(
                session = session,
                state = state(
                    taskId = id,
                    hasActiveTurn = session.hasActiveTurn,
                    lastTurnEndMs = session.lastTurnEndMs,
                    approvalIds = approvalIds,
                    inputIds = inputIds,
                    cancellingIds = cancellingIds,
                ),
                pendingApprovalCount = approvalCounts[id] ?: 0,
                isPinned = PinnedThreadKey(serverId = session.key.serverId, threadId = session.key.threadId) in pinned,
                isHydrating = !session.isResumed && id in hydratingIds,
                lineage = lineageByKey[session.key]?.takeIf { it.hasMultipleBranches },
            )
        }
    }

    /**
     * 「需要你处理」 (approval / input), 「正在进行」 (running + stopping) and
     * 「接着上次」 (everything else), each keeping the incoming order.
     */
    fun sections(items: List<HomeTaskItem>): HomeTaskSections {
        val attention = mutableListOf<HomeTaskItem>()
        val active = mutableListOf<HomeTaskItem>()
        val recent = mutableListOf<HomeTaskItem>()
        for (item in items) {
            when {
                item.state.needsAttention -> attention += item
                item.state == BuddyTaskState.RUNNING || item.state == BuddyTaskState.STOPPING -> active += item
                else -> recent += item
            }
        }
        return HomeTaskSections(attention, active, recent)
    }

    /** Stop markers only live while their turn is still running. */
    fun pruneCancelling(cancellingIds: Set<String>, sessions: List<AppSessionSummary>): Set<String> {
        if (cancellingIds.isEmpty()) return cancellingIds
        val stillActive = sessions.filter { it.hasActiveTurn }.mapTo(HashSet()) { taskId(it.key) }
        return cancellingIds.intersect(stillActive)
    }

    /** Hero subtitle, e.g. 「1 个任务进行中，1 个等待你确认。」 */
    fun summary(running: Int, waiting: Int): String =
        when {
            running == 0 && waiting == 0 -> "当前没有进行中的任务。"
            waiting == 0 -> "$running 个任务进行中。"
            running == 0 -> "$waiting 个等待你确认。"
            else -> "$running 个任务进行中，$waiting 个等待你确认。"
        }

    fun title(displayTitle: String): String {
        val trimmed = displayTitle.trim()
        return if (trimmed.isEmpty() || trimmed == "未命名会话") "未命名任务" else trimmed
    }

    /** Last path component of the working directory, e.g. "AgentBuddy". */
    fun projectName(cwd: String): String? {
        val trimmed = cwd.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        return trimmed.substringAfterLast('/').ifEmpty { null }
    }

    /**
     * The most recent step the partner reported: tool, then reply, then
     * preview. Text that is already the card title is skipped.
     */
    fun latestStep(session: AppSessionSummary, shownTitle: String): String? =
        listOf(session.lastToolLabel, session.lastResponsePreview, session.preview)
            .mapNotNull { it?.trim()?.replace('\n', ' ') }
            .firstOrNull { it.isNotEmpty() && it != shownTitle.trim() }

    /** 「已修改 3 个文件 · 运行了 2 条命令」 from Rust stats. */
    fun activitySummary(session: AppSessionSummary): String? {
        val stats = session.stats ?: return null
        val parts = buildList {
            if (stats.filesChanged > 0u) add("已修改 ${stats.filesChanged} 个文件")
            if (stats.commandsExecuted > 0u) add("运行了 ${stats.commandsExecuted} 条命令")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Second line of a 「接着上次」 row: project and what the task is doing. */
    fun rowSubtitle(item: HomeTaskItem, nowMs: Long = System.currentTimeMillis()): String {
        val parts = mutableListOf<String>()
        projectName(item.session.cwd)?.let(parts::add)
        parts +=
            when (item.state) {
                BuddyTaskState.AWAITING_APPROVAL -> "等待你确认"
                BuddyTaskState.AWAITING_INPUT -> "等待你回复"
                BuddyTaskState.RUNNING -> "进行中"
                BuddyTaskState.STOPPING -> "正在停止…"
                BuddyTaskState.COMPLETED ->
                    item.session.lastTurnEndMs?.let { "${relativeTime(it, nowMs)}完成" } ?: "已完成"
                else -> item.session.updatedAt?.let { "${relativeTime(it * 1000, nowMs)}更新" } ?: "空闲"
            }
        return parts.joinToString(" · ")
    }

    /** Timestamp shown on task cards: when the task last changed. */
    fun cardTime(session: AppSessionSummary, nowMs: Long = System.currentTimeMillis()): String? =
        session.updatedAt?.takeIf { it > 0 }?.let { relativeTime(it * 1000, nowMs) }

    /** Localized 「18 分钟前」 style relative time from epoch milliseconds. */
    fun relativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val seconds = ((nowMs - epochMs) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> "刚刚"
            seconds < 3_600 -> "${seconds / 60} 分钟前"
            seconds < 86_400 -> "${seconds / 3_600} 小时前"
            seconds < 2 * 86_400 -> "昨天"
            seconds < 7 * 86_400 -> "${seconds / 86_400} 天前"
            seconds < 30 * 86_400 -> "${seconds / (7 * 86_400)} 周前"
            seconds < 365 * 86_400 -> "${seconds / (30 * 86_400)} 个月前"
            else -> "${seconds / (365 * 86_400)} 年前"
        }
    }
}
