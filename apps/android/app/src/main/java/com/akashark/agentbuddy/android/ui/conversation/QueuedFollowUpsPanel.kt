package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppQueuedFollowUpKind
import uniffi.codex_mobile_client.AppQueuedFollowUpPreview

private data class QueuedFollowUpUiStyle(
    val title: String,
    val tint: Color,
    val background: Color,
    val border: Color,
)

@Composable
internal fun QueuedFollowUpsPreviewPanel(
    previews: List<AppQueuedFollowUpPreview>,
    onSteer: (AppQueuedFollowUpPreview) -> Unit,
    onDelete: (AppQueuedFollowUpPreview) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "下一项排队中",
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = previews.size.toString(),
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(AgentBuddyTheme.surface.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        previews.forEach { preview ->
            QueuedFollowUpCard(
                preview = preview,
                onSteer = onSteer,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun QueuedFollowUpCard(
    preview: AppQueuedFollowUpPreview,
    onSteer: (AppQueuedFollowUpPreview) -> Unit,
    onDelete: (AppQueuedFollowUpPreview) -> Unit,
) {
    val style = queuedFollowUpUiStyle(preview.kind)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, style.border, RoundedCornerShape(12.dp))
            .background(style.background, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .background(style.tint.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(style.tint, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = style.title,
                    color = style.tint,
                    fontSize = AgentBuddyTextStyle.caption2.scaled,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = preview.text,
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (preview.kind == AppQueuedFollowUpKind.MESSAGE) {
            Text(
                text = "\u21b3 \u5f15\u5bfc",
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(AgentBuddyTheme.surface.copy(alpha = 0.96f), RoundedCornerShape(999.dp))
                    .clickable { onSteer(preview) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        IconButton(
            onClick = { onDelete(preview) },
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除排队的后续项",
                tint = AgentBuddyTheme.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun queuedFollowUpUiStyle(kind: AppQueuedFollowUpKind): QueuedFollowUpUiStyle =
    when (kind) {
        AppQueuedFollowUpKind.MESSAGE ->
            QueuedFollowUpUiStyle(
                title = "排队的消息",
                tint = AgentBuddyTheme.accent,
                background = AgentBuddyTheme.accent.copy(alpha = 0.08f),
                border = AgentBuddyTheme.accent.copy(alpha = 0.24f),
            )

        AppQueuedFollowUpKind.PENDING_STEER ->
            QueuedFollowUpUiStyle(
                title = "引导排队中",
                tint = AgentBuddyTheme.accentStrong,
                background = AgentBuddyTheme.accentStrong.copy(alpha = 0.10f),
                border = AgentBuddyTheme.accentStrong.copy(alpha = 0.28f),
            )

        AppQueuedFollowUpKind.RETRYING_STEER ->
            QueuedFollowUpUiStyle(
                title = "重试引导中",
                tint = AgentBuddyTheme.warning,
                background = AgentBuddyTheme.warning.copy(alpha = 0.10f),
                border = AgentBuddyTheme.warning.copy(alpha = 0.28f),
            )
    }
