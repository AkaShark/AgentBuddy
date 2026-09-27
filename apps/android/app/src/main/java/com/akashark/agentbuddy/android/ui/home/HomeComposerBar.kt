package com.akashark.agentbuddy.android.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.akashark.agentbuddy.android.state.LocalAccountLoginRequiredException
import com.akashark.agentbuddy.android.state.VoiceTranscriptionManager
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.RecentDirectoryStore
import com.akashark.agentbuddy.android.ui.conversation.ComposerExpandedDialog
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyListRow
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AuthStatusRequest
import uniffi.codex_mobile_client.ThreadKey

/**
 * New-task composer. On send it creates a thread on (project.serverId,
 * project.cwd), records the directory, submits the first turn and reports
 * the new key; the caller stays on home while the task streams in the list.
 * The draft lives in [draft] so a failed creation keeps it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeComposerBar(
    project: AppProject?,
    onThreadCreated: (ThreadKey) -> Unit,
    modifier: Modifier = Modifier,
    draft: HomeComposerDraft = remember { HomeComposerDraft() },
    onLoginRequired: (String) -> Unit = {},
    onSubmittingChange: (Boolean) -> Unit = {},
    autoFocus: Boolean = true,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(150)
            runCatching { focusRequester.requestFocus() }
        }
    }
    LaunchedEffect(isSubmitting) { onSubmittingChange(isSubmitting) }

    val transcriptionManager = remember { VoiceTranscriptionManager() }
    val isRecording by transcriptionManager.isRecording.collectAsState()
    val isTranscribing by transcriptionManager.isTranscribing.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) transcriptionManager.startRecording(context)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { draft.image = readAttachmentFromUri(context, it) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            when (val picked = readPickedComposerAttachment(context, it)) {
                is PickedComposerAttachment.Image -> draft.image = picked.attachment
                is PickedComposerAttachment.File -> if (picked.attachment !in draft.files) draft.files = draft.files + picked.attachment
                null -> Unit
            }
        }
    }

    // Single send path for the inline button and the expanded editor.
    val sendCurrent: () -> Unit = send@{
        if (isSubmitting || !draft.hasContent) return@send
        val currentProject = project
        if (currentProject == null) {
            errorMessage = "发送前请先选择一个项目。"
            return@send
        }
        val payloadText = draft.text.text.trim()
        val imageToSend = draft.image
        val filesToSend = draft.files
        draft.clear()
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                val serverIsLocal = appModel.snapshot.value?.servers?.firstOrNull { it.serverId == currentProject.serverId }?.isLocal == true
                val launchSnapshot = appModel.launchState.snapshot.value
                val threadKey = appModel.startThread(
                    currentProject.serverId,
                    appModel.launchState.threadStartRequest(currentProject.cwd, serverIsLocal = serverIsLocal),
                )
                RecentDirectoryStore(context).record(currentProject.serverId, currentProject.cwd)
                val payload = AppComposerPayload(
                    text = payloadText,
                    additionalInputs = listOfNotNull(imageToSend?.toUserInput()),
                    fileAttachments = filesToSend,
                    approvalPolicy = appModel.launchState.approvalPolicyValue(threadKey),
                    sandboxPolicy = appModel.launchState.turnSandboxPolicy(threadKey),
                    model = launchSnapshot.selectedModel.trim().ifEmpty { null },
                    reasoningEffort = launchSnapshot.reasoningEffort.trim().ifEmpty { null }?.let(::reasoningEffortFromServerValue),
                    serviceTier = null,
                )
                appModel.startTurn(threadKey, payload)
                appModel.refreshThreadSnapshot(threadKey)
                onThreadCreated(threadKey)
            } catch (e: LocalAccountLoginRequiredException) {
                draft.restore(payloadText, imageToSend, filesToSend)
                onLoginRequired(e.serverId)
            } catch (e: Exception) {
                draft.restore(payloadText, imageToSend, filesToSend)
                errorMessage = e.message ?: "启动任务失败"
            } finally {
                isSubmitting = false
            }
        }
    }

    val onDictation: () -> Unit = {
        if (isRecording) {
            val currentProject = project
            if (currentProject == null) {
                transcriptionManager.cancelRecording()
            } else {
                scope.launch {
                    val auth = runCatching {
                        appModel.client.authStatus(currentProject.serverId, AuthStatusRequest(includeToken = true, refreshToken = false))
                    }.getOrNull()
                    transcriptionManager.stopAndTranscribe(authMethod = auth?.authMethod, authToken = auth?.authToken)
                        ?.let { draft.text = insertHomeComposerTranscript(draft.text, it) }
                }
            }
        } else if (!isTranscribing) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    HomeComposerCard(
        value = draft.text,
        onValueChange = { draft.text = it },
        onSend = sendCurrent,
        onAttach = { showAttachMenu = true },
        onDictation = onDictation,
        onExpand = { showExpanded = true },
        modifier = modifier,
        attachedImage = draft.image,
        attachedFiles = draft.files,
        onRemoveImage = { draft.image = null },
        onRemoveFile = { file -> draft.files = draft.files.filterNot { it == file } },
        dictation = when {
            isRecording -> HomeComposerDictation.RECORDING
            isTranscribing -> HomeComposerDictation.TRANSCRIBING
            else -> HomeComposerDictation.IDLE
        },
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
        onDismissError = { errorMessage = null },
        focusRequester = focusRequester,
    )

    if (showExpanded) {
        ComposerExpandedDialog(
            text = draft.text.text,
            onTextChange = { draft.text = TextFieldValue(text = it, selection = TextRange(it.length)) },
            onSend = sendCurrent,
            onDismiss = {
                showExpanded = false
                scope.launch {
                    delay(80)
                    runCatching { focusRequester.requestFocus() }
                }
            },
            canSend = draft.hasContent && !isSubmitting,
            placeholder = "想做什么？一句话就够。",
        )
    }

    if (showAttachMenu) {
        BuddyBottomSheet(onDismissRequest = { showAttachMenu = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = BuddySpacing.xl, vertical = BuddySpacing.sm),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            ) {
                Text("附加", style = buddyTextStyle(BuddyTextStyle.TITLE), color = AgentBuddyTheme.textPrimary)
                BuddyListRow(
                    title = "照片图库",
                    tile = { BuddyIconTile(BuddyTileContent.Symbol(Icons.Outlined.PhotoLibrary)) },
                    onClick = {
                        showAttachMenu = false
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                BuddyListRow(
                    title = "选择文件",
                    tile = { BuddyIconTile(BuddyTileContent.Symbol(Icons.Outlined.Description)) },
                    onClick = {
                        showAttachMenu = false
                        filePicker.launch(ALL_FILE_MIME_TYPES)
                    },
                )
            }
        }
    }
}
