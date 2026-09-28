package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
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
            .padding(vertical = BuddySpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
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
    val (priorityFill, priorityTint) = when (finding.priority?.toInt()) {
        0, 1 -> AgentBuddyTheme.dangerSurface to AgentBuddyTheme.danger
        2 -> AgentBuddyTheme.warningSurface to AgentBuddyTheme.warning
        else -> AgentBuddyTheme.surfaceSoft to AgentBuddyTheme.textSecondary
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
            .timelineDetailCard()
            .padding(start = BuddySpacing.md, end = BuddySpacing.xs, bottom = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            finding.priority?.let { priority ->
                Text(
                    text = "P${priority.toInt()}",
                    style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.SemiBold),
                    color = priorityTint,
                    modifier = Modifier
                        .background(priorityFill, CircleShape)
                        .padding(horizontal = BuddySpacing.xs, vertical = 2.dp),
                )
            }

            Text(
                text = finding.title,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = BuddySpacing.sm)
                    .semantics { heading() },
            )

            TimelineLinkButton(
                text = "忽略",
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = BuddySpacing.xs),
            )
        }

        Box(Modifier.padding(end = BuddySpacing.xs)) {
            MarkdownText(text = finding.body)
        }

        locationText?.takeIf { it.isNotBlank() }?.let { location ->
            Text(
                text = location,
                style = buddyTextStyle(BuddyTextStyle.CAPTION).copy(fontFamily = BerkeleyMono),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier.padding(end = BuddySpacing.xs),
            )
        }
    }
}

// ── Todo List ────────────────────────────────────────────────────────────────

@Composable
internal fun TodoListRow(
    data: uniffi.codex_mobile_client.HydratedTodoListData,
) {
    val completed = data.steps.count { it.status == HydratedPlanStepStatus.COMPLETED }
    val isComplete = data.steps.isNotEmpty() && completed == data.steps.size
    val hasInProgress = data.steps.any { it.status == HydratedPlanStepStatus.IN_PROGRESS }
    val progressTint = when {
        isComplete -> AgentBuddyTheme.success
        hasInProgress -> AgentBuddyTheme.warning
        else -> AgentBuddyTheme.textSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .timelineDetailCard()
            .padding(start = BuddySpacing.md, end = BuddySpacing.md, bottom = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = BuddySpacing.sm)
                .semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isComplete) Icons.Outlined.CheckCircle else Icons.Outlined.Checklist,
                contentDescription = null,
                tint = progressTint,
                modifier = Modifier.size(BuddySize.icon),
            )
            Text(
                text = "待办",
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                color = AgentBuddyTheme.textPrimary,
            )
            Text(
                text = "已完成 $completed/${data.steps.size} 项",
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = progressTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        data.steps.forEachIndexed { index, step ->
            TodoStepRow(index = index, step = step)
        }
    }
}

