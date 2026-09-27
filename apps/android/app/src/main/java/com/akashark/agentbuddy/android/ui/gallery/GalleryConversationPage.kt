package com.akashark.agentbuddy.android.ui.gallery

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.conversation.AssistantMessageLayout
import com.akashark.agentbuddy.android.ui.conversation.AssistantRenderBlocks
import com.akashark.agentbuddy.android.ui.conversation.AssistantSpeakerHeader
import com.akashark.agentbuddy.android.ui.conversation.CollapsedTurnCard
import com.akashark.agentbuddy.android.ui.conversation.CommandExecutionRow
import com.akashark.agentbuddy.android.ui.conversation.ConversationLoadingLabel
import com.akashark.agentbuddy.android.ui.conversation.ConversationScrollToLatestButton
import com.akashark.agentbuddy.android.ui.conversation.DiffSection
import com.akashark.agentbuddy.android.ui.conversation.DividerRow
import com.akashark.agentbuddy.android.ui.conversation.ErrorRow
import com.akashark.agentbuddy.android.ui.conversation.ExplorationGroupRow
import com.akashark.agentbuddy.android.ui.conversation.FileChangeRow
import com.akashark.agentbuddy.android.ui.conversation.ImageGenerationRow
import com.akashark.agentbuddy.android.ui.conversation.McpToolCallRow
import com.akashark.agentbuddy.android.ui.conversation.NoteRow
import com.akashark.agentbuddy.android.ui.conversation.ProposedPlanRow
import com.akashark.agentbuddy.android.ui.conversation.ReasoningRow
import com.akashark.agentbuddy.android.ui.conversation.StreamingCursor
import com.akashark.agentbuddy.android.ui.conversation.SubagentCardContent
import com.akashark.agentbuddy.android.ui.conversation.TimelineLinkButton
import com.akashark.agentbuddy.android.ui.conversation.TodoListRow
import com.akashark.agentbuddy.android.ui.conversation.ToolCardShell
import com.akashark.agentbuddy.android.ui.conversation.UserMessageRow
import com.akashark.agentbuddy.android.ui.gallery.GalleryConversationFixtures as F
import kotlinx.coroutines.delay
import uniffi.codex_mobile_client.AppOperationStatus
import uniffi.codex_mobile_client.HydratedNoteData
import uniffi.codex_mobile_client.HydratedProposedPlanData

/** Conversation timeline rows rendered from fixtures, in transcript order. */
@Composable
fun GalleryConversationPage() {
    GalleryTranscript {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimelineLinkButton(text = "加载更早的消息", onClick = {})
        }
        CollapsedTurnCard(turn = F.collapsedTurn, onExpand = {})
        UserMessageRow(data = F.userMessage, itemId = "u1", onEdit = {}, onFork = {})
        AssistantSpeakerHeader(partnerLabel = "Codex")
        ReasoningRow(data = F.reasoning)
        ExplorationGroupRow(group = F.explorationGroup, showsCollapsedPreview = true)
        AssistantMessageLayout(agentNickname = null, agentRole = null) {
            AssistantRenderBlocks(blocks = F.assistantBlocks, fallbackText = "")
        }
        TodoListRow(data = F.todoList)
        CommandExecutionRow(data = F.finishedCommand, keepExpanded = true)
        FileChangeRow(data = F.fileChange)
        ToolCardShell(
            summary = "编辑 LoginViewModel.kt（展开）",
            status = AppOperationStatus.COMPLETED,
            defaultExpanded = true,
        ) {
            DiffSection(label = "", content = F.smallDiff)
        }
        McpToolCallRow(data = F.mcpCall)
        SubagentCardContent(
            data = F.subagent,
            expanded = true,
            onToggle = {},
            rows = F.subagentRows,
            onOpen = {},
        )
        CommandExecutionRow(data = F.runningCommand, keepExpanded = true)
        ErrorRow(data = F.error)
        DividerRow(data = F.compaction, isLiveTurn = false)
        ImageGenerationRow(data = F.imageGenerating)
        StreamingCursor()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ConversationScrollToLatestButton(onClick = {})
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ConversationLoadingLabel(text = "正在加载会话…")
        }
    }
}

/** Long content: only code areas may scroll sideways, never the page. */
@Composable
fun GalleryConversationLongPage() {
    GalleryTranscript {
        UserMessageRow(data = F.longUserMessage, itemId = "u-long", onEdit = {}, onFork = {})
        AssistantSpeakerHeader(partnerLabel = "Claude Code")
        AssistantMessageLayout(agentNickname = "Reviewer", agentRole = "explorer") {
            AssistantRenderBlocks(blocks = F.longAssistantBlocks, fallbackText = "")
        }
        CommandExecutionRow(data = F.longPathCommand, keepExpanded = true)
        FileChangeRow(data = F.longPathFileChange)
        ToolCardShell(
            summary = F.longPathFileChange.changes.first().path,
            status = AppOperationStatus.COMPLETED,
            defaultExpanded = true,
        ) {
            DiffSection(label = "AVeryLongFileNameForTheGalleryCheck.kt", content = F.longPathFileChange.changes.first().diff)
        }
        ProposedPlanRow(
            data = HydratedProposedPlanData(content = "1. 保持启动页直到会话恢复\n2. 为退出登录补一条测试\n3. 在低端机上复测冷启动"),
        )
        NoteRow(data = HydratedNoteData(title = "已切换到计划模式", body = "执行前会先给出计划，等你确认。"))
        ImageGenerationRow(data = F.imageFailed)
    }
}

@Composable
private fun GalleryTranscript(content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    val view = LocalView.current
    // Selectable Markdown TextViews take View focus on launch and bring their
    // cursor into view, so the page would open part-way down. The gallery
    // blocks View focus for its embedded views and starts from the top.
    DisposableEffect(view) {
        val group = view as? ViewGroup
        val previous = group?.descendantFocusability
        group?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        group?.findFocus()?.clearFocus()
        onDispose { if (group != null && previous != null) group.descendantFocusability = previous }
    }
    LaunchedEffect(Unit) {
        delay(1500)
        scrollState.scrollTo(0)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
