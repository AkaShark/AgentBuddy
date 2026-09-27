package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.scaled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppRenameThreadRequest
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.ThreadKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationSheets(
    appModel: AppModel,
    scope: CoroutineScope,
    threadKey: ThreadKey,
    thread: AppThreadSnapshot?,
    showPermissionsSheet: Boolean,
    onDismissPermissionsSheet: () -> Unit,
    showCollaborationModeSelector: Boolean,
    onDismissCollaborationModeSelector: () -> Unit,
    collaborationModePresets: List<uniffi.codex_mobile_client.AppCollaborationModePreset>,
    collaborationModesLoading: Boolean,
    showExperimentalSheet: Boolean,
    onDismissExperimentalSheet: () -> Unit,
    showSkillsSheet: Boolean,
    onDismissSkillsSheet: () -> Unit,
    showSessionDiffSheet: Boolean,
    onDismissSessionDiffSheet: () -> Unit,
    pinnedContext: PinnedContextData?,
    onSlashError: (String) -> Unit,
) {
    if (showPermissionsSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissPermissionsSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AgentBuddyTheme.background,
        ) {
            ComposerPermissionsSheet(
                threadKey = threadKey,
                onDismiss = onDismissPermissionsSheet,
            )
        }
    }

    if (showCollaborationModeSelector) {
        ModalBottomSheet(
            onDismissRequest = onDismissCollaborationModeSelector,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AgentBuddyTheme.background,
        ) {
            CollaborationModeSheet(
                presets = collaborationModePresets.ifEmpty { fallbackCollaborationModePresets() },
                selectedMode = thread?.collaborationMode ?: uniffi.codex_mobile_client.AppModeKind.DEFAULT,
                isLoading = collaborationModesLoading,
                onDismiss = onDismissCollaborationModeSelector,
                onSelect = { mode ->
                    onDismissCollaborationModeSelector()
                    scope.launch {
                        try {
                            appModel.store.setThreadCollaborationMode(threadKey, mode)
                        } catch (e: Exception) {
                            onSlashError(e.message ?: "切换协作模式失败")
                        }
                    }
                },
            )
        }
    }

    if (showExperimentalSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissExperimentalSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AgentBuddyTheme.background,
        ) {
            ComposerExperimentalSheet(
                serverId = threadKey.serverId,
                onDismiss = onDismissExperimentalSheet,
                onError = onSlashError,
            )
        }
    }

    if (showSkillsSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissSkillsSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AgentBuddyTheme.background,
        ) {
            ComposerSkillsSheet(
                serverId = threadKey.serverId,
                cwd = thread?.info?.cwd ?: appModel.launchState.snapshot.value.currentCwd.ifBlank { "/" },
                onDismiss = onDismissSkillsSheet,
                onError = onSlashError,
            )
        }
    }

    if (showSessionDiffSheet && !pinnedContext?.diffSections.isNullOrEmpty()) {
        ModalBottomSheet(
            onDismissRequest = onDismissSessionDiffSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AgentBuddyTheme.background,
        ) {
            SessionDiffSheet(
                sections = pinnedContext?.diffSections.orEmpty(),
                onDismiss = onDismissSessionDiffSheet,
            )
        }
    }
}

