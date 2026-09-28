package com.akashark.agentbuddy.android.ui.homeshell.newtask

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.home.HomeComposerBar
import com.akashark.agentbuddy.android.ui.home.HomeComposerDraft
import com.akashark.agentbuddy.android.ui.home.HomeModelChip
import com.akashark.agentbuddy.android.ui.home.ProjectChip
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.ThreadKey

/**
 * New-task sheet on the home shell. Creating the task keeps today's flow
 * (requires a project; `startThread`, record the directory, `startTurn`,
 * pin) and stays on home. The sheet cannot be dismissed while the task is
 * being created, and a failed creation keeps the draft.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTaskSheet(
    project: AppProject?,
    hosts: List<NewTaskHostOption>,
    selectedServerId: String?,
    draft: HomeComposerDraft,
    onSelectServer: (String) -> Unit,
    onOpenProjectPicker: () -> Unit,
    onThreadCreated: (ThreadKey) -> Unit,
    onLoginRequired: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var isSubmitting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !isSubmitting },
    )
    val scope = rememberCoroutineScope()
    val activeServerId = project?.serverId ?: selectedServerId
    val canLaunch = hosts.any { it.serverId == activeServerId }
    val close: () -> Unit = {
        if (!isSubmitting) scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    BuddyBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
    ) {
        NewTaskSheetContent(
            onCancel = close,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            chips = {
                NewTaskHostChip(hosts = hosts, selectedServerId = activeServerId, onSelect = onSelectServer)
                ProjectChip(project = project, disabled = hosts.isEmpty(), onTap = onOpenProjectPicker)
                HomeModelChip(serverId = activeServerId, disabled = !canLaunch)
            },
            composer = {
                HomeComposerBar(
                    project = project,
                    draft = draft,
                    onThreadCreated = { key ->
                        onThreadCreated(key)
                        onDismiss()
                    },
                    onLoginRequired = onLoginRequired,
                    onSubmittingChange = { isSubmitting = it },
                )
            },
            onChooseProject = if (project == null && hosts.isNotEmpty()) onOpenProjectPicker else null,
        )
    }
}
