package com.akashark.agentbuddy.android.ui.gallery

import com.akashark.agentbuddy.android.ui.sessions.SessionsDerivation
import com.akashark.agentbuddy.android.ui.sessions.SessionsServerOption
import com.akashark.agentbuddy.android.ui.sessions.SessionsViewState
import com.akashark.agentbuddy.android.ui.sessions.WorkspaceSortMode
import com.akashark.agentbuddy.android.ui.sessions.buildSessionsGroups
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.ThreadKey

/** Fixture tasks for the 全部任务 gallery pages (built with named arguments, no runtime). */
internal object GallerySessionsFixtures {
    private val nowSeconds: Long get() = System.currentTimeMillis() / 1000L

    val summaries: List<AppSessionSummary> by lazy {
        listOf(
            summary("t1", "mac", "MacBook Pro", "/Users/me/AgentBuddy", "让登录体验更流畅", minutesAgo = 2, running = true),
            summary("t2", "mac", "MacBook Pro", "/Users/me/AgentBuddy", "运行登录流程测试", minutesAgo = 18, ended = true),
            summary(
                "t3", "mac", "MacBook Pro", "/Users/me/AgentBuddy", "登录页文案对比实验",
                minutesAgo = 50, ended = true, forkedFrom = "t2", isFork = true,
            ),
            summary(
                "t4", "mac", "MacBook Pro", "/Users/me/AgentBuddy", "检查 OAuth 回调",
                minutesAgo = 12, parent = "t1", subagent = true, subagentLabel = "审查员",
                subagentStatus = AppSubagentStatus.COMPLETED,
            ),
            summary("t5", "mini", "Mac mini", "/Users/studio/Personal", "整理本周笔记", minutesAgo = 60 * 26, ended = true),
            summary("t6", "mini", "Mac mini", "/Users/studio/Personal", "给相册加标签", minutesAgo = 60 * 24 * 5),
        )
    }

    val servers = listOf(SessionsServerOption("mac", "MacBook Pro"), SessionsServerOption("mini", "Mac mini"))

    fun state(
        summaries: List<AppSessionSummary> = this.summaries,
        connectedHostCount: Int = 2,
        activeKey: ThreadKey? = ThreadKey(serverId = "mac", threadId = "t2"),
        showOnlyForks: Boolean = false,
    ): SessionsViewState {
        val derived = SessionsDerivation.derive(summaries = summaries, forkOnly = showOnlyForks)
        return SessionsViewState(
            title = "全部任务",
            totalCount = derived.totalCount,
            filteredCount = derived.filteredCount,
            connectedHostCount = connectedHostCount,
            groups = buildSessionsGroups(
                derived = derived,
                allSummaries = summaries,
                activeKey = activeKey,
                collapsedGroupKeys = emptySet(),
                collapsedNodeKeys = emptySet(),
                pathLabel = { _, cwd -> cwd.replace("/Users/me", "~").replace("/Users/studio", "~") },
                approvalIds = setOf("mac/t1"),
            ),
            isLoading = false,
            hasLoadedInitialSessions = true,
            searchQuery = "",
            serverOptions = if (connectedHostCount > 0) servers else emptyList(),
            serverFilterId = null,
            showOnlyForks = showOnlyForks,
            sortMode = WorkspaceSortMode.RECENT,
            canForkCurrent = activeKey?.let { true },
            isForkingCurrent = false,
            showsInfo = false,
            canCreateTask = true,
        )
    }

    private fun summary(
        id: String,
        serverId: String,
        serverName: String,
        cwd: String,
        title: String,
        minutesAgo: Long,
        running: Boolean = false,
        ended: Boolean = false,
        parent: String? = null,
        forkedFrom: String? = null,
        isFork: Boolean = false,
        subagent: Boolean = false,
        subagentLabel: String? = null,
        subagentStatus: AppSubagentStatus = AppSubagentStatus.UNKNOWN,
    ): AppSessionSummary {
        val updated = nowSeconds - minutesAgo * 60L
        return AppSessionSummary(
            key = ThreadKey(serverId = serverId, threadId = id),
            agentRuntimeKind = "codex",
            serverDisplayName = serverName,
            serverHost = "$serverName.local",
            title = title,
            preview = "",
            cwd = cwd,
            model = "openai/gpt-5.1-codex",
            modelProvider = "openai",
            parentThreadId = parent,
            forkedFromId = forkedFrom,
            agentNickname = null,
            agentRole = null,
            agentDisplayLabel = subagentLabel,
            agentStatus = subagentStatus,
            updatedAt = updated,
            hasActiveTurn = running,
            isResumed = false,
            isSubagent = subagent,
            isFork = isFork,
            lastResponsePreview = null,
            lastResponseTurnId = null,
            lastUserMessage = null,
            lastToolLabel = null,
            recentToolLog = emptyList(),
            lastTurnStartMs = if (running || ended) updated * 1000L else null,
            lastTurnEndMs = if (ended) updated * 1000L else null,
            stats = null,
            tokenUsage = null,
            goal = null,
        )
    }
}
