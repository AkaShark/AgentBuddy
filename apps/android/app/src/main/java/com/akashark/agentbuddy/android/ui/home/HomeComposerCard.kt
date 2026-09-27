package com.akashark.agentbuddy.android.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.ComposerFileAttachment
import com.akashark.agentbuddy.android.state.ComposerImageAttachment
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Dictation state of the composer's microphone button. */
enum class HomeComposerDictation { IDLE, RECORDING, TRANSCRIBING }

/**
 * Stateless new-task composer card: attachment previews, the editor, and a
 * control row (attach, expand, dictation, send). Send is disabled without
 * content and shows progress while the task is being created, so it cannot
 * be submitted twice.
 */
@Composable
fun HomeComposerCard(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onDictation: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    attachedImage: ComposerImageAttachment? = null,
    attachedFiles: List<ComposerFileAttachment> = emptyList(),
    onRemoveImage: () -> Unit = {},
    onRemoveFile: (ComposerFileAttachment) -> Unit = {},
    dictation: HomeComposerDictation = HomeComposerDictation.IDLE,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val hasContent = value.text.isNotBlank() || attachedImage != null || attachedFiles.isNotEmpty()
    val busy = isSubmitting || dictation != HomeComposerDictation.IDLE
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        if (errorMessage != null) {
            BuddyBanner(tone = BuddyBannerTone.WARNING, message = errorMessage, actionTitle = "关闭", onAction = onDismissError)
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.surface, BuddyShapes.composer)
                    .border(1.dp, AgentBuddyTheme.borderControl, BuddyShapes.composer)
                    .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        ) {
            if (attachedImage != null) ImagePreview(attachedImage, onRemoveImage)
            attachedFiles.forEach { file -> FileRow(file, onRemove = { onRemoveFile(file) }) }
            Box(Modifier.fillMaxWidth().padding(horizontal = BuddySpacing.xxs, vertical = BuddySpacing.xs)) {
                if (value.text.isEmpty()) {
                    Text("想做什么？一句话就够。", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textMuted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = buddyTextStyle(BuddyTextStyle.BODY).copy(color = AgentBuddyTheme.textPrimary),
                    cursorBrush = SolidColor(AgentBuddyTheme.focus),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = BuddySize.composerMinHeight, max = 200.dp)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "任务描述" },
                )
            }
            BuddyChromeTypeLimit {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BuddyIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "附加",
                        onClick = onAttach,
                        tone = BuddyIconButtonTone.SOFT,
                        enabled = !busy,
                    )
                    Spacer(Modifier.weight(1f))
                    if ((value.text.contains('\n') || value.text.length > 60) && dictation == HomeComposerDictation.IDLE) {
                        BuddyIconButton(icon = Icons.Outlined.OpenInFull, contentDescription = "展开输入框", onClick = onExpand, iconSize = 18.dp)
                    }
                    DictationButton(dictation, enabled = !isSubmitting, onClick = onDictation)
                    SendButton(enabled = hasContent && !busy, isSubmitting = isSubmitting, onClick = onSend)
                }
            }
        }
    }
}

@Composable
private fun DictationButton(dictation: HomeComposerDictation, enabled: Boolean, onClick: () -> Unit) {
    when (dictation) {
        HomeComposerDictation.IDLE ->
            BuddyIconButton(icon = Icons.Outlined.Mic, contentDescription = "语音输入", onClick = onClick, enabled = enabled, iconSize = 20.dp)
        HomeComposerDictation.RECORDING ->
            BuddyIconButton(
                icon = Icons.Outlined.Stop,
                contentDescription = "停止录音",
                onClick = onClick,
                tone = BuddyIconButtonTone.SOFT,
                tint = AgentBuddyTheme.danger,
                iconSize = 20.dp,
            )
        HomeComposerDictation.TRANSCRIBING ->
            Box(Modifier.size(BuddySize.minHitTarget).semantics { stateDescription = "正在转写" }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AgentBuddyTheme.textSecondary, strokeWidth = 2.dp)
            }
    }
}

@Composable
private fun SendButton(enabled: Boolean, isSubmitting: Boolean, onClick: () -> Unit) {
    if (isSubmitting) {
        Box(Modifier.size(BuddySize.minHitTarget).semantics { stateDescription = "正在创建任务" }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).background(AgentBuddyTheme.action, CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AgentBuddyTheme.onAction, strokeWidth = 2.dp)
            }
        }
    } else {
        BuddyIconButton(
            icon = Icons.Outlined.ArrowUpward,
            contentDescription = "发送",
            onClick = onClick,
            tone = BuddyIconButtonTone.ACTION,
            diameter = 40.dp,
            iconSize = 20.dp,
            enabled = enabled,
        )
    }
}

@Composable
private fun ImagePreview(image: ComposerImageAttachment, onRemove: () -> Unit) {
    val bitmap = remember(image.data) { BitmapFactory.decodeByteArray(image.data, 0, image.data.size) }
    Row(Modifier.padding(top = BuddySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "已附加的图片",
                modifier = Modifier.size(60.dp).clip(BuddyShapes.control),
            )
        }
        BuddyIconButton(icon = Icons.Outlined.Close, contentDescription = "移除图片", onClick = onRemove, tone = BuddyIconButtonTone.SOFT, diameter = 28.dp, iconSize = 16.dp)
    }
}

@Composable
private fun FileRow(file: ComposerFileAttachment, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = BuddySpacing.xxs).background(AgentBuddyTheme.surfaceSoft, BuddyShapes.control).padding(start = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(file.label, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold), color = AgentBuddyTheme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(file.path, style = buddyTextStyle(BuddyTextStyle.CAPTION), color = AgentBuddyTheme.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BuddyIconButton(icon = Icons.Outlined.Close, contentDescription = "移除文件", onClick = onRemove, iconSize = 16.dp)
    }
}
