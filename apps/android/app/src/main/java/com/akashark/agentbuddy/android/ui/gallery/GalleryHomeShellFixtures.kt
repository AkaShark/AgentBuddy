package com.akashark.agentbuddy.android.ui.gallery

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostSummary
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostsHomeUiState
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectSummary
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectsHomeUiState
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeHostChoice
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskSections
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeUiState
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHostAvailability
import uniffi.codex_mobile_client.AppConversationStats
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.AppThreadGoal
import uniffi.codex_mobile_client.AppThreadGoalStatus
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.ApprovalKind

/** Fixture data for the home shell gallery pages (no AppModel, no network). */
object GalleryHomeShellFixtures {
    private val nowSeconds: Long get() = System.currentTimeMillis() / 1000
    private val nowMs: Long get() = System.currentTimeMillis()

    val hosts = listOf(
        HomeHostChoice(serverId = "mbp", name = "MacBook Pro", isConnected = true, isConnecting = false),
        HomeHostChoice(serverId = "mini", name = "Mac mini", isConnected = true, isConnecting = false),
    )

    fun session(
        threadId: String,
        title: String,
        cwd: String = "/Users/me/Projects/AgentBuddy",
        server: String = "mbp",
        runtime: String = "codex",
        active: Boolean = false,
        minutesAgo: Long = 1,
        endedMinutesAgo: Long? = null,
        step: String? = null,
        filesChanged: UInt = 0u,
        commands: UInt = 0u,
        goal: String? = null,
    ) = AppSessionSummary(
        key = ThreadKey(serverId = server, threadId = threadId),
        agentRuntimeKind = runtime,
        serverDisplayName = if (server == "mbp") "MacBook Pro" else "Mac mini",
        serverHost = "$server.local",
        title = title,
        preview = "",
        cwd = cwd,
        model = "gpt-5.5-codex",
        modelProvider = "openai",
        parentThreadId = null,
        forkedFromId = null,
        agentNickname = null,
        agentRole = null,
        agentDisplayLabel = null,
        agentStatus = AppSubagentStatus.UNKNOWN,
        updatedAt = nowSeconds - minutesAgo * 60,
        hasActiveTurn = active,
        isResumed = true,
        isSubagent = false,
        isFork = false,
        lastResponsePreview = step,
        lastResponseTurnId = null,
        lastUserMessage = null,
        lastToolLabel = null,
        recentToolLog = emptyList(),
        lastTurnStartMs = nowMs - (minutesAgo + 2) * 60_000,
        lastTurnEndMs = endedMinutesAgo?.let { nowMs - it * 60_000 },
        stats = stats(filesChanged, commands),
        tokenUsage = null,
        goal = goal?.let {
            AppThreadGoal(
                threadId = threadId,
                objective = it,
                status = AppThreadGoalStatus.ACTIVE,
                tokenBudget = null,
                tokensUsed = 0,
                timeUsedSeconds = 0,
                createdAt = 0,
                updatedAt = 0,
            )
        },
    )

    private fun stats(files: UInt, commands: UInt) = AppConversationStats(
        totalMessages = 6u,
        userMessageCount = 2u,
        assistantMessageCount = 4u,
        turnCount = 2u,
        commandsExecuted = commands,
        commandsSucceeded = commands,
        commandsFailed = 0u,
        totalCommandDurationMs = 0,
        filesChanged = files,
        filesAdded = 0u,
        filesModified = files,
        filesDeleted = 0u,
        diffAdditions = files * 12u,
        diffDeletions = files * 3u,
        toolCallCount = commands + files,
        mcpToolCallCount = 0u,
        dynamicToolCallCount = 0u,
        webSearchCount = 0u,
        imageCount = 0u,
        codeReviewCount = 0u,
        widgetCount = 0u,
        sessionDurationMs = null,
    )

