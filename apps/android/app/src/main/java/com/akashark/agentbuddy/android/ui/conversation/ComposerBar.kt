package com.akashark.agentbuddy.android.ui.conversation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.akashark.agentbuddy.android.state.AppComposerPayload
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.VoiceTranscriptionManager
import com.akashark.agentbuddy.android.ui.LocalAppModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSearchFilesRequest
import uniffi.codex_mobile_client.AppThreadGoal
import uniffi.codex_mobile_client.AuthStatusRequest
import uniffi.codex_mobile_client.PendingUserInputRequest
import uniffi.codex_mobile_client.ThreadKey

data class ActiveTaskSummary(val progress: String, val label: String)

/**
 * Conversation composer: Mint editor card with explicit idle / running (stop
 * + 「排队」) / stopping / disconnected / creating states, plus slash
 * commands, @file search, attachments, dictation, the full-screen editor and
 * the panels above it (goal, plan, active task, pending question, queue).
 * Turn truth comes from the snapshot ([isTurnActive], [activeTurnId],
 * [isConnected]); only the stopping / creating markers live here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerBar(
    threadKey: ThreadKey,
    collaborationMode: uniffi.codex_mobile_client.AppModeKind,
    activePlanProgress: uniffi.codex_mobile_client.AppPlanProgressSnapshot? = null,
    activeTurnId: String?,
    contextPercent: Int?,
    isTurnActive: Boolean,
    modifier: Modifier = Modifier,
    isConnected: Boolean = true,
    partnerLabel: String? = null,
    activeTaskSummary: ActiveTaskSummary? = null,
    queuedFollowUps: List<uniffi.codex_mobile_client.AppQueuedFollowUpPreview> = emptyList(),
    goal: AppThreadGoal? = null,
    rateLimits: uniffi.codex_mobile_client.RateLimitSnapshot? = null,
    onOpenCollaborationModePicker: (() -> Unit)? = null,
    onToggleModelSelector: (() -> Unit)? = null,
    onNavigateToSessions: (() -> Unit)? = null,
    onShowDirectoryPicker: (() -> Unit)? = null,
    onShowRenameDialog: ((String?) -> Unit)? = null,
    onShowPermissionsSheet: (() -> Unit)? = null,
    onShowExperimentalSheet: (() -> Unit)? = null,
    onShowSkillsSheet: (() -> Unit)? = null,
    onSlashError: ((String) -> Unit)? = null,
    pendingUserInput: PendingUserInputRequest? = null,
    onDismissPendingUserInput: (() -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val composerPrefillRequest by appModel.composerPrefillRequest.collectAsState()
    // The live draft is hydrated from AppModel's per-thread draft so it
    // survives recomposition / view teardown; edits write back below.
    var textFieldValue by remember(threadKey) {
        val saved = appModel.composerDraft(threadKey).text
        mutableStateOf(TextFieldValue(saved, selection = TextRange(saved.length)))
    }
    val text = textFieldValue.text
    var attachedImage by remember(threadKey) { mutableStateOf(appModel.composerDraft(threadKey).attachment) }
    var attachedFiles by remember(threadKey) { mutableStateOf(appModel.composerDraft(threadKey).fileAttachments) }
    LaunchedEffect(threadKey, text, attachedImage, attachedFiles) {
        appModel.setComposerDraft(threadKey, AppModel.ComposerDraft(text, attachedImage, attachedFiles))
    }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showExpanded by remember { mutableStateOf(false) }
    val inlineFocusRequester = remember { FocusRequester() }
    val transcriptionManager = remember { VoiceTranscriptionManager() }
    val isRecording by transcriptionManager.isRecording.collectAsState()
    val isTranscribing by transcriptionManager.isTranscribing.collectAsState()
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) transcriptionManager.startRecording(context)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        attachedImage = readAttachmentFromUri(context, uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        when (val picked = readPickedComposerAttachment(context, uri)) {
            is PickedComposerAttachment.Image -> attachedImage = picked.attachment
            is PickedComposerAttachment.File -> if (picked.attachment !in attachedFiles) attachedFiles = attachedFiles + picked.attachment
            null -> Unit
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        attachedImage = prepareBitmapAttachment(bitmap)
    }

    // Slash command popup
    val slashQuery by remember { derivedStateOf { if (text.startsWith("/")) text.removePrefix("/").lowercase() else null } }
    val filteredCommands by remember { derivedStateOf { slashQuery?.let(::filterSlashCommands).orEmpty() } }
    var showSlashMenu by remember { mutableStateOf(false) }
    LaunchedEffect(slashQuery) { showSlashMenu = slashQuery != null && filteredCommands.isNotEmpty() }

    // @file search popup (140ms debounce, top 8 under the thread cwd)
    var fileSearchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFileMenu by remember { mutableStateOf(false) }
    var fileSearchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(text) {
        val atIdx = text.lastIndexOf('@')
        if (atIdx >= 0 && atIdx < text.length - 1 && !text.substring(atIdx).contains(' ')) {
            val query = text.substring(atIdx + 1)
            fileSearchJob?.cancel()
            fileSearchJob = scope.launch {
                delay(140)
                try {
                    val cwd = appModel.snapshot.value?.threads?.find { it.key == threadKey }?.info?.cwd ?: "~"
                    val results = appModel.client.searchFiles(
                        threadKey.serverId,
                        AppSearchFilesRequest(query = query, roots = listOf(cwd), cancellationToken = null),
                    )
                    fileSearchResults = results.map { it.path }.take(8)
                    showFileMenu = fileSearchResults.isNotEmpty()
                } catch (_: Exception) {
                    showFileMenu = false
                }
            }
        } else {
            showFileMenu = false
        }
    }

    // Pending question answers, keyed by request id so they never leak into the next request.
    var userInputAnswers by remember(threadKey, pendingUserInput?.id) { mutableStateOf(mapOf<String, String>()) }

    // Only consume edit-message prefill for the intended thread.
    LaunchedEffect(composerPrefillRequest?.requestId, threadKey) {
        val prefill = composerPrefillRequest ?: return@LaunchedEffect
        if (prefill.threadKey != threadKey) return@LaunchedEffect
        textFieldValue = TextFieldValue(text = prefill.text, selection = TextRange(prefill.text.length))
        attachedImage = null
        attachedFiles = emptyList()
        appModel.clearComposerPrefill(prefill.requestId)
    }

    // Stopping / creating: UI-only markers, reset from snapshot truth.
    val turnState = remember(threadKey) { ComposerTurnState() }
    val normalizedTurnId = activeTurnId?.trim()?.takeIf { it.isNotEmpty() }
    val stoppingTurnId = composerStoppingTurn(turnState.stopRequestedTurnId, normalizedTurnId, isTurnActive, isConnected)
    LaunchedEffect(stoppingTurnId) { if (stoppingTurnId == null) turnState.stopRequestedTurnId = null }

    val hasContent = text.isNotBlank() || attachedImage != null || attachedFiles.isNotEmpty()
    val controls = ComposerControlsState(
        hasContent = hasContent,
        isConnected = isConnected,
        isTurnActive = isTurnActive,
        isStopping = stoppingTurnId != null,
        isCreating = turnState.isCreating,
        isVoiceBusy = isRecording || isTranscribing,
        isSlashCommand = parseSlashCommandInvocation(text) != null,
    )

    fun clearComposer() {
        textFieldValue = TextFieldValue("")
        attachedImage = null
        attachedFiles = emptyList()
        appModel.clearComposerDraft(threadKey)
    }

    fun dispatchSlashCommand(commandName: String, args: String?): Boolean =
        dispatchComposerSlashCommand(
            commandName = commandName,
            args = args,
            appModel = appModel,
            threadKey = threadKey,
            scope = scope,
            onOpenCollaborationModePicker = onOpenCollaborationModePicker,
            onToggleModelSelector = onToggleModelSelector,
            onNavigateToSessions = onNavigateToSessions,
            onShowDirectoryPicker = onShowDirectoryPicker,
            onShowRenameDialog = onShowRenameDialog,
            onShowPermissionsSheet = onShowPermissionsSheet,
            onShowExperimentalSheet = onShowExperimentalSheet,
            onShowSkillsSheet = onShowSkillsSheet,
            onSlashError = onSlashError,
        )

    fun startTurn(draft: AppModel.ComposerDraft, prepared: AppComposerPayload) {
        startComposerTurn(
            appModel = appModel,
            threadKey = threadKey,
            state = turnState,
            payload = prepared,
            sentDraft = draft,
            currentDraft = { AppModel.ComposerDraft(textFieldValue.text, attachedImage, attachedFiles) },
            restoreDraft = { restored ->
                textFieldValue = TextFieldValue(restored.text, selection = TextRange(restored.text.length))
                attachedImage = restored.attachment
                attachedFiles = restored.fileAttachments
            },
        )
    }

    // Single send path for the send button and the expanded editor. Offline:
    // keep the draft, attachments and any pending question (slash commands
    // are local and still run).
    val sendCurrent: () -> Unit = {
        val invocation = parseSlashCommandInvocation(text)
        val gate = composerSendGate(
            isSlashCommand = invocation != null,
            hasContent = hasContent,
            isConnected = isConnected,
            isCreating = turnState.isCreating,
            isVoiceBusy = controls.isVoiceBusy,
        )
        when (gate) {
            ComposerSendGate.SLASH_COMMAND -> {
                if (pendingUserInput != null) onDismissPendingUserInput?.invoke()
                if (invocation != null && dispatchSlashCommand(invocation.command.name, invocation.args)) clearComposer()
            }
            ComposerSendGate.SEND -> {
                if (pendingUserInput != null) onDismissPendingUserInput?.invoke()
                val draft = AppModel.ComposerDraft(text, attachedImage, attachedFiles)
                val payload = composerTurnPayload(appModel, threadKey, text, attachedImage, attachedFiles)
                clearComposer()
                startTurn(draft = draft, prepared = payload)
            }
            ComposerSendGate.NOTHING_TO_SEND,
            ComposerSendGate.BLOCKED_DISCONNECTED,
            ComposerSendGate.BLOCKED_BUSY -> Unit
        }
    }
    val retrySend: () -> Unit = retry@{
        val failed = turnState.failedSend ?: return@retry
        if (failed.restoredToDraft && hasContent) sendCurrent() else startTurn(draft = failed.draft, prepared = failed.payload)
    }

    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        ComposerNotices(
            isConnected = isConnected,
            sendError = turnState.failedSend?.message,
            onRetrySend = if (turnState.isCreating) null else retrySend,
            onDismissSendError = { turnState.failedSend = null },
            stopError = turnState.stopError,
            onDismissStopError = { turnState.stopError = null },
        )
        goal?.let { current ->
            val goalActions = remember(current.threadId, current.status) {
                composerGoalCardActions(current, appModel, threadKey, scope, onSlashError)
            }
            GoalPanel(current, goalActions)
        }
        activePlanProgress?.let { PlanProgressPanel(progress = it) }
        activeTaskSummary?.let { ComposerActiveTaskSummary(it) }
        if (pendingUserInput != null) {
            ComposerPendingInputHost(
                appModel = appModel,
                scope = scope,
                request = pendingUserInput,
                answers = userInputAnswers,
                onAnswersChange = { userInputAnswers = it },
                onDismiss = onDismissPendingUserInput,
            )
        }
        if (queuedFollowUps.isNotEmpty()) {
            QueuedFollowUpsPreviewPanel(
                previews = queuedFollowUps,
                onSteer = { preview ->
                    scope.launch {
                        runCatching { appModel.store.steerQueuedFollowUp(threadKey, preview.id) }
                            .onFailure { onSlashError?.invoke(it.message ?: "干预失败") }
                    }
                },
                onDelete = { preview ->
                    scope.launch {
                        runCatching { appModel.store.deleteQueuedFollowUp(threadKey, preview.id) }
                            .onFailure { onSlashError?.invoke(it.message ?: "移除排队消息失败") }
                    }
                },
            )
        }
        ComposerAttachmentPreviews(
            attachedImage = attachedImage,
            attachedFiles = attachedFiles,
            onRemoveImage = { attachedImage = null },
            onRemoveFile = { file -> attachedFiles = attachedFiles.filterNot { it == file } },
        )
        ComposerEditorCard(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = { textFieldValue = it },
            controls = controls,
            onSend = sendCurrent,
            onStop = { interruptComposerTurn(appModel, threadKey, turnState, normalizedTurnId, scope) },
            onAttach = { showAttachMenu = true },
            onShowExpanded = { showExpanded = true },
            partnerLabel = partnerLabel,
            isFastMode = HeaderOverrides.pendingFastMode,
            onOpenModelPanel = onToggleModelSelector,
            focusRequester = inlineFocusRequester,
            voiceControl = {
                ComposerVoiceControl(
                    appModel = appModel,
                    threadKey = threadKey,
                    scope = scope,
                    isRecording = isRecording,
                    isTranscribing = isTranscribing,
                    hasContent = hasContent,
                    onStartDictation = {
                        if (transcriptionManager.hasMicPermission(context)) {
                            transcriptionManager.startRecording(context)
                        } else {
                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopDictation = {
                        scope.launch {
                            val auth = runCatching {
                                appModel.client.authStatus(
                                    threadKey.serverId,
                                    AuthStatusRequest(includeToken = true, refreshToken = false),
                                )
                            }.getOrNull()
                            transcriptionManager.stopAndTranscribe(authMethod = auth?.authMethod, authToken = auth?.authToken)
                                ?.let { textFieldValue = insertComposerTranscript(textFieldValue, it) }
                        }
                    },
                )
            },
            popups = {
                ComposerSlashCommandMenu(
                    showSlashMenu = showSlashMenu,
                    filteredCommands = filteredCommands,
                    onDismissSlashMenu = { showSlashMenu = false },
                    onSlashCommandSelected = { cmd -> if (dispatchSlashCommand(cmd.name, args = null)) clearComposer() },
                )
                ComposerFileSearchMenu(
                    showFileMenu = showFileMenu,
                    fileSearchResults = fileSearchResults,
                    text = text,
                    onDismissFileMenu = { showFileMenu = false },
                    onTextFieldValueChange = { textFieldValue = it },
                )
            },
        )
        if (showExpanded) {
            ComposerExpandedDialog(
                text = text,
                onTextChange = { textFieldValue = TextFieldValue(text = it, selection = TextRange(it.length)) },
                onSend = sendCurrent,
                onDismiss = {
                    showExpanded = false
                    // Restore inline focus after the dialog animates away.
                    scope.launch {
                        delay(80)
                        runCatching { inlineFocusRequester.requestFocus() }
                    }
                },
                canSend = controls.canSend,
                queues = controls.sendAction == ComposerSendAction.QUEUE,
                placeholder = COMPOSER_PLACEHOLDER,
                notice = if (isConnected) null else COMPOSER_DISCONNECTED_MESSAGE,
            )
        }
        ComposerIndicatorsRow(contextPercent = contextPercent, rateLimits = rateLimits)
    }

    ComposerAttachSheet(
        showAttachMenu = showAttachMenu,
        onDismiss = { showAttachMenu = false },
        onAttachedImageChange = { attachedImage = it },
        photoPicker = photoPicker,
        filePicker = filePicker,
        cameraLauncher = cameraLauncher,
    )
}
