package com.akashark.agentbuddy.android.ui.conversation

import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.ComposerImageAttachment
import com.akashark.agentbuddy.android.state.ComposerFileAttachment
import com.akashark.agentbuddy.android.state.AppComposerPayload
import com.akashark.agentbuddy.android.state.ampReasoningEffortLocked
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import uniffi.codex_mobile_client.AppInterruptTurnRequest
import uniffi.codex_mobile_client.ReasoningEffort
import uniffi.codex_mobile_client.ServiceTier
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey

/**
 * Sends run here instead of the composable's scope so leaving the
 * conversation never cancels a `startTurn` halfway (the draft was already
 * cleared optimistically).
 */
internal object ComposerSendScope : CoroutineScope by MainScope()

/** A send that failed: what was sent, and whether it went back into the composer. */
internal data class ComposerFailedSend(
    val payload: AppComposerPayload,
    val draft: AppModel.ComposerDraft,
    val message: String,
    val restoredToDraft: Boolean,
)

/** UI-only turn markers of one composer (creating / stopping / their errors). */
internal class ComposerTurnState {
    /** `startTurn` in flight: the send control shows progress and blocks double submits. */
    var isCreating by mutableStateOf(false)
    var failedSend by mutableStateOf<ComposerFailedSend?>(null)

    /** Turn id a stop was requested for; see [composerStoppingTurn]. */
    var stopRequestedTurnId by mutableStateOf<String?>(null)
    var stopError by mutableStateOf<String?>(null)
}

/**
 * Starts (or, during a running turn, queues) [payload]. On failure the draft
 * comes back only into an empty composer ([shouldRestoreFailedDraft]) and the
 * error stays visible with a retry.
 */
internal fun startComposerTurn(
    appModel: AppModel,
    threadKey: ThreadKey,
    state: ComposerTurnState,
    payload: AppComposerPayload,
    sentDraft: AppModel.ComposerDraft,
    currentDraft: () -> AppModel.ComposerDraft,
    restoreDraft: (AppModel.ComposerDraft) -> Unit,
) {
    if (state.isCreating) return
    state.isCreating = true
    state.failedSend = null
    ComposerSendScope.launch {
        try {
            appModel.startTurn(threadKey, payload)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LLog.w("ComposerBar", "start turn failed", fields = mapOf("error" to error.message))
            val live = currentDraft()
            val stored = appModel.composerDraft(threadKey)
            val restore = shouldRestoreFailedDraft(live.text, live.attachment != null || live.fileAttachments.isNotEmpty()) &&
                stored.isEmpty
            if (restore) {
                restoreDraft(sentDraft)
                appModel.setComposerDraft(threadKey, sentDraft)
            }
            state.failedSend = ComposerFailedSend(payload, sentDraft, responseSubmissionErrorMessage(error), restore)
        } finally {
            state.isCreating = false
        }
    }
}

/** Asks the host to stop [turnId]; a refusal resets 「正在停止…」 and is shown. */
internal fun interruptComposerTurn(
    appModel: AppModel,
    threadKey: ThreadKey,
    state: ComposerTurnState,
    turnId: String?,
    scope: CoroutineScope,
) {
    if (state.stopRequestedTurnId != null) return
    if (turnId == null) {
        state.stopError = "暂时无法停止：还没拿到当前这一轮的状态，请稍后再试。"
        return
    }
    state.stopRequestedTurnId = turnId
    state.stopError = null
    scope.launch {
        try {
            appModel.client.interruptTurn(
                threadKey.serverId,
                AppInterruptTurnRequest(threadId = threadKey.threadId, turnId = turnId),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LLog.w("ComposerBar", "interrupt turn failed", fields = mapOf("error" to error.message))
            if (state.stopRequestedTurnId == turnId) state.stopRequestedTurnId = null
            state.stopError = "没能停止任务：" + responseSubmissionErrorMessage(error)
        }
    }
}

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
