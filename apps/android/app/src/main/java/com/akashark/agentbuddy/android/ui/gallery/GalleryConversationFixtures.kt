package com.akashark.agentbuddy.android.ui.gallery

import com.akashark.agentbuddy.android.ui.conversation.ExplorationGroup
import com.akashark.agentbuddy.android.ui.conversation.SubagentRowDisplay
import com.akashark.agentbuddy.android.ui.conversation.TranscriptTurn
import uniffi.codex_mobile_client.AppMessagePhase
import uniffi.codex_mobile_client.AppMessageRenderBlock
import uniffi.codex_mobile_client.AppOperationStatus
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.HydratedAssistantMessageData
import uniffi.codex_mobile_client.HydratedCommandActionData
import uniffi.codex_mobile_client.HydratedCommandActionKind
import uniffi.codex_mobile_client.HydratedCommandExecutionData
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.HydratedDividerData
import uniffi.codex_mobile_client.HydratedErrorData
import uniffi.codex_mobile_client.HydratedFileChangeData
import uniffi.codex_mobile_client.HydratedFileChangeEntryData
import uniffi.codex_mobile_client.HydratedImageGenerationData
import uniffi.codex_mobile_client.HydratedMcpToolCallData
import uniffi.codex_mobile_client.HydratedMultiAgentActionData
import uniffi.codex_mobile_client.HydratedMultiAgentStateData
import uniffi.codex_mobile_client.HydratedPlanStep
import uniffi.codex_mobile_client.HydratedPlanStepStatus
import uniffi.codex_mobile_client.HydratedReasoningData
import uniffi.codex_mobile_client.HydratedTodoListData
import uniffi.codex_mobile_client.HydratedUserMessageData
import uniffi.codex_mobile_client.ThreadKey

/** Fixture transcript for the `conversation` and `conversation-long` gallery pages. */
internal object GalleryConversationFixtures {
    val userMessage = HydratedUserMessageData(
        text = "帮我检查登录流程，修复页面跳转时的闪烁。",
        imageDataUris = emptyList(),
    )

    val reasoning = HydratedReasoningData(
        summary = listOf("先确认登录状态恢复和首页导航的先后顺序。"),
        content = listOf("恢复会话是异步的；如果导航在恢复完成前触发，就会先进登录页再跳回首页。"),
    )

    val assistantBlocks: List<AppMessageRenderBlock> = listOf(
        AppMessageRenderBlock.Markdown(
            markdown = "我检查了登录状态和页面切换逻辑。**闪烁**来自状态恢复时的一次重复跳转：\n\n" +
                "- 首次渲染时 `isLoggedIn` 还是默认值\n" +
                "- 恢复完成后又触发了一次导航\n\n" +
                "> 现在恢复完成前会停在启动页。详见 [导航说明](https://example.com/nav)。",
        ),
        AppMessageRenderBlock.CodeBlock(
            language = "kotlin",
            code = "if (!session.restored) return\nnavigator.replace(Route.Home)",
        ),
        AppMessageRenderBlock.Markdown(markdown = "接下来运行测试，确认登录与退出都正常。"),
    )

    val runningCommand = HydratedCommandExecutionData(
        command = "make test",
        cwd = "/Users/me/AgentBuddy",
        status = AppOperationStatus.IN_PROGRESS,
        output = "> Task :app:compileDebugKotlin\n> Task :app:testDebugUnitTest\nLoginFlowTest > restoresSession",
        exitCode = null,
        durationMs = null,
        processId = null,
        actions = emptyList(),
    )

    val finishedCommand = HydratedCommandExecutionData(
        command = "git status --short",
        cwd = "/Users/me/AgentBuddy",
        status = AppOperationStatus.COMPLETED,
        output = " M app/src/main/java/LoginViewModel.kt\n M app/src/main/java/SplashScreen.kt",
        exitCode = 0,
        durationMs = 420,
        processId = null,
        actions = emptyList(),
    )