    val sessions = listOf(
        session("t-login", "让登录体验更流畅", active = true, minutesAgo = 0, step = "搭子正在检查登录状态与页面切换。", filesChanged = 3u, goal = "登录流程两步内完成"),
        session("t-test", "运行登录流程测试", active = true, minutesAgo = 4, step = "等待你确认运行 npm test。", runtime = "claude"),
        session("t-home", "整理首页组件", minutesAgo = 18, endedMinutesAgo = 18),
        session("t-site", "更新官网首页文案", cwd = "/Users/me/Projects/Website", server = "mini", minutesAgo = 60 * 26, endedMinutesAgo = 60 * 26),
    )

    private val approvals = listOf(
        PendingApproval(
            id = "a1",
            serverId = "mbp",
            kind = ApprovalKind.COMMAND,
            threadId = "t-test",
            turnId = null,
            itemId = null,
            command = "npm test",
            path = null,
            grantRoot = null,
            cwd = null,
            reason = null,
        ),
    )

    fun tasksState(showsDetail: Boolean = false): TasksHomeUiState {
        val items = HomeTaskPresentation.items(
            sessions = sessions,
            pendingApprovals = approvals,
            pendingInputs = emptyList(),
            pinnedKeys = listOf(PinnedThreadKey(serverId = "mbp", threadId = "t-login")),
            cancellingIds = emptySet(),
        )
        return TasksHomeUiState(
            hosts = hosts,
            selectedServerId = null,
            sections = HomeTaskPresentation.sections(items),
            availability = TasksHostAvailability.ONLINE,
            showsDetail = showsDetail,
        )
    }

    fun emptyTasksState(availability: TasksHostAvailability) = TasksHomeUiState(
        hosts = if (availability == TasksHostAvailability.NO_HOSTS) emptyList() else hosts,
        selectedServerId = null,
        sections = HomeTaskSections(emptyList(), emptyList(), emptyList()),
        availability = availability,
    )

    private fun project(name: String, server: String, lastUsedHours: Long) = AppProject(
        id = "$server::/Users/me/Projects/$name",
        serverId = server,
        cwd = "/Users/me/Projects/$name",
        lastUsedAtMs = nowMs - lastUsedHours * 3_600_000,
    )

    private fun summary(name: String, server: String, host: String, tasks: Int, running: Int, lastUsedHours: Long): ProjectSummary {
        val p = project(name, server, lastUsedHours)
        return ProjectSummary(
            project = p,
            name = name,
            hostName = host,
            displayPath = "~/Projects/$name",
            taskCount = tasks,
            runningCount = running,
            lastUsedMs = p.lastUsedAtMs,
        )
    }

    val projectsState = ProjectsHomeUiState(
        hero = summary("AgentBuddy", "mbp", "MacBook Pro", 5, 2, 0),
        others = listOf(
            summary("Personal", "mbp", "MacBook Pro", 3, 0, 30),
            summary("Website", "mini", "Mac mini", 8, 0, 50),
        ),
        hasHosts = true,
    )

    val hostsState = HostsHomeUiState(
        primary = HostSummary(
            serverId = "mbp",
            name = "MacBook Pro",
            isLocal = false,
            connection = BuddyConnectionState.CONNECTED,
            sourceTitle = "点对点连接",
            partners = listOf("Codex", "Claude Code"),
            runningCount = 2,
            hasTerminal = true,
        ),
        others = listOf(
            HostSummary(
                serverId = "mini",
                name = "Mac mini",
                isLocal = false,
                connection = BuddyConnectionState.CONNECTED,
                sourceTitle = "SSH",
                partners = listOf("Codex"),
                runningCount = 0,
                hasTerminal = false,
            ),
            HostSummary(
                serverId = "studio",
                name = "工作室 iMac",
                isLocal = false,
                connection = BuddyConnectionState.DISCONNECTED,
                sourceTitle = "局域网",
                partners = emptyList(),
                runningCount = 0,
                hasTerminal = false,
            ),
        ),
    )
}
