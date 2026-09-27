package com.akashark.agentbuddy.android.ui.conversation

import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.ComposerImageAttachment
import com.akashark.agentbuddy.android.state.ComposerFileAttachment
import com.akashark.agentbuddy.android.state.AppComposerPayload
import com.akashark.agentbuddy.android.state.ampReasoningEffortLocked
import kotlinx.coroutines.CoroutineScope
import uniffi.codex_mobile_client.ReasoningEffort
import uniffi.codex_mobile_client.ServiceTier
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey

/** Runs a slash command for [ComposerBar]; returns false when [commandName] is unknown. */
internal fun dispatchComposerSlashCommand(
    commandName: String,
    args: String?,
    appModel: AppModel,
    threadKey: ThreadKey,
    scope: CoroutineScope,
    onOpenCollaborationModePicker: (() -> Unit)?,
    onToggleModelSelector: (() -> Unit)?,
    onNavigateToSessions: (() -> Unit)?,
    onShowDirectoryPicker: (() -> Unit)?,
    onShowRenameDialog: ((String?) -> Unit)?,
    onShowPermissionsSheet: (() -> Unit)?,
    onShowExperimentalSheet: (() -> Unit)?,
    onShowSkillsSheet: (() -> Unit)?,
    onSlashError: ((String) -> Unit)?,
): Boolean {
    when (commandName) {
        "plan" -> onOpenCollaborationModePicker?.invoke()
        "model" -> onToggleModelSelector?.invoke()
        "new" -> onShowDirectoryPicker?.invoke()
        "resume" -> onNavigateToSessions?.invoke()
        "rename" -> onShowRenameDialog?.invoke(args)
        "skills" -> onShowSkillsSheet?.invoke()
        "permissions" -> onShowPermissionsSheet?.invoke()
        "experimental" -> onShowExperimentalSheet?.invoke()
        "goal" -> scope.launch {
            try {
                handleComposerGoalCommand(appModel, threadKey, args, onSlashError)
            } catch (e: Exception) {
                onSlashError?.invoke(e.message ?: "更新目标失败")
            }
        }
        "fork" -> scope.launch {
            try {
                val cwd = appModel.snapshot.value?.threads?.find { it.key == threadKey }?.info?.cwd
                val newKey = appModel.client.forkThread(
                    threadKey.serverId,
                    appModel.launchState.threadForkRequest(
                        sourceThreadId = threadKey.threadId,
                        cwdOverride = cwd,
                        modelOverride = appModel.launchState.snapshot.value.selectedModel.trim().ifEmpty { null },
                        threadKey = threadKey,
                    ),
                )
                appModel.store.setActiveThread(newKey)
                appModel.refreshThreadSnapshot(newKey)
            } catch (e: Exception) {
                onSlashError?.invoke(e.message ?: "分叉对话失败")
            }
        }
        "review" -> scope.launch {
            try {
                appModel.client.startReview(
                    threadKey.serverId,
                    uniffi.codex_mobile_client.AppStartReviewRequest(
                        threadId = threadKey.threadId,
                        target = uniffi.codex_mobile_client.AppReviewTarget.UncommittedChanges,
                        delivery = null,
                    ),
                )
            } catch (e: Exception) {
                onSlashError?.invoke(e.message ?: "启动评审失败")
            }
        }
        else -> return false
    }
    return true
}

internal fun composerTurnPayload(
    appModel: AppModel,
    threadKey: ThreadKey,
    text: String,
    attachmentToSend: ComposerImageAttachment?,
    filesToSend: List<ComposerFileAttachment>,
): AppComposerPayload {
    val launchState = appModel.launchState.snapshot.value
    val pendingModel = launchState.selectedModel.trim().ifEmpty { null }
    val thread = appModel.snapshot.value?.threads?.find { it.key == threadKey }
    val effort = if (thread?.ampReasoningEffortLocked == true) {
        null
    } else {
        launchState.reasoningEffort.trim().ifEmpty { null }
            ?.let(::reasoningEffortFromServerValue)
    }
    val tier = if (HeaderOverrides.pendingFastMode) ServiceTier.FAST else null
    return AppComposerPayload(
        text = text.trim(),
        additionalInputs = listOfNotNull(attachmentToSend?.toUserInput()),
        fileAttachments = filesToSend,
        approvalPolicy = appModel.launchState.approvalPolicyValue(threadKey),
        sandboxPolicy = appModel.launchState.turnSandboxPolicy(threadKey),
        model = pendingModel,
        reasoningEffort = effort,
        serviceTier = tier,
    )
}

private fun reasoningEffortFromServerValue(value: String): ReasoningEffort? =
    when (value.trim().lowercase()) {
        "none" -> ReasoningEffort.NONE
        "minimal" -> ReasoningEffort.MINIMAL
        "low" -> ReasoningEffort.LOW
        "medium" -> ReasoningEffort.MEDIUM
        "high" -> ReasoningEffort.HIGH
        "xhigh" -> ReasoningEffort.X_HIGH
        "max" -> ReasoningEffort.MAX
        else -> null
    }
