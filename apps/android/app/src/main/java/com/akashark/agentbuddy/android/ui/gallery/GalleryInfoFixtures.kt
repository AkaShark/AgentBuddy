package com.akashark.agentbuddy.android.ui.gallery

import com.akashark.agentbuddy.android.ui.common.ModelPanelState
import com.akashark.agentbuddy.android.ui.conversation.TaskContextUsage
import com.akashark.agentbuddy.android.ui.conversation.TaskInfoHero
import com.akashark.agentbuddy.android.ui.conversation.TaskInfoServer
import com.akashark.agentbuddy.android.ui.conversation.TaskInfoState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import uniffi.codex_mobile_client.AppActivityByDayEntry
import uniffi.codex_mobile_client.AppConversationStats
import uniffi.codex_mobile_client.AppModelUsageEntry
import uniffi.codex_mobile_client.AppServerUsageStats
import uniffi.codex_mobile_client.AppTokensByThreadEntry
import uniffi.codex_mobile_client.InputModality
import uniffi.codex_mobile_client.ModelInfo
import uniffi.codex_mobile_client.RateLimitSnapshot
import uniffi.codex_mobile_client.RateLimitWindow
import uniffi.codex_mobile_client.ReasoningEffort
import uniffi.codex_mobile_client.ReasoningEffortOption

/** Fixture data for the task info and model panel gallery pages. */
internal object GalleryInfoFixtures {
    private val nowSeconds: Long
        get() = System.currentTimeMillis() / 1000

    private val stats =
        AppConversationStats(
            totalMessages = 42u,
            userMessageCount = 9u,
            assistantMessageCount = 33u,
            turnCount = 9u,
            commandsExecuted = 27u,
            commandsSucceeded = 25u,
            commandsFailed = 2u,
            totalCommandDurationMs = 84_300,
            filesChanged = 6u,
            filesAdded = 1u,
            filesModified = 5u,
            filesDeleted = 0u,
            diffAdditions = 214u,
            diffDeletions = 57u,
            toolCallCount = 31u,
            mcpToolCallCount = 4u,
            dynamicToolCallCount = 0u,
            webSearchCount = 1u,
            imageCount = 2u,
            codeReviewCount = 0u,
            widgetCount = 0u,
            sessionDurationMs = null,
        )

    private fun usage(): AppServerUsageStats {
        val day = 86_400L
        val today = nowSeconds / day * day
        val turns = listOf(3, 5, 2, 0, 7, 9, 4, 6, 1, 8)
        return AppServerUsageStats(
            totalThreads = 12u,
            activeThreads = 2u,
            totalTokens = 1_830_000uL,
            tokensByThread =
                listOf(
                    AppTokensByThreadEntry(threadTitle = "让登录体验更流畅", threadId = "t1", tokens = 812_000uL),
                    AppTokensByThreadEntry(threadTitle = "修复 Android 通知跳转", threadId = "t2", tokens = 403_500uL),
                    AppTokensByThreadEntry(threadTitle = "整理发布说明", threadId = "t3", tokens = 96_200uL),
                ),
            activityByDay =
                turns.mapIndexed { index, count ->
                    AppActivityByDayEntry(dateEpoch = today - (turns.size - 1 - index) * day, turnCount = count.toUInt())
                },
            modelUsage =
                listOf(
                    AppModelUsageEntry(model = "gpt-5.2-codex", threadCount = 8u),
                    AppModelUsageEntry(model = "claude-sonnet-4.5", threadCount = 3u),
                    AppModelUsageEntry(model = "smart", threadCount = 1u),
                ),
        )
    }

    private val server =
        TaskInfoServer(
            name = "MacBook Pro",
            address = "192.168.1.20:8390",
            mode = "远程",
            connection = BuddyConnectionState.CONNECTED,
            connectionTitle = "已连接",
            accountEmail = "dev@example.com",
            planLabel = "Pro",
            usesApiKey = false,
            models = listOf("GPT-5.2 Codex", "GPT-5.2", "GPT-5 mini"),
        )

    fun task(): TaskInfoState =
        TaskInfoState(
            isServerOnly = false,
            hero =
                TaskInfoHero(
                    title = "让登录体验更流畅",
                    status = BuddyTaskState.RUNNING,
                    statusTitle = BuddyTaskState.RUNNING.title,
                    partnerAndHost = "Codex · MacBook Pro",
                    model = "gpt-5.2-codex",
                    effort = "高",
                    cwdDisplay = "~/Projects/AgentBuddy",
                    cwdFull = "/Users/dev/Projects/AgentBuddy",
                    threadId = "019a3c1e-7b2d-7f10-9c4e-5d8a2b61f0aa",
                    createdAt = nowSeconds - 3 * 86_400,
                    updatedAt = nowSeconds - 12 * 60,
                ),
            context = TaskContextUsage(usedTokens = 128_400, windowTokens = 200_000),
            stats = stats,
            usage = usage(),
            rateLimits =
                RateLimitSnapshot(
                    primary = RateLimitWindow(usedPercent = 42),
                    secondary = RateLimitWindow(usedPercent = 86),
                ),
            server = server,
            canOpenShell = true,
        )

    fun serverOnly(): TaskInfoState =
        task().copy(isServerOnly = true, hero = null, context = null, stats = null)

    private fun efforts(vararg values: ReasoningEffort) =
        values.map { ReasoningEffortOption(reasoningEffort = it, description = "") }

    private fun model(
        id: String,
        name: String,
        description: String,
        runtime: String,
        isDefault: Boolean = false,
        efforts: List<ReasoningEffortOption> = efforts(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
    ) = ModelInfo(
        id = id,
        model = id,
        displayName = name,
        description = description,
        hidden = false,
        supportedReasoningEfforts = efforts,
        defaultReasoningEffort = ReasoningEffort.MEDIUM,
        inputModalities = listOf(InputModality.TEXT, InputModality.IMAGE),
        isDefault = isDefault,
        agentRuntimeKind = runtime,
    )

    val models: List<ModelInfo> =
        listOf(
            model("gpt-5.2-codex", "GPT-5.2 Codex", "为编码任务优化的最新模型", "codex", isDefault = true),
            model("gpt-5.2", "GPT-5.2", "通用推理，适合规划和写作", "codex"),
            model("gpt-5-mini", "GPT-5 mini", "更快、更省", "codex", efforts = efforts(ReasoningEffort.LOW, ReasoningEffort.MEDIUM)),
            model("claude-sonnet-4.5", "Claude Sonnet 4.5", "", "claude"),
            model("smart", "smart", "", "amp", efforts = emptyList()),
        )

    val modelPanel =
        ModelPanelState(
            models = models,
            selectedModel = "gpt-5.2-codex",
            selectedRuntime = "codex",
            efforts = models.first().supportedReasoningEfforts,
            selectedEffort = "high",
            effortLocked = false,
            planMode = false,
            fullAccess = false,
            fastMode = false,
        )

    /** Amp after the first message: effort locked, permissions owned by the partner. */
    val lockedModelPanel =
        modelPanel.copy(
            selectedModel = "smart",
            selectedRuntime = "amp",
            efforts = emptyList(),
            selectedEffort = null,
            effortLocked = true,
            planMode = null,
            fullAccess = null,
        )
}
