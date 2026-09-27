package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppOperationStatus
import uniffi.codex_mobile_client.HydratedPlanStepStatus

@Composable
internal fun CodeReviewRow(
    data: uniffi.codex_mobile_client.HydratedCodeReviewData,
) {
    var dismissedIndices by remember(data.findings) { mutableStateOf(setOf<Int>()) }
    val visibleFindings = remember(data.findings, dismissedIndices) {
        data.findings.mapIndexedNotNull { index, finding ->
            if (dismissedIndices.contains(index)) null else index to finding
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        visibleFindings.forEach { (index, finding) ->
            CodeReviewFindingCard(
                finding = finding,
                onDismiss = { dismissedIndices = dismissedIndices + index },
            )
        }
    }
}

@Composable
private fun CodeReviewFindingCard(
    finding: uniffi.codex_mobile_client.HydratedCodeReviewFindingData,
    onDismiss: () -> Unit,
) {
    val priorityTint = when (finding.priority?.toInt()) {
        0, 1 -> AgentBuddyTheme.danger
        2 -> AgentBuddyTheme.warning
        3 -> AgentBuddyTheme.textSecondary
        else -> AgentBuddyTheme.textSecondary
    }
    val locationText = remember(finding.codeLocation) {
        val location = finding.codeLocation ?: return@remember null
        val range = location.lineRange
        when {
            range == null -> location.absoluteFilePath
            range.start == range.end -> "${location.absoluteFilePath}:${range.start}"
            else -> "${location.absoluteFilePath}:${range.start}-${range.end}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            finding.priority?.let { priority ->
                Text(
                    text = "P${priority.toInt()}",
                    color = priorityTint,
                    fontSize = AgentBuddyTextStyle.caption2.scaled,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(priorityTint.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(10.dp))
            }

            Text(
                text = finding.title,
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.callout.scaled,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = "忽略",
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.callout.scaled,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }

        MarkdownText(text = finding.body)

        locationText?.takeIf { it.isNotBlank() }?.let { location ->
            Text(
                text = location,
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.footnote.scaled,
                fontFamily = AgentBuddyTheme.monoFont,
            )
        }
    }
}

// ── Todo List ────────────────────────────────────────────────────────────────

@Composable
internal fun TodoListRow(
    data: uniffi.codex_mobile_client.HydratedTodoListData,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        for (step in data.steps) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 1.dp),
            ) {
                val icon = when (step.status) {
                    HydratedPlanStepStatus.COMPLETED -> "✓"
                    HydratedPlanStepStatus.IN_PROGRESS -> "●"
                    HydratedPlanStepStatus.PENDING -> "○"
                }
                val color = when (step.status) {
                    HydratedPlanStepStatus.COMPLETED -> AgentBuddyTheme.success
                    HydratedPlanStepStatus.IN_PROGRESS -> AgentBuddyTheme.accent
                    HydratedPlanStepStatus.PENDING -> AgentBuddyTheme.textMuted
                }
                Text(text = icon, color = color, fontSize = AgentBuddyTextStyle.footnote.scaled)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = step.step,
                    color = AgentBuddyTheme.textBody,
                    fontSize = AgentBuddyTextStyle.body.scaled,
                )
            }
        }
    }
}

// ── Proposed Plan ────────────────────────────────────────────────────────────

@Composable
internal fun ProposedPlanRow(
    data: uniffi.codex_mobile_client.HydratedProposedPlanData,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = "计划",
            color = AgentBuddyTheme.accent,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        MarkdownText(text = data.content)
    }
}

@Composable
internal fun UserInputResponseRow(
    data: uniffi.codex_mobile_client.HydratedUserInputResponseData,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "请求的输入",
            color = AgentBuddyTheme.textPrimary,
            fontSize = AgentBuddyTextStyle.body.scaled,
            fontWeight = FontWeight.SemiBold,
        )

        data.questions.forEach { question ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                question.header?.takeIf { it.isNotBlank() }?.let { header ->
                    Text(
                        text = header.uppercase(),
                        color = AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.caption2.scaled,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = question.question,
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = AgentBuddyTextStyle.body.scaled,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = question.answer.ifBlank { "未提供回答" },
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = AgentBuddyTextStyle.body.scaled,
                )
            }
        }
    }
}

// ── Divider ──────────────────────────────────────────────────────────────────

@Composable
internal fun TurnDiffRow(
    data: uniffi.codex_mobile_client.HydratedTurnDiffData,
) {
    ToolCardShell(
        summary = "本轮差异",
        accent = AgentBuddyTheme.toolCallFileChange,
        status = AppOperationStatus.COMPLETED,
    ) {
        DiffSection(label = "差异", content = data.diff)
    }
}

@Composable
internal fun DividerRow(
    data: uniffi.codex_mobile_client.HydratedDividerData,
    isLiveTurn: Boolean,
) {
    val label = when (data) {
        is uniffi.codex_mobile_client.HydratedDividerData.ContextCompaction ->
            if (data.isComplete && !isLiveTurn) "\u4e0a\u4e0b\u6587\u5df2\u538b\u7f29" else "\u6b63\u5728\u538b\u7f29\u4e0a\u4e0b\u6587\u2026"
        is uniffi.codex_mobile_client.HydratedDividerData.ModelRerouted -> {
            val route = data.fromModel?.takeIf { it.isNotBlank() }?.let { "$it -> ${data.toModel}" }
                ?: "\u5df2\u8def\u7531\u81f3 ${data.toModel}"
            val reason = data.reason?.takeIf { it.isNotBlank() }
            if (reason != null) "$route | $reason" else route
        }
        is uniffi.codex_mobile_client.HydratedDividerData.ReviewEntered -> "\u5df2\u5f00\u59cb\u5ba1\u67e5"
        is uniffi.codex_mobile_client.HydratedDividerData.ReviewExited -> "\u5df2\u7ed3\u675f\u5ba1\u67e5"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = AgentBuddyTheme.divider,
        )
        Text(
            text = "  $label  ",
            color = AgentBuddyTheme.textMuted,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = AgentBuddyTheme.divider,
        )
    }
}

// ── Note ─────────────────────────────────────────────────────────────────────

@Composable
internal fun NoteRow(
    data: uniffi.codex_mobile_client.HydratedNoteData,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Text(
            text = data.title,
            color = AgentBuddyTheme.textPrimary,
            fontSize = AgentBuddyTextStyle.body.scaled,
            fontWeight = FontWeight.Medium,
        )
        if (data.body.isNotBlank()) {
            Text(
                text = data.body,
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.body.scaled,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun ErrorRow(
    data: uniffi.codex_mobile_client.HydratedErrorData,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        SelectableConversationText {
            Text(
                text = data.title.ifBlank { "错误" },
                color = AgentBuddyTheme.danger,
                fontSize = AgentBuddyTextStyle.body.scaled,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = data.message,
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.body.scaled,
                modifier = Modifier.padding(top = 2.dp),
            )
            data.details?.takeIf { it.isNotBlank() }?.let { details ->
                Text(
                    text = details,
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = AgentBuddyTextStyle.body.scaled,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
