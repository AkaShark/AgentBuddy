package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

/**
 * Renders a collapsed turn card with preview and metadata.
 * Tap to expand and show all items.
 */
@Composable
fun CollapsedTurnCard(
    turn: TranscriptTurn,
    onExpand: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(10.dp))
            .clickable(onClick = onExpand)
            .padding(10.dp),
    ) {
        // User prompt preview
        turn.userPrompt?.let { prompt ->
            Text(
                text = prompt,
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.footnote.scaled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Assistant snippet
        turn.assistantSnippet?.let { snippet ->
            Text(
                text = snippet,
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Metadata footer
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (turn.commandCount > 0) {
                MetadataBadge("${turn.commandCount} cmd", AgentBuddyTheme.toolCallCommand)
            }
            if (turn.fileChangeCount > 0) {
                MetadataBadge("${turn.fileChangeCount} files", AgentBuddyTheme.toolCallFileChange)
            }
            if (turn.totalDurationMs > 0) {
                val dur = if (turn.totalDurationMs < 1000) "${turn.totalDurationMs}ms"
                else "%.1fs".format(turn.totalDurationMs / 1000.0)
                MetadataBadge(dur, AgentBuddyTheme.textMuted)
            }
            Spacer(Modifier.weight(1f))
            Text("点击展开", color = AgentBuddyTheme.textMuted, fontSize = AgentBuddyTextStyle.caption2.scaled)
        }
    }
}

@Composable
private fun MetadataBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        fontSize = AgentBuddyTextStyle.caption2.scaled,
        fontWeight = FontWeight.Medium,
    )
}
