package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.scaled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.ThreadKey

@Composable
internal fun ConversationTranscriptList(
    appModel: AppModel,
    scope: CoroutineScope,
    threadKey: ThreadKey,
    thread: AppThreadSnapshot,
    items: List<HydratedConversationItem>,
    listState: LazyListState,
    hasWallpaper: Boolean,
    isWaitingForData: Boolean,
    isInitialTurnsLoading: Boolean,
    hasMoreTurnsAbove: Boolean,
    isLoadingOlderTurns: Boolean,
    onLoadOlderTurns: () -> Unit,
    displayedTurns: List<TranscriptTurn>,
    expandedTurnIds: Set<String>,
    onExpandTurn: (String) -> Unit,
    onCollapseTurn: (String) -> Unit,
    agentDirectoryVersion: ULong,
    onStreamingSnapshotRendered: () -> Unit,
    onOpenSavedApp: ((String) -> Unit)?,
) {
    // Use transparent gradient when wallpaper is set
    val fadeColor = if (hasWallpaper) Color.Transparent else AgentBuddyTheme.background

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .then(
                if (!hasWallpaper) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(AgentBuddyTheme.background, Color.Transparent),
                                startY = 0f,
                                endY = 48f,
                            ),
                        )
                    }
                } else Modifier.drawWithContent { drawContent() }
            ),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        if (isWaitingForData || isInitialTurnsLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isInitialTurnsLoading) {
                        CircularProgressIndicator(
                            color = AgentBuddyTheme.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            "正在加载会话…",
                            color = AgentBuddyTheme.textMuted,
                            fontSize = AgentBuddyTextStyle.caption.scaled,
                        )
                    }
                }
            }
        }

        if (hasMoreTurnsAbove) {
            item {
                TextButton(
                    enabled = !isLoadingOlderTurns,
                    onClick = onLoadOlderTurns,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoadingOlderTurns) {
                        CircularProgressIndicator(
                            color = AgentBuddyTheme.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "加载更早的消息",
                            color = AgentBuddyTheme.accent,
                            fontSize = AgentBuddyTextStyle.caption.scaled,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = displayedTurns,
            key = { index, turn -> "${turn.id}#$index" },
        ) { _, turn ->
            val isExpanded = !turn.isCollapsedByDefault || expandedTurnIds.contains(turn.id)
            val streamingAssistantItemId = remember(turn.items, turn.isActiveTurn) {
                if (!turn.isActiveTurn) {
                    null
                } else {
                    turn.items.lastOrNull {
                        it.content is HydratedConversationItemContent.Assistant
                    }?.id
                }
            }
            if (isExpanded) {
                val timelineEntries = remember(turn.items, turn.isActiveTurn) {
                    buildTimelineEntries(turn.items, turn.isActiveTurn)
                }
                val latestCommandExecutionItemId = remember(timelineEntries) {
                    timelineEntries.asReversed().firstNotNullOfOrNull { entry ->
                        when (entry) {
                            is TimelineEntry.Single -> {
                                if (entry.item.content is HydratedConversationItemContent.CommandExecution) {
                                    entry.item.id
                                } else {
                                    null
                                }
                            }

                            is TimelineEntry.Exploration -> null
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timelineEntries.forEachIndexed { index, entry ->
                        when (entry) {
                            is TimelineEntry.Single -> {
                                ConversationTimelineItem(
                                    item = entry.item,
                                    serverId = threadKey.serverId,
                                    threadId = threadKey.threadId,
                                    agentDirectoryVersion = agentDirectoryVersion,
                                    latestCommandExecutionItemId = latestCommandExecutionItemId,
                                    isLiveTurn = turn.isActiveTurn,
                                    isStreamingMessage = entry.item.id == streamingAssistantItemId,
                                    onStreamingSnapshotRendered = if (entry.item.id == streamingAssistantItemId) {
                                        onStreamingSnapshotRendered
                                    } else {
                                        null
                                    },
                                    onEditMessage = { messageId ->
                                        // Resolve the user-message position in the
                                        // currently-loaded transcript. The Rust
                                        // `editMessage` / `forkThreadFromMessage` APIs
                                        // expect an index into `thread.items` filtered
                                        // to user messages — recomputing here keeps
                                        // the index correct under pagination, where a
                                        // cached `sourceTurnIndex` from a prior hydrate
                                        // would be stale.
                                        loadedUserItemIndex(items, messageId)?.let { turnIndex ->
                                            scope.launch {
                                                val prefill = appModel.store.editMessage(threadKey, turnIndex)
                                                appModel.queueComposerPrefill(threadKey, prefill)
                                            }
                                        }
                                    },
                                    onForkFromMessage = { messageId ->
                                        loadedUserItemIndex(items, messageId)?.let { turnIndex ->
                                            scope.launch {
                                                try {
                                                    val newKey = appModel.store.forkThreadFromMessage(
                                                        threadKey,
                                                        turnIndex,
                                                        appModel.launchState.forkThreadFromMessageRequest(
                                                            cwdOverride = thread.info.cwd,
                                                            threadKey = threadKey,
                                                        ),
                                                    )
                                                    appModel.store.setActiveThread(newKey)
                                                    appModel.refreshThreadSnapshot(newKey)
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    onOpenSavedApp = onOpenSavedApp,
                                    onWidgetPrompt = { text ->
                                        scope.launch {
                                            try {
                                                val payload = com.akashark.agentbuddy.android.state.AppComposerPayload(
                                                    text = text,
                                                    additionalInputs = emptyList(),
                                                    approvalPolicy = appModel.launchState.approvalPolicyValue(threadKey),
                                                    sandboxPolicy = appModel.launchState.turnSandboxPolicy(threadKey),
                                                    model = appModel.launchState.snapshot.value.selectedModel.trim().ifEmpty { null },
                                                    reasoningEffort = null,
                                                    serviceTier = null,
                                                )
                                                appModel.startTurn(threadKey, payload)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                )
                            }

                            is TimelineEntry.Exploration -> {
                                ExplorationGroupRow(
                                    group = entry.group,
                                    showsCollapsedPreview = index == timelineEntries.lastIndex,
                                )
                            }
                        }
                    }

                    // Debug turn metrics
                    if (com.akashark.agentbuddy.android.state.DebugSettings.enabled && com.akashark.agentbuddy.android.state.DebugSettings.showTurnMetrics) {
                        val metricsText = remember(turn.items) {
                            val dur = turn.totalDurationMs
                            val cmds = turn.commandCount
                            val files = turn.fileChangeCount
                            val itemCount = turn.items.size
                            buildString {
                                append("$itemCount items")
                                if (cmds > 0) append(" \u00b7 $cmds cmds")
                                if (files > 0) append(" \u00b7 $files files")
                                if (dur > 0) {
                                    val durStr = if (dur < 1000) "${dur}ms" else "%.1fs".format(dur / 1000.0)
                                    append(" \u00b7 $durStr")
                                }
                            }
                        }
                        Text(
                            text = metricsText,
                            color = AgentBuddyTheme.textMuted.copy(alpha = 0.6f),
                            fontSize = 10f.scaled,
                            fontFamily = com.akashark.agentbuddy.android.ui.BerkeleyMono,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                        )
                    }

                    if (turn.isActiveTurn) {
                        StreamingCursor()
                    }

                    if (turn.isCollapsedByDefault) {
                        Text(
                            text = "收起",
                            color = AgentBuddyTheme.textMuted,
                            fontSize = AgentBuddyTextStyle.caption2.scaled,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable {
                                    onCollapseTurn(turn.id)
                                }
                                .padding(top = 2.dp),
                        )
                    }
                }
            } else {
                CollapsedTurnCard(turn = turn) {
                    onExpandTurn(turn.id)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

/**
 * Resolve the user-message position in the currently-loaded transcript.
 * `forkThreadFromMessage` / `editMessage` on the Rust side expect an index
 * into the thread's items filtered to user messages — see
 * `rollback_depth_for_turn` in `mobile_client/thread_projection.rs`.
 * Recomputing from the live `items` keeps the index correct under
 * pagination (older turns can shift positions; a cached `sourceTurnIndex`
 * from a prior hydrate would be stale).
 */
private fun loadedUserItemIndex(
    items: List<uniffi.codex_mobile_client.HydratedConversationItem>,
    messageId: String,
): UInt? {
    var idx = 0u
    for (candidate in items) {
        if (candidate.content !is HydratedConversationItemContent.User) continue
        if (candidate.id == messageId) return idx
        idx++
    }
    return null
}

/**
 * Shimmering "Thinking..." text shown while the assistant is working.
 */
@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            AgentBuddyTheme.textSecondary.copy(alpha = 0.4f),
            AgentBuddyTheme.accent,
            AgentBuddyTheme.textSecondary.copy(alpha = 0.4f),
        ),
        start = Offset(shimmerOffset * 200f, 0f),
        end = Offset((shimmerOffset + 0.6f) * 200f, 0f),
    )
    Text(
        text = "思考中...",
        fontSize = AgentBuddyTextStyle.body.scaled,
        fontWeight = FontWeight.Medium,
        style = TextStyle(brush = shimmerBrush),
    )
}
