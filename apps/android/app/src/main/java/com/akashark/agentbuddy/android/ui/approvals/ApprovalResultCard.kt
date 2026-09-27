package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Shown after a decision (radius 18, soft surface). It describes what was
 * sent, not what the task has done; the timeline shows the real progress.
 */
@Composable
fun ApprovalResultCard(
    kind: ApprovalOutcomeKind,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .buddyCard(
                BuddySurfaceTone.SOFT,
                shape = BuddyShapes.resultCard,
                padding = null,
            )
            .padding(start = BuddySpacing.lg, top = BuddySpacing.md, bottom = BuddySpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = outcomeIcon(kind),
            contentDescription = null,
            tint = AgentBuddyTheme.textPrimary,
            modifier = Modifier.padding(top = BuddySpacing.sm).size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = BuddySpacing.xs)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        ) {
            Text(
                text = ApprovalCopy.outcomeTitle(kind),
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
            )
            Text(
                text = ApprovalCopy.outcomeDetail(kind),
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        BuddyIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "关闭",
            onClick = onDismiss,
            iconSize = 16.dp,
        )
    }
}

private fun outcomeIcon(kind: ApprovalOutcomeKind): ImageVector =
    when (kind) {
        ApprovalOutcomeKind.ALLOWED_ONCE, ApprovalOutcomeKind.ALLOWED_FOR_SESSION -> Icons.Outlined.CheckCircle
        ApprovalOutcomeKind.DENIED -> Icons.Outlined.HighlightOff
        ApprovalOutcomeKind.ABORTED -> Icons.Outlined.StopCircle
        ApprovalOutcomeKind.RESOLVED_ELSEWHERE -> Icons.Outlined.Sync
    }
