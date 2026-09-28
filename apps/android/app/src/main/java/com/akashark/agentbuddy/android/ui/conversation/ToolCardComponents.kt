package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppOperationStatus

internal fun toolCardWorkspaceTitle(path: String): String {
    return path
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { path }
}

// ── Mint timeline styling ────────────────────────────────────────────────────

/** Collapsible detail summary card: surface, 1dp border, radius 16. */
internal fun Modifier.timelineDetailCard(): Modifier =
    buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard, padding = null)

/**
 * Fill behind code, commands and output. Inside a surface card it is
 * `surfaceSoft` (the light-mode code fill equals the card, so it would vanish);
 * directly on the page it is `codeBackground`.
 */
internal fun timelineCodeFill(nested: Boolean): Color =
    if (nested) AgentBuddyTheme.surfaceSoft else AgentBuddyTheme.codeBackground

/** Code surface radius: 12 inside a card, 16 on the page. */
internal fun timelineCodeShape(nested: Boolean): Shape =
    if (nested) BuddyShapes.control else BuddyShapes.detailCard

@Composable
internal fun ToolCardShell(
    summary: String,
    summaryAnnotated: AnnotatedString? = null,
    status: AppOperationStatus,
    durationMs: Long? = null,
    defaultExpanded: Boolean = false,
    fallbackIcon: ImageVector = Icons.Outlined.Build,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(summary, status) {
        mutableStateOf(defaultExpanded || status == AppOperationStatus.FAILED)
    }

    Column(modifier = Modifier.fillMaxWidth().timelineDetailCard()) {
        TimelineCardHeader(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            leading = { TimelineStatusGlyph(status, fallbackIcon) },
            durationMs = durationMs,
        ) {
            Text(
                text = summaryAnnotated ?: AnnotatedString(summary),
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = BuddySpacing.md, end = BuddySpacing.md, bottom = BuddySpacing.md),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                content = content,
            )
        }
    }
}

/**
 * 48dp tall header of a detail card: leading status glyph, title, optional
 * duration and the disclosure chevron. The whole row toggles the card.
 */
@Composable
internal fun TimelineCardHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    leading: @Composable () -> Unit,
    durationMs: Long? = null,
    title: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BuddySize.minHitTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "收起" else "展开",
                onClick = onToggle,
            )
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        title()
        durationMs?.takeIf { it > 0 }?.let { ms ->
            TimelineDurationText(toolCardFormatDuration(ms))
        }
        TimelineDisclosureChevron(expanded)
    }
}

// ── Shared Helpers ───────────────────────────────────────────────────────────

/**
 * Leading status glyph: spinner, check, exclamation or clock, so the state
 * never depends on colour alone. With reduced motion the spinner becomes a
 * static icon.
 */
@Composable
internal fun TimelineStatusGlyph(
    status: AppOperationStatus,
    fallbackIcon: ImageVector = Icons.Outlined.Build,
) {
    val description = operationStatusLabel(status)
    Box(
        modifier = Modifier
            .size(BuddySize.icon)
            .then(
                if (description != null) {
                    Modifier.clearAndSetSemantics { contentDescription = description }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val tint = statusGlyphTint(status)
        when (status) {
            AppOperationStatus.IN_PROGRESS -> {
                if (buddyReduceMotion) {
                    Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon))
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = tint,
                    )
                }
            }
            AppOperationStatus.COMPLETED ->
                Icon(Icons.Outlined.Check, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon))
            AppOperationStatus.FAILED ->
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon))
            AppOperationStatus.PENDING ->
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon))
            AppOperationStatus.DECLINED ->
                Icon(Icons.Outlined.Block, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon))
            AppOperationStatus.UNKNOWN ->
                Icon(fallbackIcon, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon))
        }
    }
}

/** Spoken / visible name of an operation status; null when unknown. */
internal fun operationStatusLabel(status: AppOperationStatus): String? =
    when (status) {
        AppOperationStatus.IN_PROGRESS -> "进行中"
        AppOperationStatus.COMPLETED -> "已完成"
        AppOperationStatus.FAILED -> "失败"
        AppOperationStatus.PENDING -> "等待中"
        AppOperationStatus.DECLINED -> "已拒绝"
        AppOperationStatus.UNKNOWN -> null
    }

private fun statusGlyphTint(status: AppOperationStatus): Color =
    when (status) {
        AppOperationStatus.COMPLETED -> AgentBuddyTheme.success
        AppOperationStatus.IN_PROGRESS -> AgentBuddyTheme.warning
        AppOperationStatus.FAILED -> AgentBuddyTheme.danger
        else -> AgentBuddyTheme.textSecondary
    }

internal fun statusTint(status: AppOperationStatus): Color {
    return when (status) {
        AppOperationStatus.COMPLETED -> AgentBuddyTheme.success
        AppOperationStatus.IN_PROGRESS -> AgentBuddyTheme.warning
        AppOperationStatus.FAILED -> AgentBuddyTheme.danger
        else -> AgentBuddyTheme.textMuted
    }
}

/** Duration next to a summary ("1.2s"); the glyph carries the status. */
@Composable
internal fun TimelineDurationText(text: String) {
    Text(
        text = text,
        style = buddyTextStyle(BuddyTextStyle.CAPTION),
        color = AgentBuddyTheme.textSecondary,
        maxLines = 1,
    )
}

/** Decorative disclosure chevron for collapsible rows. */
@Composable
internal fun TimelineDisclosureChevron(expanded: Boolean) {
    Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = null,
        tint = AgentBuddyTheme.textSecondary,
        modifier = Modifier.size(BuddySize.icon),
    )
}

/** Quiet text action inside the timeline ("展开" / "收起"): link colour, 48dp tall. */
@Composable
internal fun TimelineLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = BuddySize.minHitTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = BuddySpacing.xxs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
            color = AgentBuddyTheme.link,
        )
    }
}

internal fun toolCardFormatDuration(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> "%.1fs".format(ms / 1000.0)
        else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
    }
}
