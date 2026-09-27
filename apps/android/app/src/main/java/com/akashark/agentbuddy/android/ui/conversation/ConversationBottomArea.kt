package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.MinigameOverlayState
import com.akashark.agentbuddy.android.state.contextPercent
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.PendingUserInputRequest
import uniffi.codex_mobile_client.ThreadKey

@Composable
internal fun ConversationBottomArea(
    appModel: AppModel,
    threadKey: ThreadKey,
    thread: AppThreadSnapshot?,
    server: AppServerSnapshot?,
    items: List<HydratedConversationItem>,
    hasWallpaper: Boolean,
    headerScrimColor: Color,
    pinnedContext: PinnedContextData?,
    showUnsupportedHostHint: Boolean,
    onDismissUnsupportedHostHint: () -> Unit,
    isThinking: Boolean,
    activeTaskSummary: ActiveTaskSummary?,
    pendingInput: PendingUserInputRequest?,
    onShowSessionDiffSheet: () -> Unit,
    onShowCollaborationModeSelector: () -> Unit,
    onToggleModelSelector: () -> Unit,
    onNavigateToSessions: (() -> Unit)?,
    onShowDirectoryPicker: (() -> Unit)?,
    onShowRenameDialog: (String?) -> Unit,
    onShowPermissionsSheet: () -> Unit,
    onShowExperimentalSheet: () -> Unit,
    onShowSkillsSheet: () -> Unit,
    onSlashError: (String) -> Unit,
    onDismissPendingUserInput: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Floating minigame launcher — visible only while thinking,
        // gated by the experimental flag.
        val minigameFeatureOn = com.akashark.agentbuddy.android.ui.ExperimentalFeatures.isEnabled(
            com.akashark.agentbuddy.android.ui.AgentBuddyFeature.THINKING_MINIGAME,
        )
        if (minigameFeatureOn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                MinigameLaunchButton(onClick = {
                    val (lastUser, lastAssistant) = lastUserAndAssistantText(items)
                    appModel.requestMinigame(
                        parentThreadId = threadKey.threadId,
                        serverId = threadKey.serverId,
                        lastUserMessage = lastUser,
                        lastAssistantMessage = lastAssistant,
                    )
                })
            }
        }

        // Gradient fade from transparent to scrim
        if (hasWallpaper) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, headerScrimColor),
                        ),
                    ),
            )
        }

        // Solid scrim area for controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerScrimColor),
        ) {
            // Pinned context strip
            if (pinnedContext != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AgentBuddyTheme.codeBackground.copy(alpha = if (hasWallpaper) 0.75f else 1f))
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pinnedContext.todoProgress?.let { todo ->
                        PlanContextBadge(progress = todo)
                    }
                    pinnedContext.diffSummary?.let { diff ->
                        DiffSummaryBadge(
                            summary = diff,
                            onClick = onShowSessionDiffSheet,
                        )
                        CollaborationModeChip(
                            mode = thread?.collaborationMode ?: uniffi.codex_mobile_client.AppModeKind.DEFAULT,
                            onClick = onShowCollaborationModeSelector,
                        )
                    }
                }
            }

            if (showUnsupportedHostHint) {
                UnsupportedHostPushHint(
                    onDismiss = onDismissUnsupportedHostHint,
                )
            }

            // Inline voice status strip (above composer when voice active)
            run {
                val voiceController = remember { com.akashark.agentbuddy.android.state.VoiceRuntimeController.shared }
                val voiceLocalSession by voiceController.activeVoiceSession.collectAsState()
                val voiceSnap by appModel.snapshot.collectAsState()
                val voicePhase = voiceSnap?.voiceSession?.phase
                if (voiceLocalSession != null && voicePhase != null) {
                    com.akashark.agentbuddy.android.ui.voice.InlineVoiceStatusStrip(
                        phase = voicePhase,
                        inputLevel = voiceLocalSession?.inputLevel ?: 0f,
                        outputLevel = voiceLocalSession?.outputLevel ?: 0f,
                        onToggleSpeaker = {
                            val current = voiceController.isSpeakerEnabled()
                            voiceController.setSpeakerEnabled(!current)
                        },
                    )
                }
            }

            // Composer bar
            ComposerBar(
                threadKey = threadKey,
                collaborationMode = thread?.collaborationMode ?: uniffi.codex_mobile_client.AppModeKind.DEFAULT,
                activePlanProgress = thread?.activePlanProgress,
                activeTurnId = thread?.activeTurnId,
                contextPercent = thread?.composerContextPercent(),
                isThinking = isThinking,
                activeTaskSummary = activeTaskSummary,
                queuedFollowUps = thread?.queuedFollowUps ?: emptyList(),
                goal = thread?.goal,
                rateLimits = thread?.agentRuntimeKind?.let { runtimeKind ->
                    server?.rateLimitsByRuntime?.firstOrNull { it.runtimeKind == runtimeKind }?.rateLimits
                },
                showCollaborationModeChip = pinnedContext?.diffSummary == null,
                onOpenCollaborationModePicker = onShowCollaborationModeSelector,
                onToggleModelSelector = onToggleModelSelector,
                onNavigateToSessions = onNavigateToSessions,
                onShowDirectoryPicker = onShowDirectoryPicker,
                onShowRenameDialog = onShowRenameDialog,
                onShowPermissionsSheet = onShowPermissionsSheet,
                onShowExperimentalSheet = onShowExperimentalSheet,
                onShowSkillsSheet = onShowSkillsSheet,
                onSlashError = onSlashError,
                pendingUserInput = pendingInput,
                onDismissPendingUserInput = onDismissPendingUserInput,
            )

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
internal fun BoxScope.ConversationMinigameOverlay(
    appModel: AppModel,
    threadKey: ThreadKey,
    items: List<HydratedConversationItem>,
    isMinigameActive: Boolean,
    minigameOverlay: MinigameOverlayState,
) {
    // Thinking-indicator minigame overlay: bottom 40% of the screen.
    // Slides up from the bottom when appearing and slides back out on
    // dismiss, mirroring iOS ConversationView.swift:148
    // `.transition(.move(edge: .bottom).combined(with: .opacity))`.
    AnimatedVisibility(
        visible = isMinigameActive,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.4f)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .navigationBarsPadding(),
    ) {
        MinigameOverlay(
            state = minigameOverlay,
            onClose = { appModel.dismissMinigame() },
            onRetry = {
                val (lastUser, lastAssistant) = lastUserAndAssistantText(items)
                appModel.dismissMinigame()
                appModel.requestMinigame(
                    parentThreadId = threadKey.threadId,
                    serverId = threadKey.serverId,
                    lastUserMessage = lastUser,
                    lastAssistantMessage = lastAssistant,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun uniffi.codex_mobile_client.AppThreadSnapshot.composerContextPercent(): Int? {
    if (contextTokensUsed == null && modelContextWindow == null) return null
    val contextWindow = modelContextWindow?.toLong()
    val baseline = 12_000L
    if (contextWindow == null || contextWindow <= baseline) {
        return contextPercent.coerceIn(0, 100)
    }
    val totalTokens = contextTokensUsed?.toLong() ?: baseline
    val effectiveWindow = contextWindow - baseline
    val usedTokens = (totalTokens - baseline).coerceAtLeast(0)
    val remainingTokens = (effectiveWindow - usedTokens).coerceAtLeast(0)
    return ((remainingTokens.toDouble() / effectiveWindow.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}

@Composable
private fun UnsupportedHostPushHint(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(AgentBuddyTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.NotificationsOff,
            contentDescription = null,
            tint = AgentBuddyTheme.warning,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "该主机版本不支持完成通知，升级桌面 App 后可用",
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = AgentBuddyTheme.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun PlanContextBadge(progress: String) {
    Text(
        text = "计划 $progress",
        color = AgentBuddyTheme.accent,
        fontSize = AgentBuddyTextStyle.caption2.scaled,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(AgentBuddyTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun DiffSummaryBadge(
    summary: DiffSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(AgentBuddyTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\u2194",
            color = AgentBuddyTheme.accent,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
            fontWeight = FontWeight.SemiBold,
        )
        if (summary.hasChanges) {
            Text(
                text = "+${summary.additions}",
                color = AgentBuddyTheme.success,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BerkeleyMono,
            )
            Text(
                text = "-${summary.deletions}",
                color = AgentBuddyTheme.danger,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BerkeleyMono,
            )
        } else {
            Text(
                text = "差异",
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun lastUserAndAssistantText(
    items: List<uniffi.codex_mobile_client.HydratedConversationItem>,
): Pair<String?, String?> {
    var lastUser: String? = null
    var lastAssistant: String? = null
    for (item in items.reversed()) {
        when (val c = item.content) {
            is HydratedConversationItemContent.User -> if (lastUser == null) lastUser = c.v1.text
            is HydratedConversationItemContent.Assistant -> if (lastAssistant == null) lastAssistant = c.v1.text
            else -> {}
        }
        if (lastUser != null && lastAssistant != null) break
    }
    return lastUser to lastAssistant
}

@Composable
private fun MinigameLaunchButton(onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = AgentBuddyTheme.surface.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, AgentBuddyTheme.accent.copy(alpha = 0.3f)),
        shadowElevation = 2.dp,
        modifier = Modifier.size(36.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = "等待时玩个小游戏",
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
