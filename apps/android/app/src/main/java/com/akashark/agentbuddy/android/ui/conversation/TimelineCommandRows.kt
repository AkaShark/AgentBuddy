package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppOperationStatus

// ── Command Execution ────────────────────────────────────────────────────────

@Composable
internal fun CommandExecutionRow(
    data: uniffi.codex_mobile_client.HydratedCommandExecutionData,
    keepExpanded: Boolean,
) {
    var expanded by remember(data.command) { mutableStateOf(keepExpanded) }
    val outputScrollState = rememberScrollState()
    val outputText =
        data.output
            ?.trim('\n')
            ?.takeIf { it.isNotBlank() }
            ?: if (data.status == AppOperationStatus.PENDING || data.status == AppOperationStatus.IN_PROGRESS) {
                "等待输出…"
            } else {
                "无输出"
            }
    val isRunning = data.status == AppOperationStatus.PENDING || data.status == AppOperationStatus.IN_PROGRESS
    val displayedCommand = remember(data.command) { displayCommandText(data.command) }
    val collapsedCommand = remember(data.command) { collapseCommandText(data.command) }

    LaunchedEffect(keepExpanded) {
        expanded = keepExpanded
    }

    LaunchedEffect(outputText, outputScrollState.maxValue, expanded) {
        if (!expanded) return@LaunchedEffect
        if (outputScrollState.maxValue <= 0) return@LaunchedEffect
        outputScrollState.animateScrollTo(outputScrollState.maxValue)
    }

    val codeStyle = buddyTextStyle(BuddyTextStyle.CODE)
    // Output viewport: five code lines tall, growing with the text size.
    val outputMaxHeight = with(LocalDensity.current) {
        (codeStyle.fontSize.toPx() * CodeLineHeightRatio * OutputViewportLines).toDp()
    } + BuddySpacing.xs * 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .timelineDetailCard(),
    ) {
        TimelineCardHeader(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            leading = { TimelineStatusGlyph(data.status, fallbackIcon = Icons.Outlined.Terminal) },
            durationMs = data.durationMs,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = if (expanded) BuddySpacing.xs else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            ) {
                Text(
                    text = "$",
                    style = buddyTextStyle(BuddyTextStyle.CODE, FontWeight.SemiBold),
                    color = AgentBuddyTheme.textSecondary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    text = if (expanded) displayedCommand else collapsedCommand,
                    style = codeStyle,
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = BuddySpacing.md, end = BuddySpacing.md, bottom = BuddySpacing.sm),
            ) {
                LimitedToolTextBlock(outputText, previewFromTail = isRunning) { display ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = outputMaxHeight)
                            .clip(timelineCodeShape(nested = true))
                            .background(timelineCodeFill(nested = true))
                            .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
                    ) {
                        SelectableConversationText {
                            Text(
                                text = display,
                                style = codeStyle,
                                color = AgentBuddyTheme.textBody,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(outputScrollState),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── File Change ──────────────────────────────────────────────────────────────

@Composable
internal fun FileChangeRow(
    data: uniffi.codex_mobile_client.HydratedFileChangeData,
) {
    val summary = remember(data.changes) {
        buildFileChangeSummary(data)
    }
    val diffChanges = remember(data.changes) {
        data.changes.filter { it.diff.isNotBlank() }
    }

    ToolCardShell(
        summary = summary.plainText,
        summaryAnnotated = summary.annotatedText,
        status = data.status,
        fallbackIcon = Icons.Outlined.Description,
    ) {
        if (diffChanges.isEmpty() && data.changes.isNotEmpty()) {
            ListSection("文件", data.changes.map { toolCardWorkspaceTitle(it.path) })
        }
        diffChanges.forEach { change ->
            DiffSection(
                label = if (diffChanges.size > 1) toolCardWorkspaceTitle(change.path) else "",
                content = change.diff,
            )
        }
    }
}

private data class FileChangeSummary(
    val plainText: String,
    val annotatedText: AnnotatedString,
)

private fun buildFileChangeSummary(
    data: uniffi.codex_mobile_client.HydratedFileChangeData,
): FileChangeSummary {
    if (data.changes.isEmpty()) {
        return FileChangeSummary(
            plainText = "文件改动",
            annotatedText = AnnotatedString("文件改动"),
        )
    }

    val additions = data.changes.sumOf { it.additions.toInt() }
    val deletions = data.changes.sumOf { it.deletions.toInt() }
    val hasCountSummary = additions > 0 || deletions > 0

    if (data.changes.size == 1) {
        val change = data.changes.first()
        val verb = fileChangeVerb(change.kind)
        val filename = toolCardWorkspaceTitle(change.path)
        if (!hasCountSummary) {
            return FileChangeSummary(
                plainText = "$verb $filename",
                annotatedText = AnnotatedString("$verb $filename"),
            )
        }
        val plainText = "$verb $filename +$additions −$deletions"
        val annotatedText = buildAnnotatedString {
            withStyle(SpanStyle(color = AgentBuddyTheme.textSecondary)) {
                append("$verb ")
            }
            withStyle(SpanStyle(color = AgentBuddyTheme.textPrimary)) {
                append(filename)
            }
            withStyle(SpanStyle(color = AgentBuddyTheme.success)) {
                append(" +$additions")
            }
            withStyle(SpanStyle(color = AgentBuddyTheme.danger)) {
                append(" −$deletions")
            }
        }
        return FileChangeSummary(plainText = plainText, annotatedText = annotatedText)
    }

    if (!hasCountSummary) {
        return FileChangeSummary(
            plainText = "修改 ${data.changes.size} 个文件",
            annotatedText = AnnotatedString("修改 ${data.changes.size} 个文件"),
        )
    }

    val plainText = "修改 ${data.changes.size} 个文件 · +$additions −$deletions"
    val annotatedText = buildAnnotatedString {
        append("修改 ${data.changes.size} 个文件 · ")
        withStyle(SpanStyle(color = AgentBuddyTheme.success)) {
            append("+$additions")
        }
        withStyle(SpanStyle(color = AgentBuddyTheme.danger)) {
            append(" −$deletions")
        }
    }
    return FileChangeSummary(plainText = plainText, annotatedText = annotatedText)
}

private fun fileChangeVerb(kind: String): String = when (kind.lowercase()) {
    "add" -> "新增"
    "delete" -> "删除"
    "update" -> "编辑"
    else -> "修改"
}

private const val CodeLineHeightRatio = 22f / 14f
private const val OutputViewportLines = 5

private fun displayCommandText(command: String): String {
    val trimmed = command.trim()
    return if (trimmed.isEmpty()) "命令" else trimmed
}

private fun collapseCommandText(command: String): String {
    val collapsed = displayCommandText(command)
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (collapsed.isEmpty()) "命令" else collapsed
}
