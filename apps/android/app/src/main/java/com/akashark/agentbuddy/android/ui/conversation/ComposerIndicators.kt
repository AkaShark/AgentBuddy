package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Rate-limit windows and remaining context under the composer (iOS context bar). */
@Composable
internal fun ComposerIndicatorsRow(
    contextPercent: Int?,
    rateLimits: uniffi.codex_mobile_client.RateLimitSnapshot?,
) {
    val hasIndicators = contextPercent != null || rateLimits?.primary != null || rateLimits?.secondary != null
    if (!hasIndicators) return
    BuddyChromeTypeLimit {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BuddySpacing.lg, vertical = BuddySpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            rateLimits?.primary?.let { RateLimitBadge(it) }
            rateLimits?.secondary?.let { RateLimitBadge(it) }
            contextPercent?.let { percent ->
                UsageBadge(
                    label = "上下文",
                    percent = percent,
                    tint = when {
                        percent <= 15 -> AgentBuddyTheme.danger
                        percent <= 35 -> AgentBuddyTheme.warning
                        else -> AgentBuddyTheme.success
                    },
                    description = "上下文剩余 $percent%",
                )
            }
        }
    }
}

@Composable
private fun RateLimitBadge(window: uniffi.codex_mobile_client.RateLimitWindow) {
    val remaining = (100 - window.usedPercent.toInt()).coerceIn(0, 100)
    val label = window.windowDurationMins?.let { mins ->
        when {
            mins >= 1440 -> "${mins / 1440}d"
            mins >= 60 -> "${mins / 60}h"
            else -> "${mins}m"
        }
    } ?: "?"
    UsageBadge(
        label = label,
        percent = remaining,
        tint = when {
            remaining <= 10 -> AgentBuddyTheme.danger
            remaining <= 30 -> AgentBuddyTheme.warning
            else -> AgentBuddyTheme.textSecondary
        },
        description = "$label 限额剩余 $remaining%",
    )
}

/** 「5h [72]」: a label plus a small gauge whose fill is the remaining share. */
@Composable
private fun UsageBadge(
    label: String,
    percent: Int,
    tint: Color,
    description: String,
) {
    val normalized = percent.coerceIn(0, 100)
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = buddyTextStyle(BuddyTextStyle.CAPTION),
            color = AgentBuddyTheme.textSecondary,
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = 20.dp)
                .border(1.dp, tint.copy(alpha = 0.5f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(normalized / 100f)
                        .background(tint.copy(alpha = 0.22f), shape),
                )
            }
            Text(
                text = "$normalized",
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Bold),
                color = tint,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}
