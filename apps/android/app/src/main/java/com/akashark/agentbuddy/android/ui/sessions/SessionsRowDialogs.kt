package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.VoiceRuntimeController
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.discovery.MintAlertDialog
import com.akashark.agentbuddy.android.ui.discovery.MintTextField
import uniffi.codex_mobile_client.AppArchiveThreadRequest
import uniffi.codex_mobile_client.AppRenameThreadRequest
import uniffi.codex_mobile_client.AppSessionSummary

/** 「重命名任务」 with the current title prefilled. */
@Composable
internal fun SessionRenameDialog(
    summary: AppSessionSummary,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newName by remember(summary.key) { mutableStateOf(summary.title ?: "") }
    MintAlertDialog(
        onDismissRequest = onDismiss,
        title = "重命名任务",
        confirmTitle = "重命名",
        onConfirm = { onConfirm(newName) },
    ) {
        MintTextField(
            value = newName,
            onValueChange = { newName = it },
            placeholder = "新的任务标题",
            label = "名称",
        )
    }
}

/** 「归档任务？」 confirmation; archiving removes the task from the list. */
@Composable
internal fun SessionArchiveDialog(
    summary: AppSessionSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MintAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Outlined.Archive,
        iconTint = AgentBuddyTheme.danger,
        title = "归档任务？",
        message = "「${summary.displayTitle}」会从任务列表中移除。",
        confirmTitle = "归档",
        confirmKind = BuddyButtonKind.DESTRUCTIVE,
        onConfirm = onConfirm,
    )
}

/** Renames the thread on its server, then refreshes that thread's snapshot. */
internal suspend fun renameSession(appModel: AppModel, summary: AppSessionSummary, newName: String) {
    try {
        appModel.client.renameThread(
            summary.key.serverId,
            AppRenameThreadRequest(
                threadId = summary.key.threadId,
                name = newName,
            ),
        )
        appModel.refreshThreadSnapshot(summary.key)
    } catch (_: Exception) {}
}

/** Stops voice on the thread, clears it if active, archives it and refreshes. */
internal suspend fun archiveSession(
    appModel: AppModel,
    voiceController: VoiceRuntimeController,
    summary: AppSessionSummary,
) {
    try {
        voiceController.stopVoiceSessionIfActive(appModel, summary.key)
        voiceController.clearPinnedLocalVoiceThreadIfMatches(appModel, summary.key)
        if (appModel.snapshot.value?.activeThread == summary.key) {
            appModel.store.setActiveThread(null)
        }
        appModel.client.archiveThread(
            summary.key.serverId,
            AppArchiveThreadRequest(threadId = summary.key.threadId),
        )
        appModel.refreshSnapshot()
    } catch (_: Exception) {}
}