    const val smallDiff = "@@ -12,7 +12,8 @@ class LoginViewModel\n" +
        "     fun onStart() {\n" +
        "-        navigator.replace(Route.Home)\n" +
        "+        if (!session.restored) return\n" +
        "+        navigator.replace(Route.Home)\n" +
        "     }"

    val fileChange = HydratedFileChangeData(
        status = AppOperationStatus.COMPLETED,
        changes = listOf(
            HydratedFileChangeEntryData(
                path = "app/src/main/java/LoginViewModel.kt",
                kind = "update",
                diff = smallDiff,
                additions = 2u,
                deletions = 1u,
            ),
        ),
    )

    val todoList = HydratedTodoListData(
        steps = listOf(
            HydratedPlanStep(step = "定位闪烁来源", status = HydratedPlanStepStatus.COMPLETED),
            HydratedPlanStep(step = "在恢复完成前保持启动页", status = HydratedPlanStepStatus.IN_PROGRESS),
            HydratedPlanStep(step = "运行登录相关测试", status = HydratedPlanStepStatus.PENDING),
        ),
    )

    val subagent = HydratedMultiAgentActionData(
        tool = "spawn_agent",
        status = AppOperationStatus.IN_PROGRESS,
        prompt = "检查退出登录后是否还会闪回首页。",
        targets = listOf("Reviewer [explorer]"),
        receiverThreadIds = listOf("thread-reviewer"),
        agentStates = listOf(
            HydratedMultiAgentStateData(targetId = "thread-reviewer", status = AppSubagentStatus.RUNNING, message = null),
        ),
    )

    val subagentRows = listOf(
        SubagentRowDisplay(
            label = "Reviewer [explorer]",
            status = AppSubagentStatus.RUNNING,
            threadKey = ThreadKey(serverId = "gallery", threadId = "thread-reviewer"),
        ),
        SubagentRowDisplay(label = "Tester [worker]", status = AppSubagentStatus.COMPLETED, threadKey = null),
    )

    val mcpCall = HydratedMcpToolCallData(
        server = "github",
        tool = "search_issues",
        status = AppOperationStatus.COMPLETED,
        durationMs = 1_300,
        argumentsJson = "{\"query\":\"login flicker\"}",
        contentSummary = "找到 2 个相关问题",
        structuredContentJson = null,
        rawOutputJson = null,
        errorMessage = null,
        progressMessages = emptyList(),
        computerUse = null,
    )

    val error = HydratedErrorData(
        title = "测试失败",
        message = "LoginFlowTest > signOutClearsSession 失败：期望停在登录页。",
        details = "exit code 1",
    )

    val imageGenerating = HydratedImageGenerationData(
        status = AppOperationStatus.IN_PROGRESS,
        revisedPrompt = null,
        imagePng = null,
        savedPath = null,
    )

    val imageFailed = HydratedImageGenerationData(
        status = AppOperationStatus.FAILED,
        revisedPrompt = "一只坐在笔记本电脑前的猫，扁平插画风格",
        imagePng = null,
        savedPath = null,
    )

    val compaction = HydratedDividerData.ContextCompaction(isComplete = true)

    val explorationGroup = ExplorationGroup(
        id = "exploration-gallery",
        items = listOf(
            commandItem(
                id = "x1",
                status = AppOperationStatus.COMPLETED,
                actions = listOf(
                    action(HydratedCommandActionKind.READ, "cat LoginViewModel.kt", path = "app/src/main/java/LoginViewModel.kt"),
                    action(HydratedCommandActionKind.READ, "cat SplashScreen.kt", path = "app/src/main/java/SplashScreen.kt"),
                ),
            ),
            commandItem(
                id = "x2",
                status = AppOperationStatus.IN_PROGRESS,
                actions = listOf(
                    action(HydratedCommandActionKind.SEARCH, "rg restored", query = "restored", path = "app/src"),
                    action(HydratedCommandActionKind.LIST_FILES, "ls app/src/main/java", path = "app/src/main/java"),
                ),
            ),
        ),
    )

