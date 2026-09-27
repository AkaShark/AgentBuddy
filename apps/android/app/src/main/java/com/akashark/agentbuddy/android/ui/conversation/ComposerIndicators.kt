package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

@Composable
internal fun ComposerIndicatorsRow(
    contextPercent: Int?,
    rateLimits: uniffi.codex_mobile_client.RateLimitSnapshot?,
) {
    val hasIndicators = contextPercent != null || rateLimits?.primary != null || rateLimits?.secondary != null
    if (hasIndicators) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 52.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            rateLimits?.primary?.let { window ->
                RateLimitBadge(window)
            }
            rateLimits?.secondary?.let { window ->
                RateLimitBadge(window)
            }
            contextPercent?.let {
                ContextBadge(it)
            }
        }
    }
}

// ── Rate Limit Badge (matching iOS RateLimitBadgeView) ───────────────────────

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
    val tint = when {
        remaining <= 10 -> AgentBuddyTheme.danger
        remaining <= 30 -> AgentBuddyTheme.warning
        else -> AgentBuddyTheme.textMuted
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = AgentBuddyTheme.textSecondary,
            fontSize = 10f.scaled,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AgentBuddyTheme.monoFont,
        )
        ContextBadge(percent = remaining, tint = tint)
    }
}

// ── Context Badge (matching iOS ContextBadgeView) ────────────────────────────

@Composable
private fun ContextBadge(
    percent: Int,
    tint: Color = when {
        percent <= 15 -> AgentBuddyTheme.danger
        percent <= 35 -> AgentBuddyTheme.warning
        else -> AgentBuddyTheme.success
    },
) {
    val normalizedPercent = percent.coerceIn(0, 100)

    Box(
        modifier = Modifier
            .size(width = 35.dp, height = 16.dp)
            .background(Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.2.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Fill bar
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = normalizedPercent / 100f)
                .background(tint.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
        )
        // Number overlay
        Text(
            text = "$normalizedPercent",
            color = tint,
            fontSize = 9f.scaled,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = AgentBuddyTheme.monoFont,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
