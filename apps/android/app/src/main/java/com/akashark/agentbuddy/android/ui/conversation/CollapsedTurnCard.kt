package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Renders a collapsed turn card with preview and metadata.
 * Tap to expand and show all items.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollapsedTurnCard(
    turn: TranscriptTurn,
    onExpand: () -> Unit,
) {
    val commandText = if (turn.commandCount > 0) "${turn.commandCount} 条命令" else null
    val fileText = if (turn.fileChangeCount > 0) "${turn.fileChangeCount} 次文件改动" else null
    val durationText = if (turn.totalDurationMs > 0) {
        if (turn.totalDurationMs < 1000) "${turn.totalDurationMs}ms"
        else "%.1fs".format(turn.totalDurationMs / 1000.0)
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .timelineDetailCard()
            .clickable(role = Role.Button, onClickLabel = "展开", onClick = onExpand)
            .padding(start = BuddySpacing.md, end = BuddySpacing.md, top = BuddySpacing.sm, bottom = BuddySpacing.xs),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        // User prompt preview
        turn.userPrompt?.let { prompt ->
            Text(
                text = prompt,
                style = buddyTextStyle(BuddyTextStyle.BODY, FontWeight.SemiBold),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Assistant snippet
        turn.assistantSnippet?.let { snippet ->
            Text(
                text = snippet,
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Metadata footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = BuddySpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
            ) {
                commandText?.let { MetadataBadge(Icons.Outlined.Terminal, it) }
                fileText?.let { MetadataBadge(Icons.Outlined.Description, it) }
                durationText?.let { MetadataBadge(Icons.Outlined.Schedule, it) }
            }
            Text(
                text = "展开",
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
            )
            TimelineDisclosureChevron(expanded = false)
        }
    }
}

@Composable
private fun MetadataBadge(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.CAPTION),
            color = AgentBuddyTheme.textSecondary,
            maxLines = 1,
        )
    }
}