    val collapsedTurn = TranscriptTurn(
        id = "turn-gallery-old",
        turnId = "t0",
        items = listOf(
            item("c1", HydratedConversationItemContent.User(HydratedUserMessageData(text = "先看看首页为什么加载慢", imageDataUris = emptyList())), boundary = true),
            item("c2", HydratedConversationItemContent.CommandExecution(finishedCommand.copy(durationMs = 1_800))),
            item("c3", HydratedConversationItemContent.CommandExecution(finishedCommand.copy(durationMs = 900))),
            item("c4", HydratedConversationItemContent.FileChange(fileChange)),
            item(
                "c5",
                HydratedConversationItemContent.Assistant(
                    HydratedAssistantMessageData(
                        text = "首页在主线程解析了整份会话列表。我把解析挪到后台并加了分页，冷启动快了一半。",
                        agentNickname = null,
                        agentRole = null,
                        phase = AppMessagePhase.FINAL_ANSWER,
                    ),
                ),
            ),
        ),
        isActiveTurn = false,
        isCollapsedByDefault = true,
    )

    // ── conversation-long ──

    val longUserMessage = HydratedUserMessageData(
        text = "这是一条很长的消息，用来检查气泡的折行和「展开」。".repeat(60),
        imageDataUris = emptyList(),
    )

    val longAssistantBlocks: List<AppMessageRenderBlock> = listOf(
        AppMessageRenderBlock.Markdown(
            markdown = "## 长内容检查\n\n" +
                "这一段很长的说明文字会在屏幕宽度内正常折行，页面本身永远不应该横向滚动；只有代码区域可以左右滑动。".repeat(3) +
                "\n\n1. 第一项：`/Users/me/Projects/AgentBuddy/apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/conversation/TimelineMessageRows.kt`\n" +
                "2. 第二项：普通文字\n",
        ),
        AppMessageRenderBlock.CodeBlock(
            language = "bash",
            code = "./gradlew :app:testDebugUnitTest --tests 'com.akashark.agentbuddy.android.ui.conversation.MathMarkdownTest' --info --stacktrace --no-daemon\necho done",
        ),
    )

    val longPathCommand = HydratedCommandExecutionData(
        command = "cat /Users/me/Projects/AgentBuddy/apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/conversation/ConversationTranscriptList.kt",
        cwd = "/Users/me/AgentBuddy",
        status = AppOperationStatus.COMPLETED,
        output = "package com.akashark.agentbuddy.android.ui.conversation // a very long first line of output that must stay inside the output viewport",
        exitCode = 0,
        durationMs = 65_000,
        processId = null,
        actions = emptyList(),
    )

    val longPathFileChange = HydratedFileChangeData(
        status = AppOperationStatus.COMPLETED,
        changes = listOf(
            HydratedFileChangeEntryData(
                path = "apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/conversation/AVeryLongFileNameForTheGalleryCheck.kt",
                kind = "add",
                diff = "+" + "val x = \"a very long added line that should scroll sideways inside the diff area only\"".repeat(2),
                additions = 1u,
                deletions = 0u,
            ),
        ),
    )

    private fun action(
        kind: HydratedCommandActionKind,
        command: String,
        path: String? = null,
        query: String? = null,
    ) = HydratedCommandActionData(kind = kind, command = command, name = null, path = path, query = query)

    private fun commandItem(
        id: String,
        status: AppOperationStatus,
        actions: List<HydratedCommandActionData>,
    ) = item(
        id,
        HydratedConversationItemContent.CommandExecution(
            HydratedCommandExecutionData(
                command = actions.joinToString(" && ") { it.command },
                cwd = "/Users/me/AgentBuddy",
                status = status,
                output = null,
                exitCode = null,
                durationMs = null,
                processId = null,
                actions = actions,
            ),
        ),
    )

    private fun item(
        id: String,
        content: HydratedConversationItemContent,
        boundary: Boolean = false,
    ) = HydratedConversationItem(
        id = id,
        content = content,
        sourceTurnId = "t0",
        sourceTurnIndex = 0u,
        timestamp = null,
        isFromUserTurnBoundary = boundary,
    )
}
