package com.akashark.agentbuddy.android.ui.conversation

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.ComposerImageAttachment
import com.akashark.agentbuddy.android.state.ComposerFileAttachment
import com.akashark.agentbuddy.android.state.VoiceTranscriptionManager
import kotlinx.coroutines.CoroutineScope
import uniffi.codex_mobile_client.AuthStatusRequest
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppInterruptTurnRequest

/** Composer input row: attach, text field with slash/@file menus, voice, send and cancel. */
@Composable
internal fun ComposerInputRow(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    attachedImage: ComposerImageAttachment?,
    attachedFiles: List<ComposerFileAttachment>,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isThinking: Boolean,
    canSend: Boolean,
    inlineFocusRequester: FocusRequester,
    showSlashMenu: Boolean,
    filteredCommands: List<SlashCommand>,
    onDismissSlashMenu: () -> Unit,
    onSlashCommandSelected: (SlashCommand) -> Unit,
    showFileMenu: Boolean,
    fileSearchResults: List<String>,
    onDismissFileMenu: () -> Unit,
    onShowAttachMenu: () -> Unit,
    onShowExpanded: () -> Unit,
    onSend: () -> Unit,
    transcriptionManager: VoiceTranscriptionManager,
    micPermissionLauncher: ActivityResultLauncher<String>,
    onTranscript: (String) -> Unit,
    appModel: AppModel,
    threadKey: ThreadKey,
    scope: CoroutineScope,
    activeTurnId: String?,
) {
    val context = LocalContext.current
    val text = textFieldValue.text
    // Input row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isRecording && !isTranscribing && !isThinking) {
            IconButton(
                onClick = { onShowAttachMenu() },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "附加",
                    tint = AgentBuddyTheme.textPrimary,
                )
            }
        }

        // Text field
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 36.dp, max = 120.dp)
                .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        text = "\u6d88\u606f\u2026",
                        color = AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                    )
                }
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { onTextFieldValueChange(it) },
                    textStyle = TextStyle(
                        color = AgentBuddyTheme.textPrimary,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                        fontFamily = AgentBuddyTheme.monoFont,
                    ),
                    cursorBrush = SolidColor(AgentBuddyTheme.accent),
                    // Always reserve trailing space for the expand icon so
                    // wrapped lines don't slide under it when the icon
                    // appears (and it doesn't cause a layout jump when it
                    // toggles on/off at the 60-char threshold).
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 24.dp)
                        .focusRequester(inlineFocusRequester),
                )

                val shouldShowExpand = (text.contains('\n') || text.length > 60) &&
                    !isRecording && !isTranscribing
                if (shouldShowExpand) {
                    IconButton(
                        onClick = { onShowExpanded() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "展开输入框",
                            tint = AgentBuddyTheme.textSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                // Slash command popup
                ComposerSlashCommandMenu(
                    showSlashMenu = showSlashMenu,
                    filteredCommands = filteredCommands,
                    onDismissSlashMenu = onDismissSlashMenu,
                    onSlashCommandSelected = onSlashCommandSelected,
                )

                // @file search popup
                ComposerFileSearchMenu(
                    showFileMenu = showFileMenu,
                    fileSearchResults = fileSearchResults,
                    text = text,
                    onDismissFileMenu = onDismissFileMenu,
                    onTextFieldValueChange = onTextFieldValueChange,
                )
            }

            when {
                isRecording -> {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                val auth = runCatching {
                                    appModel.client.authStatus(
                                        threadKey.serverId,
                                        AuthStatusRequest(
                                            includeToken = true,
                                            refreshToken = false,
                                        ),
                                    )
                                }.getOrNull()
                                val transcript = transcriptionManager.stopAndTranscribe(
                                    authMethod = auth?.authMethod,
                                    authToken = auth?.authToken,
                                )
                                transcript?.let {
                                    onTranscript(it)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止录音",
                            tint = AgentBuddyTheme.accentStrong,
                        )
                    }
                }

                isTranscribing -> {
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.width(24.dp),
                        color = AgentBuddyTheme.accent,
                        trackColor = Color.Transparent,
                    )
                }

                else -> {
                    val realtimeAvailable = remember {
                        com.akashark.agentbuddy.android.ui.ExperimentalFeatures.isEnabled(
                            com.akashark.agentbuddy.android.ui.AgentBuddyFeature.REALTIME_VOICE,
                        )
                    }
                    val voiceController = remember { com.akashark.agentbuddy.android.state.VoiceRuntimeController.shared }
                    val voiceSession by voiceController.activeVoiceSession.collectAsState()
                    val voiceSnapshot by appModel.snapshot.collectAsState()
                    val voicePhase = voiceSnapshot?.voiceSession?.phase
                    val voiceInputLevel = voiceSession?.inputLevel ?: 0f

                    if (realtimeAvailable && text.isEmpty() && attachedImage == null && attachedFiles.isEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        com.akashark.agentbuddy.android.ui.voice.InlineVoiceButton(
                            phase = voicePhase,
                            inputLevel = voiceInputLevel,
                            isAvailable = true,
                            onStart = {
                                scope.launch {
                                    voiceController.startVoiceOnThread(appModel, threadKey)
                                }
                            },
                            onStop = {
                                scope.launch {
                                    voiceController.stopActiveVoiceSession(appModel)
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        )
                    } else {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (transcriptionManager.hasMicPermission(context)) {
                                    transcriptionManager.startRecording(context)
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "语音",
                                tint = AgentBuddyTheme.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        if (canSend) {
            IconButton(
                onClick = onSend,
                enabled = !isRecording && !isTranscribing,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (!isRecording && !isTranscribing) {
                            AgentBuddyTheme.accent
                        } else {
                            AgentBuddyTheme.accent.copy(alpha = 0.45f)
                        },
                        CircleShape,
                    ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.Black,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        if (isThinking && !canSend) {
            Text(
                text = "取消",
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(AgentBuddyTheme.surface)
                    .clickable {
                        val turnId = activeTurnId ?: return@clickable
                        scope.launch {
                            try {
                                appModel.client.interruptTurn(
                                    threadKey.serverId,
                                    AppInterruptTurnRequest(threadId = threadKey.threadId, turnId = turnId),
                                )
                            } catch (_: Exception) {}
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

internal fun insertComposerTranscript(current: TextFieldValue, transcript: String): TextFieldValue {
    val insertion = transcript.trim()
    if (insertion.isEmpty()) return current

    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    val replacement = composerInsertionText(insertion, text, start, end)
    val updated = text.replaceRange(start, end, replacement)
    val cursor = start + replacement.length
    return TextFieldValue(
        text = updated,
        selection = TextRange(cursor),
    )
}

private fun composerInsertionText(insertion: String, text: String, start: Int, end: Int): String {
    var replacement = insertion
    if (start > 0 && !text[start - 1].isWhitespace()) {
        replacement = " $replacement"
    }
    if (end < text.length && !text[end].isWhitespace()) {
        replacement += " "
    }
    return replacement
}
