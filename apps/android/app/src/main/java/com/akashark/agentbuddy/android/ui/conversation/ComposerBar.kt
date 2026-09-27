package com.akashark.agentbuddy.android.ui.conversation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.VoiceTranscriptionManager
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import uniffi.codex_mobile_client.AppSearchFilesRequest
import uniffi.codex_mobile_client.PendingUserInputAnswer
import uniffi.codex_mobile_client.PendingUserInputRequest
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppThreadGoal

data class ActiveTaskSummary(val progress: String, val label: String)

/**
 * Bottom composer bar with text input, send, voice, slash commands,
 * @file search, and inline pending user input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerBar(
    threadKey: ThreadKey,
    collaborationMode: uniffi.codex_mobile_client.AppModeKind,
    activePlanProgress: uniffi.codex_mobile_client.AppPlanProgressSnapshot? = null,
    activeTurnId: String?,
    contextPercent: Int?,
    isThinking: Boolean,
    activeTaskSummary: ActiveTaskSummary? = null,
    queuedFollowUps: List<uniffi.codex_mobile_client.AppQueuedFollowUpPreview> = emptyList(),
    goal: AppThreadGoal? = null,
    rateLimits: uniffi.codex_mobile_client.RateLimitSnapshot? = null,
    showCollaborationModeChip: Boolean = true,
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
    // Hydrate the live composer state from `AppModel`'s per-thread draft so
    // it survives ComposerBar recomposition / view-tree teardown when the
    // user backgrounds the app. `remember(threadKey)` re-initializes when
    // navigating to a different thread; subsequent edits write back.
    var textFieldValue by remember(threadKey) {
        val saved = appModel.composerDraft(threadKey).text
        mutableStateOf(TextFieldValue(saved, selection = TextRange(saved.length)))
    }
    val text = textFieldValue.text
    var attachedImage by remember(threadKey) {
        mutableStateOf(appModel.composerDraft(threadKey).attachment)
    }
    var attachedFiles by remember(threadKey) {
        mutableStateOf(appModel.composerDraft(threadKey).fileAttachments)
    }
    LaunchedEffect(threadKey, text, attachedImage, attachedFiles) {
        appModel.setComposerDraft(
            threadKey,
            AppModel.ComposerDraft(
                text = text,
                attachment = attachedImage,
                fileAttachments = attachedFiles,
            ),
        )
    }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showExpanded by remember { mutableStateOf(false) }
    val inlineFocusRequester = remember { FocusRequester() }
    val transcriptionManager = remember { VoiceTranscriptionManager() }
    val isRecording by transcriptionManager.isRecording.collectAsState()
    val isTranscribing by transcriptionManager.isTranscribing.collectAsState()
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
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
            is PickedComposerAttachment.File -> {
                if (picked.attachment !in attachedFiles) {
                    attachedFiles = attachedFiles + picked.attachment
                }
            }
            null -> Unit
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        attachedImage = prepareBitmapAttachment(bitmap)
    }

    // Slash command state
    val slashQuery by remember {
        derivedStateOf {
            if (text.startsWith("/")) text.removePrefix("/").lowercase() else null
        }
    }
    val filteredCommands by remember {
        derivedStateOf {
            val q = slashQuery ?: return@derivedStateOf emptyList()
            filterSlashCommands(q)
        }
    }
    var showSlashMenu by remember { mutableStateOf(false) }
    LaunchedEffect(slashQuery) { showSlashMenu = slashQuery != null && filteredCommands.isNotEmpty() }

    // @file search state
    var fileSearchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFileMenu by remember { mutableStateOf(false) }
    var fileSearchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(text) {
        val atIdx = text.lastIndexOf('@')
        if (atIdx >= 0 && atIdx < text.length - 1 && !text.substring(atIdx).contains(' ')) {
            val query = text.substring(atIdx + 1)
            fileSearchJob?.cancel()
            fileSearchJob = scope.launch {
                delay(140) // debounce
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

    // Pending user input answers
    var userInputAnswers by remember { mutableStateOf(mapOf<String, String>()) }
    var pendingUserInputSubmitError by remember(pendingUserInput?.id) { mutableStateOf<String?>(null) }
    var isSubmittingPendingUserInput by remember(pendingUserInput?.id) { mutableStateOf(false) }

    // Only consume edit-message prefill for the intended thread.
    LaunchedEffect(composerPrefillRequest?.requestId, threadKey) {
        val prefill = composerPrefillRequest ?: return@LaunchedEffect
        if (prefill.threadKey != threadKey) return@LaunchedEffect
        textFieldValue = TextFieldValue(
            text = prefill.text,
            selection = TextRange(prefill.text.length),
        )
        attachedImage = null
        attachedFiles = emptyList()
        appModel.clearComposerPrefill(prefill.requestId)
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

    // Single send path used by both the inline send button and the expanded
    // dialog. Keep this in sync if you change slash-command dispatch or
    // payload shape.
    val sendCurrent: () -> Unit = {
        if (pendingUserInput != null) {
            onDismissPendingUserInput?.invoke()
        }
        val handledAsSlash = parseSlashCommandInvocation(text)?.let { invocation ->
            if (dispatchSlashCommand(invocation.command.name, invocation.args)) {
                textFieldValue = TextFieldValue("")
                attachedImage = null
                attachedFiles = emptyList()
                true
            } else false
        } ?: false
        if (!handledAsSlash && (text.isNotBlank() || attachedImage != null || attachedFiles.isNotEmpty())) {
            val attachmentToSend = attachedImage
            val filesToSend = attachedFiles
            val payload = composerTurnPayload(appModel, threadKey, text, attachmentToSend, filesToSend)
            textFieldValue = TextFieldValue("")
            attachedImage = null
            attachedFiles = emptyList()
            scope.launch {
                try {
                    appModel.startTurn(threadKey, payload)
                } catch (e: Exception) {
                    textFieldValue = TextFieldValue(
                        text = payload.text,
                        selection = TextRange(payload.text.length),
                    )
                    attachedImage = attachmentToSend
                    attachedFiles = filesToSend
                }
            }
        }
    }
    val canSend = text.isNotBlank() || attachedImage != null || attachedFiles.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface)
            .imePadding(),
    ) {
        ComposerAttachmentPreviews(
            attachedImage = attachedImage,
            attachedFiles = attachedFiles,
            onRemoveImage = { attachedImage = null },
            onRemoveFile = { file ->
                attachedFiles = attachedFiles.filterNot { it == file }
            },
        )

        goal?.let { current ->
            val goalActions = remember(current.threadId, current.status) {
                composerGoalCardActions(current, appModel, threadKey, scope, onSlashError)
            }
            GoalPanel(current, goalActions)
        }

        activePlanProgress?.let { progress ->
            PlanProgressPanel(progress = progress)
        }

        activeTaskSummary?.let { summary ->
            ComposerActiveTaskSummary(summary)
        }

        // Inline pending user input prompt (above composer)
        if (pendingUserInput != null) {
            ComposerPendingInputCard(
                pendingUserInput = pendingUserInput,
                userInputAnswers = userInputAnswers,
                onAnswerChange = { questionId, answer ->
                    userInputAnswers = userInputAnswers + (questionId to answer)
                },
                pendingUserInputSubmitError = pendingUserInputSubmitError,
                isSubmittingPendingUserInput = isSubmittingPendingUserInput,
                onDismissPendingUserInput = onDismissPendingUserInput,
                onSubmit = {
                    scope.launch {
                        isSubmittingPendingUserInput = true
                        pendingUserInputSubmitError = null
                        try {
                            val answers = pendingUserInput.questions.map { q ->
                                PendingUserInputAnswer(
                                    questionId = q.id,
                                    answers = listOfNotNull(userInputAnswers[q.id]),
                                )
                            }
                            appModel.store.respondToUserInput(pendingUserInput.id, answers)
                            userInputAnswers = emptyMap()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            LLog.e(
                                "ComposerBar",
                                "user input response failed",
                                error,
                                fields = mapOf("requestId" to pendingUserInput.id),
                            )
                            pendingUserInputSubmitError = responseSubmissionErrorMessage(error)
                        } finally {
                            isSubmittingPendingUserInput = false
                        }
                    }
                },
            )
        }

        if (queuedFollowUps.isNotEmpty()) {
            QueuedFollowUpsPreviewPanel(
                previews = queuedFollowUps,
                onSteer = { preview ->
                    scope.launch {
                        runCatching {
                            appModel.store.steerQueuedFollowUp(threadKey, preview.id)
                        }
                    }
                },
                onDelete = { preview ->
                    scope.launch {
                        runCatching {
                            appModel.store.deleteQueuedFollowUp(threadKey, preview.id)
                        }
                    }
                },
            )
        }

        ComposerInputRow(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = { textFieldValue = it },
            attachedImage = attachedImage,
            attachedFiles = attachedFiles,
            isRecording = isRecording,
            isTranscribing = isTranscribing,
            isThinking = isThinking,
            canSend = canSend,
            inlineFocusRequester = inlineFocusRequester,
            showSlashMenu = showSlashMenu,
            filteredCommands = filteredCommands,
            onDismissSlashMenu = { showSlashMenu = false },
            onSlashCommandSelected = { cmd ->
                if (dispatchSlashCommand(cmd.name, args = null)) {
                    textFieldValue = TextFieldValue("")
                    attachedImage = null
                    attachedFiles = emptyList()
                }
            },
            showFileMenu = showFileMenu,
            fileSearchResults = fileSearchResults,
            onDismissFileMenu = { showFileMenu = false },
            onShowAttachMenu = { showAttachMenu = true },
            onShowExpanded = { showExpanded = true },
            onSend = sendCurrent,
            transcriptionManager = transcriptionManager,
            micPermissionLauncher = micPermissionLauncher,
            onTranscript = { textFieldValue = insertComposerTranscript(textFieldValue, it) },
            appModel = appModel,
            threadKey = threadKey,
            scope = scope,
            activeTurnId = activeTurnId,
        )

        if (showExpanded) {
            ComposerExpandedDialog(
                text = text,
                onTextChange = {
                    textFieldValue = TextFieldValue(
                        text = it,
                        selection = TextRange(it.length),
                    )
                },
                onSend = sendCurrent,
                onDismiss = {
                    showExpanded = false
                    // Restore inline focus after the dialog animates away, so
                    // the user can keep typing without tapping again.
                    scope.launch {
                        kotlinx.coroutines.delay(80)
                        runCatching { inlineFocusRequester.requestFocus() }
                    }
                },
                canSend = canSend,
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
