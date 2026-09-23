package com.akashark.agentbuddy.android.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentIconView
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppSessionSummary

/**
 * Line showing "time ago · host · model" on the left and [InlineStats] on
 * the right. Left text truncates with ellipsis; right chips are pinned.
 *
 * Ref: HomeDashboardView.swift:772-815 (`modelBadgeLine`).
 */
@Composable
fun ModelBadgeLine(
    session: AppSessionSummary,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val timeAgo = HomeDashboardSupport.relativeTime(session.updatedAt)
    val model = session.model.trim()
    val agentLabel = session.agentDisplayLabel?.trim()?.takeIf { it.isNotEmpty() }

    Row(
        modifier = modifier.padding(top = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BadgeText(text = timeAgo, color = AgentBuddyTheme.textMuted.copy(alpha = 0.8f))
            SeparatorDot()
            Icon(
                imageVector = Icons.Outlined.Storage,
                contentDescription = null,
                tint = AgentBuddyTheme.accent.copy(alpha = 0.5f),
                modifier = Modifier.size(10.dp),
            )
            BadgeText(
                text = HomeDashboardSupport.runtimeLabel(session.agentRuntimeKind),
                color = AgentBuddyTheme.accent.copy(alpha = 0.75f),
            )
            BadgeText(
                text = session.serverDisplayName,
                color = AgentBuddyTheme.accent.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.isNotEmpty()) {
                SeparatorDot()
                AgentIconView(kind = session.agentRuntimeKind, sizeDp = 12)
                BadgeText(
                    text = model,
                    color = AgentBuddyTheme.textSecondary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (session.isFork) {
                SeparatorDot()
                BadgeText(text = "分叉", color = AgentBuddyTheme.warning.copy(alpha = 0.8f))
            }
            if (session.isSubagent && agentLabel != null) {
                SeparatorDot()
                BadgeText(
                    text = agentLabel,
                    color = AgentBuddyTheme.accent.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))
        InlineStats(
            session = session,
            isActive = isActive,
            modifier = Modifier.wrapContentWidth(),
        )
    }
}

@Composable
private fun BadgeText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        color = color,
        fontFamily = AgentBuddyTheme.monoFont,
        fontSize = BADGE_FONT_SP.scaled,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
private fun SeparatorDot() {
    Text(
        text = "\u00b7",
        color = AgentBuddyTheme.textMuted.copy(alpha = 0.5f),
        fontFamily = AgentBuddyTheme.monoFont,
        fontSize = BADGE_FONT_SP.scaled,
    )
}

private const val BADGE_FONT_SP = 10f
