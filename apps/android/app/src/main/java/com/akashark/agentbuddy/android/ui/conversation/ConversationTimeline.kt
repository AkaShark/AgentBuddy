package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import uniffi.codex_mobile_client.AppOperationStatus
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import kotlinx.coroutines.delay

/**
 * Renders a single [HydratedConversationItem] by matching on its content type.
 * Uses Rust-provided types directly — no intermediate model conversion.
 */
@Composable
fun ConversationTimelineItem(
    item: HydratedConversationItem,
    serverId: String,
    threadId: String,
    agentDirectoryVersion: ULong,
    latestCommandExecutionItemId: String? = null,
    isLiveTurn: Boolean = false,
    isStreamingMessage: Boolean = false,
    onStreamingSnapshotRendered: (() -> Unit)? = null,
    onEditMessage: ((String) -> Unit)? = null,
    onForkFromMessage: ((String) -> Unit)? = null,
    onOpenSavedApp: ((String) -> Unit)? = null,
    onWidgetPrompt: ((String) -> Unit)? = null,
) {
    val shouldNotifyLiveContentRendered = remember(item.content, isLiveTurn) {
        isLiveTurn && item.content.shouldAutoFollowRenderedContent()
    }

    LaunchedEffect(item.id, item.hashCode(), shouldNotifyLiveContentRendered) {
        if (!shouldNotifyLiveContentRendered) return@LaunchedEffect
        delay(32)
        onStreamingSnapshotRendered?.invoke()
    }

    when (val content = item.content) {
        is HydratedConversationItemContent.User -> UserMessageRow(
            data = content.v1,
            itemId = item.id,
            onEdit = onEditMessage,
            onFork = onForkFromMessage,
        )

        is HydratedConversationItemContent.Assistant -> AssistantMessageRow(
            itemId = item.id,
            data = content.v1,
            serverId = serverId,
            agentDirectoryVersion = agentDirectoryVersion,
            isStreamingMessage = isStreamingMessage,
            onStreamingSnapshotRendered = onStreamingSnapshotRendered,
        )

        is HydratedConversationItemContent.CodeReview -> CodeReviewRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.Reasoning -> ReasoningRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.CommandExecution -> CommandExecutionRow(
            data = content.v1,
            keepExpanded = item.id == latestCommandExecutionItemId ||
                content.v1.status == AppOperationStatus.PENDING ||
                content.v1.status == AppOperationStatus.IN_PROGRESS,
        )

        is HydratedConversationItemContent.FileChange -> FileChangeRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.TurnDiff -> TurnDiffRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.TodoList -> TodoListRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.ProposedPlan -> ProposedPlanRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.McpToolCall -> {
            val cu = content.v1.computerUse
            if (cu != null) {
                ComputerUseToolCallRow(data = content.v1, view = cu)
            } else {
                McpToolCallRow(data = content.v1)
            }
        }

        is HydratedConversationItemContent.DynamicToolCall -> DynamicToolCallRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.MultiAgentAction -> {
            SubagentCard(data = content.v1, serverId = serverId)
        }

        is HydratedConversationItemContent.WebSearch -> WebSearchRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.ImageView -> ImageViewRow(
            data = content.v1,
            serverId = serverId,
        )

        is HydratedConversationItemContent.ImageGeneration -> ImageGenerationRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.Widget -> WidgetRow(
            data = content.v1,
            originThreadId = threadId,
            onOpenSavedApp = onOpenSavedApp,
            onWidgetPrompt = onWidgetPrompt,
        )

        is HydratedConversationItemContent.UserInputResponse -> UserInputResponseRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.Divider -> DividerRow(
            data = content.v1,
            isLiveTurn = isLiveTurn,
        )

        is HydratedConversationItemContent.Error -> ErrorRow(
            data = content.v1,
        )

        is HydratedConversationItemContent.Note -> NoteRow(
            data = content.v1,
        )
    }
}

private fun HydratedConversationItemContent.shouldAutoFollowRenderedContent(): Boolean {
    return when (this) {
        is HydratedConversationItemContent.Reasoning,
        is HydratedConversationItemContent.CommandExecution,
        is HydratedConversationItemContent.FileChange,
        is HydratedConversationItemContent.TurnDiff,
        is HydratedConversationItemContent.McpToolCall,
        is HydratedConversationItemContent.DynamicToolCall,
        is HydratedConversationItemContent.MultiAgentAction,
        is HydratedConversationItemContent.WebSearch,
        is HydratedConversationItemContent.ImageView,
        is HydratedConversationItemContent.ImageGeneration,
        is HydratedConversationItemContent.Widget -> true
        else -> false
    }
}
