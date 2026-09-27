package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import uniffi.codex_mobile_client.PendingUserInputRequest
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

/** Inline pending user input prompt shown above the composer input row. */
@Composable
internal fun ComposerPendingInputCard(
    pendingUserInput: PendingUserInputRequest,
    userInputAnswers: Map<String, String>,
    onAnswerChange: (questionId: String, answer: String) -> Unit,
    pendingUserInputSubmitError: String?,
    isSubmittingPendingUserInput: Boolean,
    onDismissPendingUserInput: (() -> Unit)?,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.codeBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "需要输入",
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            if (onDismissPendingUserInput != null) {
                Text(
                    text = "✕",
                    color = AgentBuddyTheme.textMuted,
                    fontSize = AgentBuddyTextStyle.body.scaled,
                    modifier = Modifier
                        .clickable { onDismissPendingUserInput() }
                        .padding(4.dp)
                        .semantics { contentDescription = "关闭输入请求" },
                )
            }
        }
        for (question in pendingUserInput.questions) {
            Text(question.question, color = AgentBuddyTheme.textPrimary, fontSize = AgentBuddyTextStyle.footnote.scaled)
            if (question.options.isNotEmpty()) {
                // FlowRow so long option labels wrap to a new line
                // instead of crushing a short option into a narrow
                // column with character-by-character text wrapping.
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (option in question.options) {
                        val selected = userInputAnswers[question.id] == option.label
                        Text(
                            text = option.label,
                            color = if (selected) Color.Black else AgentBuddyTheme.textPrimary,
                            fontSize = AgentBuddyTextStyle.caption.scaled,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .background(
                                    if (selected) AgentBuddyTheme.accent else AgentBuddyTheme.surface,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onAnswerChange(question.id, option.label) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            } else {
                var answer by remember { mutableStateOf("") }
                BasicTextField(
                    value = answer,
                    onValueChange = {
                        answer = it
                        onAnswerChange(question.id, it)
                    },
                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = AgentBuddyTextStyle.footnote.scaled),
                    cursorBrush = SolidColor(AgentBuddyTheme.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            }
        }
        pendingUserInputSubmitError?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFF6B6B),
                fontSize = AgentBuddyTextStyle.caption.scaled,
            )
        }
        Text(
            text = "提交",
            color = if (isSubmittingPendingUserInput) AgentBuddyTheme.textMuted else Color.Black,
            fontSize = AgentBuddyTextStyle.code.scaled,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    if (isSubmittingPendingUserInput) AgentBuddyTheme.surface else AgentBuddyTheme.accent,
                    RoundedCornerShape(8.dp),
                )
                .clickable(enabled = !isSubmittingPendingUserInput) { onSubmit() }
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
