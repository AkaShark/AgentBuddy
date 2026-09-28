package com.akashark.agentbuddy.android.state

import uniffi.codex_mobile_client.AppAskForApproval
import uniffi.codex_mobile_client.AppSandboxMode
import uniffi.codex_mobile_client.AppSandboxPolicy
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ReasoningEffort
import uniffi.codex_mobile_client.ServiceTier
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppFinalizeRealtimeHandoffRequest
import uniffi.codex_mobile_client.AppResolveRealtimeHandoffRequest
import uniffi.codex_mobile_client.generativeUiDynamicToolSpecs

// Handoff action dispatch for [VoiceRuntimeController]: drains the Rust
// `HandoffManager` and runs each action against [AppModel].

internal suspend fun VoiceRuntimeController.processHandoffActions(appModel: AppModel) {
    val hm = handoffManager ?: return
    val actions = hm.uniffiDrainActions()
    for (action in actions) {
        dispatchHandoffAction(appModel, action)
    }
}

private suspend fun VoiceRuntimeController.dispatchHandoffAction(
    appModel: AppModel,
    action: uniffi.codex_mobile_client.HandoffAction,
) {
    when (action) {
        is uniffi.codex_mobile_client.HandoffAction.StartThread -> {
            try {
                val serverIsLocal = appModel.snapshot.value
                    ?.servers
                    ?.firstOrNull { it.serverId == action.targetServerId }
                    ?.isLocal == true
                val key = appModel.startThread(
                    action.targetServerId,
                    AppThreadLaunchConfig(
                        model = null,
                        approvalPolicy = AppAskForApproval.Never,
                        sandboxMode = AppSandboxMode.DANGER_FULL_ACCESS,
                        developerInstructions = null,
                        persistHistory = true,
                    ).toAppStartThreadRequest(
                        cwd = action.cwd,
                        dynamicTools = if (serverIsLocal) generativeUiDynamicToolSpecs() else null,
                    ),
                )
                SavedThreadsStore.add(
                    appModel.appContext,
                    PinnedThreadKey(serverId = key.serverId, threadId = key.threadId),
                )
                handoffManager?.uniffiReportThreadCreated(action.handoffId, action.targetServerId, key.threadId)
            } catch (e: Exception) {
                handoffManager?.uniffiReportThreadFailed(action.handoffId, e.message ?: "Thread creation failed")
            }
        }

        is uniffi.codex_mobile_client.HandoffAction.SendTurn -> {
            try {
                val payload = AppComposerPayload(
                    text = action.transcript,
                    approvalPolicy = AppAskForApproval.Never,
                    sandboxPolicy = AppSandboxPolicy.DangerFullAccess,
                    model = action.config.model,
                    reasoningEffort = reasoningEffortFromWireValue(action.config.effort),
                    serviceTier = if (action.config.fastMode) ServiceTier.FAST else null,
                )
                appModel.startTurn(
                    ThreadKey(serverId = action.targetServerId, threadId = action.threadId),
                    payload,
                )
                handoffManager?.uniffiReportTurnSent(action.handoffId, 0u)
                val handoffKey = ThreadKey(serverId = action.targetServerId, threadId = action.threadId)
                appModel.store.setVoiceHandoffThread(key = handoffKey)
            } catch (e: Exception) {
                handoffManager?.uniffiReportTurnFailed(action.handoffId, e.message ?: "Turn failed")
            }
        }

        is uniffi.codex_mobile_client.HandoffAction.ResolveHandoff -> {
            try {
                appModel.client.resolveRealtimeHandoff(
                    action.voiceThreadKey.serverId,
                    AppResolveRealtimeHandoffRequest(
                        threadId = action.voiceThreadKey.threadId,
                        toolCallOutput = action.text,
                    ),
                )
            } catch (_: Exception) {}
        }

        is uniffi.codex_mobile_client.HandoffAction.FinalizeHandoff -> {
            try {
                appModel.client.finalizeRealtimeHandoff(
                    action.voiceThreadKey.serverId,
                    AppFinalizeRealtimeHandoffRequest(
                        threadId = action.voiceThreadKey.threadId,
                    ),
                )
            } catch (_: Exception) {}
            handoffManager?.uniffiReportFinalized(action.handoffId)
            appModel.store.setVoiceHandoffThread(key = null)
        }

        is uniffi.codex_mobile_client.HandoffAction.Error -> {
            android.util.Log.e("VoiceRuntime", "Handoff error: ${action.message}")
        }

        else -> {}
    }
}

private fun reasoningEffortFromWireValue(value: String?): ReasoningEffort? =
    when (value?.trim()?.lowercase()) {
        "none" -> ReasoningEffort.NONE
        "minimal" -> ReasoningEffort.MINIMAL
        "low" -> ReasoningEffort.LOW
        "medium" -> ReasoningEffort.MEDIUM
        "high" -> ReasoningEffort.HIGH
        "xhigh", "x-high" -> ReasoningEffort.X_HIGH
        "max" -> ReasoningEffort.MAX
        else -> null
    }
