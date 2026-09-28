package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Compact, non-blocking banner for requests that belong to a conversation
 * that is not on screen: 「有 N 个请求等待你确认」 plus host and task. Tapping
 * opens the request ([actionTitle] names what happens); closing only hides it
 * locally and never counts as a denial.
 */
@Composable
fun PendingApprovalBannerContent(
    count: Int,
    detail: String?,
    actionTitle: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuddyChromeTypeLimit {
        Row(
            modifier = modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .shadow(12.dp, BuddyShapes.detailCard, ambientColor = AgentBuddyTheme.floatingShadow, spotColor = AgentBuddyTheme.floatingShadow)
                .clip(BuddyShapes.detailCard)
                .background(AgentBuddyTheme.warningSurface)
                .clickable(role = Role.Button, onClickLabel = actionTitle, onClick = onOpen)
                .padding(start = BuddySpacing.md, end = BuddySpacing.xxs, top = BuddySpacing.xxs, bottom = BuddySpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = AgentBuddyTheme.warning,
                modifier = Modifier.size(BuddySize.icon),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = BuddySpacing.xs)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = ApprovalCopy.pendingCount(count),
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                    color = AgentBuddyTheme.textPrimary,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = buddyTextStyle(BuddyTextStyle.CAPTION),
                        color = AgentBuddyTheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .background(AgentBuddyTheme.action, CircleShape)
                    .padding(horizontal = BuddySpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionTitle,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                    color = AgentBuddyTheme.onAction,
                    maxLines = 1,
                )
            }
            BuddyIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "暂时隐藏",
                onClick = onClose,
                iconSize = 16.dp,
            )
        }
    }
}

/** 「主机 · 任务」 for the banner's second line. */
fun approvalBannerDetail(hostName: String?, taskTitle: String?): String? =
    listOfNotNull(
        hostName?.takeIf { it.isNotBlank() },
        taskTitle?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" · ").ifEmpty { null }
