package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import androidx.compose.ui.unit.dp

/**
 * Full-screen composer for long prompts. Opened from the inline composer's
 * expand icon; shares the same `text` state so edits round-trip back when
 * the dialog is dismissed. Matches `ConversationComposerExpandedView` on iOS.
 * Send goes through the caller's single send path and is disabled while
 * [canSend] is false (the draft is never cleared by a blocked send).
 */
@Composable
fun ComposerExpandedDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    canSend: Boolean,
    placeholder: String = "消息…",
    /** Shown under the toolbar when sending is blocked (e.g. the host is disconnected). */
    notice: String? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AgentBuddyTheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BuddySpacing.xs, vertical = BuddySpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BuddyIconButton(
                        icon = Icons.Outlined.CloseFullscreen,
                        contentDescription = "收起编辑框",
                        onClick = onDismiss,
                        iconSize = 20.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    BuddyIconButton(
                        icon = Icons.Outlined.ArrowUpward,
                        contentDescription = "发送",
                        onClick = {
                            if (canSend) {
                                onSend()
                                onDismiss()
                            }
                        },
                        tone = BuddyIconButtonTone.ACTION,
                        diameter = 40.dp,
                        enabled = canSend,
                    )
                }
                if (notice != null) {
                    BuddyBanner(
                        tone = BuddyBannerTone.WARNING,
                        message = notice,
                        modifier = Modifier.padding(horizontal = BuddySpacing.md),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = BuddySpacing.lg, vertical = BuddySpacing.xs),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = buddyTextStyle(BuddyTextStyle.BODY),
                            color = AgentBuddyTheme.textSecondary,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = buddyTextStyle(BuddyTextStyle.BODY).copy(color = AgentBuddyTheme.textPrimary),
                        cursorBrush = SolidColor(AgentBuddyTheme.focus),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    )
                }
            }
        }
    }
}
