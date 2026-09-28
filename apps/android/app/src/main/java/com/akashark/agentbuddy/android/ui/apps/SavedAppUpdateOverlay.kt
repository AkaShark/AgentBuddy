package com.akashark.agentbuddy.android.ui.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.settings.SettingsTextField
import kotlinx.coroutines.delay

/**
 * Bottom card that asks what to change in a saved app. The dim fades out
 * while the update runs so the widget stays visible underneath.
 */
@Composable
fun SavedAppUpdateOverlay(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    isSubmitting: Boolean,
) {
    var prompt by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    // Fade the dim out while the update is running so the user can see the
    // widget regenerating underneath. Mirrors iOS SavedAppUpdateOverlay.swift:18.
    val dimAlpha by animateFloatAsState(
        targetValue = if (isSubmitting) 0f else 0.45f,
        animationSpec = BuddyMotion.PAGE.spec(buddyReduceMotion),
        label = "update-overlay-dim",
    )

    LaunchedEffect(Unit) {
        // Brief delay lets the overlay animation settle before pulling focus so
        // the IME doesn't fight the dim fade. Mirrors iOS' 200ms asyncAfter.
        delay(200)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimAlpha))
            .pointerInput(isSubmitting) {
                // Consume taps so they don't fall through to the underlying
                // WebView. Dismiss on tap outside the card only when not
                // submitting.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .clickable(enabled = !isSubmitting, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(BuddySpacing.md)
                .navigationBarsPadding()
                .imePadding()
                .clip(BuddyShapes.confirmCard)
                .background(AgentBuddyTheme.surface)
                .border(1.dp, AgentBuddyTheme.border, BuddyShapes.confirmCard)
                .clickable(enabled = false, onClick = {})
                .padding(BuddySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSubmitting) "正在更新「$currentTitle」" else "更新应用",
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!isSubmitting) {
                    BuddyIconButton(icon = Icons.Outlined.Close, contentDescription = "关闭", onClick = onDismiss)
                }
            }

            if (isSubmitting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AgentBuddyTheme.textSecondary,
                    )
                    Text(
                        text = "正在处理你的更新…",
                        style = buddyTextStyle(BuddyTextStyle.BODY),
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
            } else {
                Text(
                    text = "描述你想要的改动。模型会保留你已保存的状态。",
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = AgentBuddyTheme.textSecondary,
                )
                SettingsTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = "改动",
                    placeholder = "例如：把按钮放大一些",
                    singleLine = false,
                    modifier = Modifier.focusRequester(focusRequester),
                )
                BuddyChromeTypeLimit {
                    Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
                        BuddyButton(
                            text = "取消",
                            onClick = onDismiss,
                            kind = BuddyButtonKind.SECONDARY,
                            modifier = Modifier.weight(1f),
                        )
                        BuddyButton(
                            text = "提交",
                            onClick = {
                                val value = prompt.trim()
                                if (value.isNotEmpty()) onSubmit(value)
                            },
                            enabled = prompt.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
