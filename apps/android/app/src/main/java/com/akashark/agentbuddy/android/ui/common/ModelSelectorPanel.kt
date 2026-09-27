package com.akashark.agentbuddy.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.state.ampReasoningEffortLocked
import com.akashark.agentbuddy.android.ui.LocalAppModel
import uniffi.codex_mobile_client.AppModeKind
import uniffi.codex_mobile_client.AppThreadPermissionPreset
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.ModelInfo
import uniffi.codex_mobile_client.threadPermissionPreset

/**
 * Reusable model/reasoning/plan/permissions/fast-mode panel shared by the
 * conversation header (scoped to an existing thread) and the home composer
 * chip (pre-thread, `thread == null`). Mirrors iOS
 * `InlineModelSelectorView` + `ModelPickerComponents`.
 *
 * This wrapper reads `AppLaunchState` and the thread snapshot and renders
 * [ModelSelectorPanelContent], which only takes plain state and callbacks.
 *
 * When `thread` is null:
 *   - Permission toggle operates on `AppLaunchState` defaults (threadKey=null)
 *     so the choice carries through the next `startThread` call.
 *   - Plan toggle is hidden — the collaboration mode is a per-thread field
 *     with no pre-thread equivalent on Android.
 *
 * `onToggleMode` is invoked for Plan switch changes; pass null (or it will be
 * ignored because the switch is hidden) when there's no thread.
 */
@Composable
fun ModelSelectorPanel(
    thread: AppThreadSnapshot?,
    availableModels: List<ModelInfo>,
    onToggleMode: ((AppModeKind) -> Unit)? = null,
    fastMode: Boolean,
    onFastModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
) {
    val appModel = LocalAppModel.current
    val launchState by appModel.launchState.snapshot.collectAsState()
    val visibleModels = remember(availableModels) {
        availableModels.filter { it.isVisibleModelOption() }
    }
    val selectedModel = launchState.selectedModel
        .takeIf { it.isNotBlank() }
        ?: thread?.model
        ?: visibleModels.firstOrNull { it.isDefault }?.id
        ?: visibleModels.firstOrNull()?.id
    val selectedRuntime = launchState.selectedAgentRuntimeKind
        ?: thread?.agentRuntimeKind
        ?: visibleModels.firstOrNull { it.id == selectedModel || it.model == selectedModel }?.agentRuntimeKind
    val selectedRuntimeSupportsPermissionOverrides =
        selectedRuntime?.supportsThreadPermissionOverrides ?: true
    val selectedModelDefinition by remember(selectedModel, selectedRuntime, visibleModels) {
        derivedStateOf {
            visibleModels.firstOrNull { it.matchesModelSelection(selectedModel, selectedRuntime) }
                ?: visibleModels.firstOrNull { it.isDefault }
                ?: visibleModels.firstOrNull()
        }
    }
    val selectedModelIsAmp = selectedModelDefinition?.agentRuntimeKind == "amp"
    val ampEffortLocked = selectedModelIsAmp && thread?.ampReasoningEffortLocked == true
    val supportedEfforts = remember(selectedModelDefinition, ampEffortLocked) {
        if (ampEffortLocked) {
            emptyList()
        } else {
            selectedModelDefinition?.supportedReasoningEfforts ?: emptyList()
        }
    }
    val selectedEffort = if (supportedEfforts.isEmpty()) {
        null
    } else {
        launchState.reasoningEffort
            .takeIf { pending ->
                pending.isNotBlank() &&
                    supportedEfforts.any { effortLabel(it.reasoningEffort) == pending }
            }
            ?: thread?.reasoningEffort
                ?.takeIf { current ->
                    supportedEfforts.any { effortLabel(it.reasoningEffort) == current }
                }
            ?: selectedModelDefinition?.defaultReasoningEffort?.let(::effortLabel)
    }

    LaunchedEffect(launchState.reasoningEffort, selectedModelDefinition, supportedEfforts, ampEffortLocked) {
        val pendingEffort = launchState.reasoningEffort.trim()
        val defaultEffort = selectedModelDefinition?.defaultReasoningEffort
        if (pendingEffort.isEmpty()) {
            return@LaunchedEffect
        }
        if (ampEffortLocked) {
            appModel.launchState.updateReasoningEffort(null)
            return@LaunchedEffect
        }
        if (supportedEfforts.isEmpty()) {
            appModel.launchState.updateReasoningEffort(null)
            return@LaunchedEffect
        }
        if (defaultEffort == null) {
            return@LaunchedEffect
        }
        if (supportedEfforts.none { effortLabel(it.reasoningEffort) == pendingEffort }) {
            appModel.launchState.updateReasoningEffort(effortLabel(defaultEffort))
        }
    }

    val threadKey = thread?.key
    val isFullAccess = if (selectedRuntimeSupportsPermissionOverrides) {
        val approval = appModel.launchState.approvalPolicyValue(threadKey)
            ?: thread?.effectiveApprovalPolicy
        val sandbox = appModel.launchState.turnSandboxPolicy(threadKey)
            ?: thread?.effectiveSandboxPolicy
        val preset = if (approval != null && sandbox != null) {
            threadPermissionPreset(approval, sandbox)
        } else {
            null
        }
        preset == AppThreadPermissionPreset.FULL_ACCESS
    } else {
        null
    }
    val planMode = if (thread != null && onToggleMode != null) {
        thread.collaborationMode == AppModeKind.PLAN
    } else {
        null
    }

    ModelSelectorPanelContent(
        state = ModelPanelState(
            models = visibleModels,
            selectedModel = selectedModel,
            selectedRuntime = selectedRuntime,
            efforts = supportedEfforts,
            selectedEffort = selectedEffort,
            effortLocked = ampEffortLocked,
            planMode = planMode,
            fullAccess = isFullAccess,
            fastMode = fastMode,
        ),
        actions = ModelPanelActions(
            onSelectModel = { model ->
                appModel.launchState.updateSelectedModel(
                    model.id,
                    agentRuntimeKind = model.agentRuntimeKind,
                )
                appModel.launchState.updateReasoningEffort(
                    if (ampEffortLocked && model.agentRuntimeKind == "amp") {
                        null
                    } else {
                        model.defaultReasoningEffortSelection()
                    },
                )
            },
            onSelectEffort = { effort -> appModel.launchState.updateReasoningEffort(effort) },
            onPlanModeChange = { enabled ->
                onToggleMode?.invoke(if (enabled) AppModeKind.PLAN else AppModeKind.DEFAULT)
            },
            onFullAccessChange = { enabled ->
                if (enabled) {
                    appModel.launchState.updateThreadPermissions(
                        threadKey,
                        approvalPolicy = "never",
                        sandboxMode = "danger-full-access",
                    )
                } else {
                    appModel.launchState.updateThreadPermissions(
                        threadKey,
                        approvalPolicy = "on-request",
                        sandboxMode = "workspace-write",
                    )
                }
            },
            onFastModeChange = onFastModeChange,
        ),
        modifier = modifier,
        showBackground = showBackground,
    )
}
