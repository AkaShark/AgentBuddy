package com.akashark.agentbuddy.android.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

private val PlaceholderMaxWidth = 480.dp

@Composable
internal fun LoadingPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(
            color = AgentBuddyTheme.textSecondary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "正在加载应用…",
            style = buddyTextStyle(BuddyTextStyle.LABEL),
            color = AgentBuddyTheme.textSecondary,
        )
    }
}

@Composable
internal fun BrokenPlaceholder(onDelete: () -> Unit) {
    PlaceholderFrame {
        BuddyEmptyState(
            icon = Icons.Outlined.WarningAmber,
            title = "该应用的文件已丢失",
            message = "删除它以清除该条目。",
            actionTitle = "删除应用",
            actionIcon = Icons.Outlined.Delete,
            actionKind = BuddyButtonKind.DESTRUCTIVE,
            onAction = onDelete,
        )
    }
}

@Composable
internal fun FailurePlaceholder(message: String, onRetry: () -> Unit) {
    PlaceholderFrame {
        BuddyEmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "无法加载该应用",
            message = message,
            actionTitle = "重新加载",
            actionIcon = Icons.Outlined.Refresh,
            actionKind = BuddyButtonKind.SECONDARY,
            onAction = onRetry,
        )
    }
}

@Composable
private fun PlaceholderFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = BuddySpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.widthIn(max = PlaceholderMaxWidth).fillMaxWidth()) { content() }
    }
}

/**
 * Veil over the running widget while an update is in flight, with a
 * progress strip along the top. The strip only moves when motion is
 * allowed; touches still reach the widget.
 */
@Composable
internal fun ShimmerOverlay() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AgentBuddyTheme.background.copy(alpha = 0.35f))
                .semantics { contentDescription = "正在更新应用" },
    ) {
        if (buddyReduceMotion) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(AgentBuddyTheme.link),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = AgentBuddyTheme.link,
                trackColor = AgentBuddyTheme.surfaceSoft,
            )
        }
    }
}
