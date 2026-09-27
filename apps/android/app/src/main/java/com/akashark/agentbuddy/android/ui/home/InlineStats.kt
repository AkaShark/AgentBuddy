package com.akashark.agentbuddy.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppSessionSummary

/**
 * Turn statistics for the detailed task card: turns, tool calls, diff size,
 * turn stopwatch and context usage. Every value comes precomputed from the
 * Rust `AppSessionSummary`, so the card stays prop-driven.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineStats(
    session: AppSessionSummary,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = AgentBuddyTheme.textSecondary,
) {
    val stats = session.stats
    val turnCount = stats?.turnCount?.toInt() ?: 0
    val toolCallCount = stats?.toolCallCount?.toInt() ?: 0
    val additions = stats?.diffAdditions?.toInt() ?: 0
    val deletions = stats?.diffDeletions?.toInt() ?: 0
    val startSeconds = session.lastTurnStartMs?.let { it.toDouble() / 1000.0 }
    val endSeconds = if (isActive) null else session.lastTurnEndMs?.let { it.toDouble() / 1000.0 }
    val tokenUsage = session.tokenUsage
    val window = tokenUsage?.contextWindow?.takeIf { it > 0L }
    if (turnCount == 0 && toolCallCount == 0 && additions == 0 && deletions == 0 && startSeconds == null && window == null) {
        return
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        if (turnCount > 0) {
            StatItem(Icons.Outlined.SubdirectoryArrowRight, "$turnCount", "$turnCount 轮", tint)
        }
        if (toolCallCount > 0) {
            StatItem(Icons.Outlined.Code, "$toolCallCount", "$toolCallCount 次工具调用", tint)
        }
        if (additions > 0 || deletions > 0) {
            Text(
                text = "+$additions −$deletions",
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = tint,
                modifier = Modifier.semantics { contentDescription = "新增 $additions 行，删除 $deletions 行" },
            )
        }
        if (startSeconds != null) {
            TurnStopwatchChip(startSeconds = startSeconds, endSeconds = endSeconds, tint = tint)
        }
        if (tokenUsage != null && window != null) {
            val pct = ((tokenUsage.totalTokens.toDouble() / window.toDouble()) * 100.0).toInt()
            Text(
                text = "上下文 $pct%",
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = if (pct > 80) AgentBuddyTheme.warning else tint,
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    description: String,
    tint: Color,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text = value, style = buddyTextStyle(BuddyTextStyle.CAPTION), color = tint)
    }
}
