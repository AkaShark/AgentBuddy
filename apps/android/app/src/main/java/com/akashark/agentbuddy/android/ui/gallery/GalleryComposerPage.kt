package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.akashark.agentbuddy.android.ui.conversation.ComposerControlsState
import com.akashark.agentbuddy.android.ui.conversation.ComposerDictationButton
import com.akashark.agentbuddy.android.ui.conversation.ComposerEditorCard
import com.akashark.agentbuddy.android.ui.conversation.ComposerExpandedDialog
import com.akashark.agentbuddy.android.ui.conversation.ComposerIndicatorsRow
import com.akashark.agentbuddy.android.ui.conversation.ComposerNotices
import com.akashark.agentbuddy.android.ui.conversation.ComposerPendingInputCard
import com.akashark.agentbuddy.android.ui.conversation.QueuedFollowUpsPreviewPanel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySectionHeader
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import uniffi.codex_mobile_client.AppQueuedFollowUpKind
import uniffi.codex_mobile_client.AppQueuedFollowUpPreview
import uniffi.codex_mobile_client.PendingUserInputOption
import uniffi.codex_mobile_client.PendingUserInputQuestion
import uniffi.codex_mobile_client.PendingUserInputRequest

/**
 * DEBUG gallery: every composer state on one scrolling page (idle, running
 * with stop + 「排队」, stopping, disconnected, creating, failed send, queue,
 * pending question).
 */
@Composable
fun GalleryComposerPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(vertical = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Section("空闲 · 无输入")
        GalleryComposer(ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = false))
        Section("空闲 · 有输入")
        GalleryComposer(ComposerControlsState(hasContent = true, isConnected = true, isTurnActive = false), "帮我检查登录流程")
        Section("执行中 · 停止")
        GalleryComposer(ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = true))
        Section("执行中 · 有输入时排队")
        GalleryComposer(ComposerControlsState(hasContent = true, isConnected = true, isTurnActive = true), "顺便把退出流程也检查一下")
        Section("正在停止")
        GalleryComposer(ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = true, isStopping = true))
        Section("断线")
        ComposerNotices(
            isConnected = false,
            sendError = null,
            onRetrySend = null,
            onDismissSendError = {},
            stopError = null,
            onDismissStopError = {},
        )
        GalleryComposer(ComposerControlsState(hasContent = true, isConnected = false, isTurnActive = false), "草稿会保留")
        Section("断线 · 斜杠命令仍可运行")
        GalleryComposer(
            ComposerControlsState(hasContent = true, isConnected = false, isTurnActive = true, isSlashCommand = true),
            "/rename 新标题",
        )
        Section("创建中")
        GalleryComposer(ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = false, isCreating = true))
        Section("发送失败")
        ComposerNotices(
            isConnected = true,
            sendError = "连接已断开，等搭子重连后再试。",
            onRetrySend = {},
            onDismissSendError = {},
            stopError = "没能停止任务：主机没有响应。",
            onDismissStopError = {},
        )
        Section("排队 · 2 条")
        QueuedFollowUpsPreviewPanel(
            previews = listOf(
                AppQueuedFollowUpPreview(id = "q1", kind = AppQueuedFollowUpKind.MESSAGE, text = "测试通过后，再补一段发布说明。"),
                AppQueuedFollowUpPreview(id = "q2", kind = AppQueuedFollowUpKind.PENDING_STEER, text = "先别改样式，只修跳转。"),
            ),
            onSteer = {},
            onDelete = {},
        )
        GalleryComposer(ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = true))
        Section("等待你的回答")
        var answers by remember { mutableStateOf(mapOf("scope" to "只改登录")) }
        ComposerPendingInputCard(
            pendingUserInput = galleryPendingInput,
            userInputAnswers = answers,
            onAnswerChange = { id, value -> answers = answers + (id to value) },
            pendingUserInputSubmitError = null,
            isSubmittingPendingUserInput = false,
            onDismissPendingUserInput = {},
            onSubmit = {},
        )
        GalleryComposer(ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = true))
        Section("等待你的回答 · 多个问题（卡片内滚动）")
        ComposerPendingInputCard(
            pendingUserInput = galleryPendingInput.copy(
                id = "input-2",
                questions = galleryPendingInput.questions + (1..4).map { n ->
                    PendingUserInputQuestion(
                        id = "extra-$n",
                        header = "补充 $n",
                        question = "第 $n 个补充问题：这里需要注意什么？",
                        isOtherAllowed = true,
                        isSecret = false,
                        options = emptyList(),
                    )
                },
            ),
            userInputAnswers = answers,
            onAnswerChange = { id, value -> answers = answers + (id to value) },
            pendingUserInputSubmitError = "连接已断开，等搭子重连后再试。",
            isSubmittingPendingUserInput = false,
            onDismissPendingUserInput = {},
            onSubmit = {},
        )
        ComposerIndicatorsRow(contextPercent = 62, rateLimits = null)
    }
}

/** The full-screen editor while a turn runs: the send control reads 「排队」. */
@Composable
fun GalleryComposerExpandedQueuePage() {
    var text by remember { mutableStateOf("顺便把退出流程也检查一下，\n然后补一段发布说明。") }
    ComposerExpandedDialog(
        text = text,
        onTextChange = { text = it },
        onSend = {},
        onDismiss = {},
        canSend = true,
        queues = true,
    )
}

@Composable
private fun Section(title: String) {
    BuddySectionHeader(title, modifier = Modifier.padding(horizontal = BuddySpacing.md))
}

@Composable
private fun GalleryComposer(controls: ComposerControlsState, initialText: String = "") {
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    ComposerEditorCard(
        textFieldValue = value,
        onTextFieldValueChange = { value = it },
        controls = controls,
        onSend = {},
        onStop = {},
        onAttach = {},
        onShowExpanded = {},
        partnerLabel = "Codex",
        onOpenModelPanel = {},
        voiceControl = { ComposerDictationButton(onClick = {}) },
    )
}

private val galleryPendingInput = PendingUserInputRequest(
    id = "input-1",
    serverId = "gallery",
    threadId = "gallery-thread",
    turnId = "turn-1",
    itemId = "item-1",
    questions = listOf(
        PendingUserInputQuestion(
            id = "scope",
            header = "范围",
            question = "这次要改哪些页面？",
            isOtherAllowed = false,
            isSecret = false,
            options = listOf(
                PendingUserInputOption(label = "只改登录", description = null),
                PendingUserInputOption(label = "登录和注册", description = null),
                PendingUserInputOption(label = "所有需要登录的页面", description = null),
            ),
        ),
        PendingUserInputQuestion(
            id = "note",
            header = null,
            question = "还有什么要注意的？",
            isOtherAllowed = true,
            isSecret = false,
            options = emptyList(),
        ),
    ),
    requesterAgentNickname = "Codex",
    requesterAgentRole = null,
)
