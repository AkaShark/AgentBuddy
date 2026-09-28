package com.akashark.agentbuddy.android.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.common.ModelSelectorPanel
import com.akashark.agentbuddy.android.ui.common.matchesModelSelection
import com.akashark.agentbuddy.android.ui.common.modelPickerDisplayName
import com.akashark.agentbuddy.android.ui.conversation.HeaderOverrides
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import uniffi.codex_mobile_client.ModelInfo

/**
 * Model context chip of the new-task composer: current model and reasoning
 * effort; tapping opens the same [ModelSelectorPanel] the conversation
 * header uses, so both surfaces stay identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeModelChip(
    serverId: String?,
    disabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val launchState by appModel.launchState.snapshot.collectAsState()

    val server = remember(snapshot, serverId) {
        snapshot?.servers?.firstOrNull { it.serverId == serverId }
    }
    val availableModels: List<ModelInfo> = server?.availableModels.orEmpty()

    val selectedId = launchState.selectedModel
        .takeIf { it.isNotBlank() }
        ?: availableModels.firstOrNull { it.isDefault }?.id
        ?: availableModels.firstOrNull()?.id
        ?: ""
    val selectedRuntime = launchState.selectedAgentRuntimeKind
        ?: availableModels.firstOrNull { it.id == selectedId || it.model == selectedId }?.agentRuntimeKind

    val selectedLabel = remember(selectedId, selectedRuntime, availableModels) {
        availableModels.firstOrNull { it.matchesModelSelection(selectedId, selectedRuntime) }?.modelPickerDisplayName()?.ifBlank { selectedId }
            ?: selectedId.ifBlank { "模型" }
    }

    LaunchedEffect(serverId) {
        if (!serverId.isNullOrBlank()) {
            runCatching { appModel.loadConversationMetadataIfNeeded(serverId) }
        }
    }

    var showSheet by remember { mutableStateOf(false) }
    val effortLabel = launchState.reasoningEffort.trim()
    val label = if (effortLabel.isNotEmpty()) "$selectedLabel · $effortLabel" else selectedLabel

    BuddyContextChip(
        text = label,
        onClick = { showSheet = true },
        icon = Icons.Outlined.Memory,
        trailingIcon = Icons.Outlined.KeyboardArrowDown,
        enabled = !disabled,
        modifier = modifier.semantics { contentDescription = "模型：$label" },
    )

    if (showSheet) {
        BuddyBottomSheet(onDismissRequest = { showSheet = false }) {
            // No thread yet: the panel hides the Plan toggle, and the
            // Full-access toggle writes to the shared launch defaults via
            // `updateThreadPermissions(threadKey = null)`.
            ModelSelectorPanel(
                thread = null,
                availableModels = availableModels,
                onToggleMode = null,
                fastMode = HeaderOverrides.pendingFastMode,
                onFastModeChange = { HeaderOverrides.pendingFastMode = it },
            )
        }
    }
}
