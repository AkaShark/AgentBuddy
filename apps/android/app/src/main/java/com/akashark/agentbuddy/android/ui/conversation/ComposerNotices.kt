package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing

internal const val COMPOSER_DISCONNECTED_MESSAGE = "与主机的连接已断开，草稿和附件会保留。"

/**
 * Persistent notices above the composer: the lost connection (sending is
 * blocked, nothing is cleared), a send that failed (with 「重试」), and a stop
 * request the host refused.
 */
@Composable
internal fun ComposerNotices(
    isConnected: Boolean,
    sendError: String?,
    onRetrySend: (() -> Unit)?,
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
            BuddyBanner(
                tone = BuddyBannerTone.DANGER,
                message = "没有发送成功：$message",
                actionTitle = if (onRetrySend != null && isConnected) "重试" else null,
                onAction = onRetrySend,
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
