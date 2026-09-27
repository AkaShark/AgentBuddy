package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.PendingApproval

/** Lines a command shows before 「展开」. */
internal const val APPROVAL_COMMAND_COLLAPSED_LINES = 6

private const val MAX_LISTED_FILES = 5

/**
 * What will run and where: the question, the host's reason, the command,
 * files, access scope and working directory. Long content is bounded so the
 * decision buttons stay reachable. Copying never runs anything.
 */
@Composable
internal fun ApprovalDetails(
    approval: PendingApproval,
    hostName: String?,
    filePaths: List<String>,
    formatPath: (String) -> String,
    onViewDiff: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        Text(
            text = ApprovalCopy.question(approval.kind, hostName),
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textPrimary,
        )
        approval.reason.nonBlank()?.let { reason ->
            Text(
                text = reason,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        approval.command.nonBlank()?.let { command ->
            ApprovalCodeBlock(text = command, label = "命令")
        }
        val paths = filePaths.ifEmpty { listOfNotNull(approval.path.nonBlank()) }
        if (paths.isNotEmpty()) {
            val shown = paths.take(MAX_LISTED_FILES).joinToString("\n") { formatPath(it) }
            val extra = paths.size - MAX_LISTED_FILES
            ApprovalDetailRow(
                label = "文件",
                value = if (extra > 0) "$shown\n还有 $extra 个文件" else shown,
                // Already capped at MAX_LISTED_FILES paths; let long paths wrap.
                maxLines = Int.MAX_VALUE,
            )
        }
        approval.grantRoot.nonBlank()?.let { root ->
            ApprovalDetailRow(label = "访问范围", value = formatPath(root))
        }
        approval.cwd.nonBlank()?.let { cwd ->
            ApprovalDetailRow(label = "工作目录", value = formatPath(cwd))
        }
        if (onViewDiff != null && approval.kind == ApprovalKind.FILE_CHANGE) {
            ApprovalInlineAction(
                title = ApprovalCopy.VIEW_DIFF,
                icon = Icons.Outlined.Difference,
                onClick = onViewDiff,
            )
        }
    }
}

@Composable
private fun ApprovalDetailRow(
    label: String,
    value: String,
    maxLines: Int = 4,
) {
    Column(
        modifier = Modifier
            .padding(top = BuddySpacing.xxs)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = AgentBuddyTheme.textSecondary,
        )
        Text(
            text = value,
            style = buddyTextStyle(BuddyTextStyle.CODE),
            color = AgentBuddyTheme.textPrimary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Monospaced command with a copy button. Short commands take their natural
 * height; long ones stop at [APPROVAL_COMMAND_COLLAPSED_LINES] lines with an
 * explicit 「展开」. Only this block scrolls sideways, never the page.
 */
@Composable
internal fun ApprovalCodeBlock(
    text: String,
    label: String,
    initiallyExpanded: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }
    var expanded by remember(text) { mutableStateOf(initiallyExpanded) }
    val lineCount = remember(text) { text.lines().size }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, BuddyShapes.control),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = BuddySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            ApprovalInlineAction(
                title = if (copied) "已复制" else "复制",
                icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                onClick = {
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = BuddySpacing.sm, end = BuddySpacing.sm, bottom = BuddySpacing.sm),
        ) {
            Text(
                text = text,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = AgentBuddyTheme.textPrimary,
                softWrap = false,
                maxLines = if (expanded) Int.MAX_VALUE else APPROVAL_COMMAND_COLLAPSED_LINES,
                overflow = TextOverflow.Clip,
            )
        }
        if (lineCount > APPROVAL_COMMAND_COLLAPSED_LINES) {
            ApprovalInlineAction(
                title = if (expanded) "收起" else "展开",
                icon = null,
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(start = BuddySpacing.xxs),
            )
        }
    }
}

/** Text action in link colour with a 48dp hit area. */
@Composable
internal fun ApprovalInlineAction(
    title: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = if (enabled) AgentBuddyTheme.link else AgentBuddyTheme.onDisabled
    Row(
        modifier = modifier
            .heightIn(min = BuddySize.minHitTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
            color = tint,
        )
    }
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
