package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppOperationStatus

internal fun toolCardWorkspaceTitle(path: String): String {
    return path
        .trimEnd('/')
        .substringAfterLast('/')
        .ifBlank { path }
}

@Composable
internal fun ToolCardShell(
    summary: String,
    summaryAnnotated: AnnotatedString? = null,
    accent: Color,
    status: AppOperationStatus,
    durationMs: Long? = null,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(summary, status) {
        mutableStateOf(defaultExpanded || status == AppOperationStatus.FAILED)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .border(0.5.dp, AgentBuddyTheme.border, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(status)
            Spacer(Modifier.width(8.dp))
            Text(
                text = summaryAnnotated ?: AnnotatedString(summary),
                color = AgentBuddyTheme.textSystem,
                fontSize = AgentBuddyTextStyle.body.scaled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            durationMs?.takeIf { it > 0 }?.let { ms ->
                Spacer(Modifier.width(8.dp))
                DurationChip(toolCardFormatDuration(ms), statusTint(status))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (expanded) "▲" else "▼",
                color = accent,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.Bold,
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

// ── Shared Helpers ───────────────────────────────────────────────────────────

@Composable
internal fun StatusIcon(status: AppOperationStatus) {
    when (status) {
        AppOperationStatus.IN_PROGRESS -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = AgentBuddyTheme.accent,
            )
        }
        AppOperationStatus.COMPLETED -> {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已完成",
                tint = AgentBuddyTheme.success,
                modifier = Modifier.size(14.dp),
            )
        }
        AppOperationStatus.FAILED -> {
            Icon(
                Icons.Default.Error,
                contentDescription = "失败",
                tint = AgentBuddyTheme.danger,
                modifier = Modifier.size(14.dp),
            )
        }
        else -> {
            Icon(
                Icons.Default.HourglassEmpty,
                contentDescription = "未知",
                tint = AgentBuddyTheme.textMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

internal fun statusTint(status: AppOperationStatus): Color {
    return when (status) {
        AppOperationStatus.COMPLETED -> AgentBuddyTheme.success
        AppOperationStatus.IN_PROGRESS -> AgentBuddyTheme.warning
        AppOperationStatus.FAILED -> AgentBuddyTheme.danger
        else -> AgentBuddyTheme.textMuted
    }
}

@Composable
internal fun DurationChip(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .border(0.5.dp, tint.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
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