@Composable
internal fun ConversationDialogs(
    appModel: AppModel,
    scope: CoroutineScope,
    threadKey: ThreadKey,
    thread: AppThreadSnapshot?,
    showRenameDialog: Boolean,
    onDismissRenameDialog: () -> Unit,
    renameDraft: String,
    onRenameDraftChange: (String) -> Unit,
    slashErrorMessage: String?,
    onDismissSlashError: () -> Unit,
    reloadErrorMessage: String?,
    onDismissReloadError: () -> Unit,
    onSlashError: (String) -> Unit,
) {
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = onDismissRenameDialog,
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = onRenameDraftChange,
                    label = { Text("新会话标题") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nextTitle = renameDraft.trim()
                        if (nextTitle.isEmpty()) {
                            onDismissRenameDialog()
                            return@TextButton
                        }
                        onDismissRenameDialog()
                        scope.launch {
                            try {
                                appModel.client.renameThread(
                                    threadKey.serverId,
                                    AppRenameThreadRequest(
                                        threadId = threadKey.threadId,
                                        name = nextTitle,
                                    ),
                                )
                                appModel.refreshThreadSnapshot(threadKey)
                            } catch (e: Exception) {
                                onSlashError(e.message ?: "Failed to rename conversation")
                            }
                        }
                    },
                ) {
                    Text("重命名")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRenameDialog) {
                    Text("取消")
                }
            },
        )
    }

    thread?.pendingPlanImplementationPrompt?.let {
        AlertDialog(
            onDismissRequest = { appModel.store.dismissPlanImplementationPrompt(threadKey) },
            title = { Text("执行这个计划？") },
            text = { Text("切回默认模式并发送 \"Implement the plan.\"") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                appModel.store.implementPlan(threadKey)
                            } catch (e: Exception) {
                                onSlashError(e.message ?: "执行计划失败")
                            }
                        }
                    },
                ) {
                    Text("是，执行")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { appModel.store.dismissPlanImplementationPrompt(threadKey) },
                ) {
                    Text("不，留在计划模式")
                }
            },
        )
    }

    slashErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissSlashError,
            title = { Text("斜杠命令错误") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismissSlashError) {
                    Text("确定")
                }
            },
        )
    }

    reloadErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissReloadError,
            title = { Text("重新加载失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismissReloadError) {
                    Text("确定")
                }
            },
        )
    }
}

internal fun fallbackCollaborationModePresets(): List<uniffi.codex_mobile_client.AppCollaborationModePreset> =
    listOf(
        uniffi.codex_mobile_client.AppCollaborationModePreset(
            kind = uniffi.codex_mobile_client.AppModeKind.DEFAULT,
            name = "默认",
            model = null,
            reasoningEffort = null,
        ),
        uniffi.codex_mobile_client.AppCollaborationModePreset(
            kind = uniffi.codex_mobile_client.AppModeKind.PLAN,
            name = "计划",
            model = null,
            reasoningEffort = uniffi.codex_mobile_client.ReasoningEffort.MEDIUM,
        ),
    )

@Composable
private fun CollaborationModeSheet(
    presets: List<uniffi.codex_mobile_client.AppCollaborationModePreset>,
    selectedMode: uniffi.codex_mobile_client.AppModeKind,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (uniffi.codex_mobile_client.AppModeKind) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "协作模式",
                color = AgentBuddyTheme.textPrimary,
                fontSize = 18f.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }

        if (isLoading && presets.isEmpty()) {
            CircularProgressIndicator(color = AgentBuddyTheme.accent)
        }

        presets.forEach { preset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.surface, RoundedCornerShape(16.dp))
                    .clickable { onSelect(preset.kind) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = preset.name,
                        color = AgentBuddyTheme.textPrimary,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                    preset.reasoningEffort?.let { effort ->
                        Text(
                            text = collaborationModeEffortLabel(effort),
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = AgentBuddyTextStyle.caption2.scaled,
                        )
                    }
                }
                if (preset.kind == selectedMode) {
                    Text(
                        text = "已选择",
                        color = AgentBuddyTheme.accent,
                        fontSize = AgentBuddyTextStyle.caption2.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun collaborationModeEffortLabel(
    effort: uniffi.codex_mobile_client.ReasoningEffort,
): String =
    when (effort) {
        uniffi.codex_mobile_client.ReasoningEffort.NONE -> "无"
        uniffi.codex_mobile_client.ReasoningEffort.MINIMAL -> "极低"
        uniffi.codex_mobile_client.ReasoningEffort.LOW -> "低"
        uniffi.codex_mobile_client.ReasoningEffort.MEDIUM -> "中"
        uniffi.codex_mobile_client.ReasoningEffort.HIGH -> "高"
        uniffi.codex_mobile_client.ReasoningEffort.X_HIGH -> "极高"
        uniffi.codex_mobile_client.ReasoningEffort.MAX -> "最高"
    }
