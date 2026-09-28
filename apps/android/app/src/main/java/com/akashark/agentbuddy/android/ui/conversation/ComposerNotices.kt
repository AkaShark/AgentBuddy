package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

internal const val COMPOSER_DISCONNECTED_MESSAGE = "与主机的连接已断开，草稿和附件会保留。"

/**
 * Persistent notices above the composer: the lost connection (sending is
 * blocked, nothing is cleared), a send that failed (「知道了」 and 「重试」), and
 * a stop request the host refused.
 */
@Composable
internal fun ComposerNotices(
    isConnected: Boolean,
    sendError: String?,
    onRetrySend: (() -> Unit)?,
    onDismissSendError: () -> Unit,
    stopError: String?,
    onDismissStopError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isConnected && sendError == null && stopError == null) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        if (!isConnected) {
            BuddyBanner(
                tone = BuddyBannerTone.WARNING,
                message = COMPOSER_DISCONNECTED_MESSAGE,
                icon = Icons.Outlined.CloudOff,
            )
        }
        sendError?.let { message ->
            ComposerFailedSendNotice(
                message = "没有发送成功：$message",
                onDismiss = onDismissSendError,
                onRetry = if (isConnected) onRetrySend else null,
            )
        }
        stopError?.let { message ->
            BuddyBanner(
                tone = BuddyBannerTone.DANGER,
                message = message,
                actionTitle = "知道了",
                onAction = onDismissStopError,
            )
        }
    }
}

/** Danger notice with two text actions; they wrap under the message at large text sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerFailedSendNotice(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    val tone = BuddyBannerTone.DANGER
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.fill, BuddyShapes.detailCard)
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(tone.defaultIcon, contentDescription = null, tint = tone.foreground, modifier = Modifier.size(BuddySize.icon))
            Text(
                text = message,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier.weight(1f).padding(vertical = BuddySpacing.xs),
            )
        }
        BuddyChromeTypeLimit {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs, Alignment.End),
            ) {
                NoticeTextAction("知道了", onDismiss)
                onRetry?.let { NoticeTextAction("重试", it) }
            }
        }
    }
}

@Composable
private fun NoticeTextAction(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = BuddySize.minHitTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = BuddySpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
            color = AgentBuddyTheme.link,
        )
    }
}
