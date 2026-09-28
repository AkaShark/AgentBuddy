package com.akashark.agentbuddy.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.ThreadKey

/**
 * Minimal reply composer opened from a task's swipe or 「回复」 menu. Calls
 * [onSend] with the trimmed text and dismisses on success; a failure stays
 * visible in the sheet with the text kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReplySheet(
    thread: AppSessionSummary,
    onDismiss: () -> Unit,
    onSend: suspend (ThreadKey, String) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val canSend = !isSending && text.trim().isNotEmpty()

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    val send: () -> Unit = send@{
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isSending) return@send
        isSending = true
        errorMessage = null
        scope.launch {
            val result = onSend(thread.key, trimmed)
            isSending = false
            result
                .onSuccess { onDismiss() }
                .onFailure { err -> errorMessage = err.message ?: "发送回复失败" }
        }
    }

    BuddyBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BuddySpacing.xl).padding(bottom = BuddySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
                Text(
                    text = thread.displayTitle,
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${thread.serverDisplayName} · ${HomeDashboardSupport.workspaceLabel(thread.cwd)}",
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            errorMessage?.let { BuddyBanner(tone = BuddyBannerTone.DANGER, message = it) }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AgentBuddyTheme.surface, BuddyShapes.composer)
                        .border(1.dp, AgentBuddyTheme.borderControl, BuddyShapes.composer)
                        .padding(start = BuddySpacing.md, end = BuddySpacing.xxs, top = BuddySpacing.xxs, bottom = BuddySpacing.xxs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.weight(1f).padding(vertical = BuddySpacing.sm)) {
                    if (text.isEmpty()) {
                        Text("回复…", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textMuted)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = buddyTextStyle(BuddyTextStyle.BODY).copy(color = AgentBuddyTheme.textPrimary),
                        cursorBrush = SolidColor(AgentBuddyTheme.focus),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 24.dp, max = 200.dp)
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = "回复内容" },
                    )
                }
                if (isSending) {
                    Box(Modifier.size(BuddySize.minHitTarget).semantics { stateDescription = "正在发送" }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).background(AgentBuddyTheme.action, CircleShape), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AgentBuddyTheme.onAction, strokeWidth = 2.dp)
                        }
                    }
                } else {
                    BuddyIconButton(
                        icon = Icons.Outlined.ArrowUpward,
                        contentDescription = "发送",
                        onClick = send,
                        tone = BuddyIconButtonTone.ACTION,
                        diameter = 40.dp,
                        iconSize = 20.dp,
                        enabled = canSend,
                    )
                }
            }
        }
    }
}