@Composable
private fun TodoStepRow(
    index: Int,
    step: uniffi.codex_mobile_client.HydratedPlanStep,
) {
    val (icon, tint, label) = when (step.status) {
        HydratedPlanStepStatus.COMPLETED -> Triple(Icons.Outlined.CheckCircle, AgentBuddyTheme.success, "已完成")
        HydratedPlanStepStatus.IN_PROGRESS -> Triple(Icons.Outlined.Adjust, AgentBuddyTheme.warning, "正在进行")
        HydratedPlanStepStatus.PENDING -> Triple(Icons.Outlined.RadioButtonUnchecked, AgentBuddyTheme.textSecondary, "待处理")
    }
    val isDone = step.status == HydratedPlanStepStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp),
        )
        Text(
            text = "${index + 1}.",
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textSecondary,
        )
        Text(
            text = step.step,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal).copy(
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
            ),
            color = if (isDone) AgentBuddyTheme.textSecondary else AgentBuddyTheme.textBody,
            modifier = Modifier.weight(1f),
        )
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
            .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard, padding = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        TimelineCardTitle(
            icon = Icons.AutoMirrored.Outlined.ListAlt,
            title = "计划",
            tint = AgentBuddyTheme.textSecondary,
        )
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
            .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard, padding = BuddySpacing.md),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        TimelineCardTitle(
            icon = Icons.Outlined.CheckCircle,
            title = "请求的输入",
            tint = AgentBuddyTheme.success,
        )

        data.questions.forEach { question ->
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                question.header?.takeIf { it.isNotBlank() }?.let { header ->
                    Text(
                        text = header,
                        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
                Text(
                    text = question.question,
                    style = buddyTextStyle(BuddyTextStyle.LABEL),
                    color = AgentBuddyTheme.textPrimary,
                )
                Text(
                    text = question.answer.ifBlank { "未提供回答" },
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
        }
    }
}

/** Icon + semibold label heading at the top of a timeline card. */
@Composable
private fun TimelineCardTitle(
    icon: ImageVector,
    title: String,
    tint: Color,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon))
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
            color = AgentBuddyTheme.textPrimary,
        )
    }
}

// ── Divider ──────────────────────────────────────────────────────────────────

@Composable
internal fun TurnDiffRow(
    data: uniffi.codex_mobile_client.HydratedTurnDiffData,
) {
    ToolCardShell(
        summary = "本轮差异",
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
            if (data.isComplete && !isLiveTurn) "上下文已压缩" else "正在压缩上下文…"
        is uniffi.codex_mobile_client.HydratedDividerData.ModelRerouted -> {
            val route = data.fromModel?.takeIf { it.isNotBlank() }?.let { "$it -> ${data.toModel}" }
                ?: "已路由至 ${data.toModel}"
            val reason = data.reason?.takeIf { it.isNotBlank() }
            if (reason != null) "$route | $reason" else route
        }
        is uniffi.codex_mobile_client.HydratedDividerData.ReviewEntered -> "已开始审查"
        is uniffi.codex_mobile_client.HydratedDividerData.ReviewExited -> "已结束审查"
    }
    val compacting = data is uniffi.codex_mobile_client.HydratedDividerData.ContextCompaction &&
        !(data.isComplete && !isLiveTurn)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BuddySpacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DividerLine(Modifier.weight(1f))
        Text(
            text = label,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = if (compacting) AgentBuddyTheme.warning else AgentBuddyTheme.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        DividerLine(Modifier.weight(1f))
    }
}

@Composable
private fun DividerLine(modifier: Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(AgentBuddyTheme.border),
    )
}

// ── Note ─────────────────────────────────────────────────────────────────────

@Composable
internal fun NoteRow(
    data: uniffi.codex_mobile_client.HydratedNoteData,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.SOFT, shape = BuddyShapes.detailCard, padding = BuddySpacing.md)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        TimelineCardTitle(
            icon = Icons.Outlined.Info,
            title = data.title,
            tint = AgentBuddyTheme.textSecondary,
        )
        if (data.body.isNotBlank()) {
            Text(
                text = data.body,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

@Composable
internal fun ErrorRow(
    data: uniffi.codex_mobile_client.HydratedErrorData,
) {
    // Danger banner style: dangerSurface fill with an icon, so the state is
    // readable without colour.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.DANGER, shape = BuddyShapes.detailCard, padding = BuddySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = "错误",
            tint = AgentBuddyTheme.danger,
            modifier = Modifier.size(BuddySize.icon),
        )
        SelectableConversationText(modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
                Text(
                    text = data.title.ifBlank { "错误" },
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                    color = AgentBuddyTheme.textPrimary,
                )
                Text(
                    text = data.message,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textPrimary,
                )
                data.details?.takeIf { it.isNotBlank() }?.let { details ->
                    Text(
                        text = details,
                        style = buddyTextStyle(BuddyTextStyle.CAPTION),
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
            }
        }
    }
}
