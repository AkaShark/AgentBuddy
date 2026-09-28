package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppQueuedFollowUpKind
import uniffi.codex_mobile_client.AppQueuedFollowUpPreview

private data class QueuedFollowUpUiStyle(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val background: Color,
)

/**
 * Messages the shared store holds behind the running turn: count, text
 * previews, 「干预」 (send into the running turn now, messages only) and remove.
 * Both actions are confirmed by the next snapshot, not patched here.
 */
@Composable
internal fun QueuedFollowUpsPreviewPanel(
    previews: List<AppQueuedFollowUpPreview>,
    onSteer: (AppQueuedFollowUpPreview) -> Unit,
    onDelete: (AppQueuedFollowUpPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs)
            .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard, padding = null)
            .padding(start = BuddySpacing.sm, end = BuddySpacing.sm, bottom = BuddySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        BuddyChromeTypeLimit {
            Row(
                modifier = Modifier.heightIn(min = BuddySize.minHitTarget),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = AgentBuddyTheme.link, modifier = Modifier.size(18.dp))
                Text(
                    text = "下一项排队中",
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                    color = AgentBuddyTheme.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = previews.size.toString(),
                    style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.SemiBold),
                    color = AgentBuddyTheme.textSecondary,
                    modifier = Modifier
                        .background(AgentBuddyTheme.surfaceSoft, CircleShape)
                        .padding(horizontal = BuddySpacing.xs, vertical = 2.dp)
                        .semantics { contentDescription = "${previews.size} 条排队中" },
                )
            }
        }
        previews.forEach { preview ->
            QueuedFollowUpRow(preview = preview, onSteer = onSteer, onDelete = onDelete)
        }
    }
}

@Composable
private fun QueuedFollowUpRow(
    preview: AppQueuedFollowUpPreview,
    onSteer: (AppQueuedFollowUpPreview) -> Unit,
    onDelete: (AppQueuedFollowUpPreview) -> Unit,
) {
    val style = queuedFollowUpUiStyle(preview.kind)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.background, BuddyShapes.control)
            .padding(start = BuddySpacing.sm),
    ) {
        BuddyChromeTypeLimit {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(style.icon, contentDescription = null, tint = style.tint, modifier = Modifier.size(16.dp))
                Text(
                    text = style.title,
                    style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.SemiBold),
                    color = style.tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
                when (preview.kind) {
                    AppQueuedFollowUpKind.MESSAGE -> SteerButton(label = "干预", icon = Icons.Outlined.SubdirectoryArrowRight, enabled = true) {
                        onSteer(preview)
                    }
                    AppQueuedFollowUpKind.PENDING_STEER -> SteerButton(label = "干预中", icon = Icons.Outlined.Check, enabled = false) {}
                    AppQueuedFollowUpKind.RETRYING_STEER -> Unit
                }
                BuddyIconButton(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = "移除排队消息",
                    onClick = { onDelete(preview) },
                    iconSize = 18.dp,
                    tint = AgentBuddyTheme.textSecondary,
                )
            }
        }
        Text(
            text = preview.text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = BuddySpacing.sm, bottom = BuddySpacing.sm),
        )
    }
}

@Composable
private fun SteerButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val content = if (enabled) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary
    Box(
        modifier = Modifier
            .heightIn(min = BuddySize.minHitTarget)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "立即把这条消息发给正在运行的任务",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = BuddySize.compactPill)
                .background(AgentBuddyTheme.surface, CircleShape)
                .border(1.dp, AgentBuddyTheme.borderControl, CircleShape)
                .padding(horizontal = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(label, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold), color = content, maxLines = 1)
        }
    }
}

private fun queuedFollowUpUiStyle(kind: AppQueuedFollowUpKind): QueuedFollowUpUiStyle =
    when (kind) {
        AppQueuedFollowUpKind.MESSAGE -> QueuedFollowUpUiStyle(
            title = "排队中的消息",
            icon = Icons.AutoMirrored.Outlined.Chat,
            tint = AgentBuddyTheme.textSecondary,
            background = AgentBuddyTheme.surfaceSoft,
        )
        AppQueuedFollowUpKind.PENDING_STEER -> QueuedFollowUpUiStyle(
            title = "干预已排队",
            icon = Icons.AutoMirrored.Outlined.Redo,
            tint = AgentBuddyTheme.link,
            background = AgentBuddyTheme.surfaceSoft,
        )
        AppQueuedFollowUpKind.RETRYING_STEER -> QueuedFollowUpUiStyle(
            title = "正在重试干预",
            icon = Icons.Outlined.Refresh,
            tint = AgentBuddyTheme.warning,
            background = AgentBuddyTheme.warningSurface,
        )
    }
