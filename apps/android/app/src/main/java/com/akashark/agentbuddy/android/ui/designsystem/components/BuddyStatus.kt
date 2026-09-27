package com.akashark.agentbuddy.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Presentation state of a task card. It is derived from Rust-owned snapshot
 * data in the feature layer; this type never decides task state itself.
 * Connection state is shown separately ([BuddyConnectionState]), so a lost
 * connection never turns a remote task into "failed" or "completed".
 */
enum class BuddyTaskState(
    val title: String,
    /** Shape carries meaning too, so state never depends on colour alone. */
    val icon: ImageVector,
) {
    IDLE("空闲", Icons.Outlined.Circle),
    RUNNING("进行中", Icons.Outlined.Autorenew),
    STOPPING("正在停止…", Icons.Outlined.StopCircle),
    AWAITING_APPROVAL("等待你确认", Icons.Outlined.Shield),
    AWAITING_INPUT("等待你回复", Icons.Outlined.ChatBubbleOutline),
    COMPLETED("已完成", Icons.Outlined.CheckCircle),
    FAILED("失败", Icons.Outlined.ErrorOutline),
    INTERRUPTED("已停止", Icons.Outlined.PauseCircle),
    ;

    val fill: Color
        get() =
            when (this) {
                RUNNING, STOPPING -> AgentBuddyTheme.brand
                AWAITING_APPROVAL, AWAITING_INPUT -> AgentBuddyTheme.warningSurface
                COMPLETED -> AgentBuddyTheme.successSurface
                FAILED -> AgentBuddyTheme.dangerSurface
                IDLE, INTERRUPTED -> AgentBuddyTheme.surfaceSoft
            }

    val foreground: Color
        get() =
            when (this) {
                RUNNING, STOPPING -> AgentBuddyTheme.onBrand
                AWAITING_APPROVAL, AWAITING_INPUT -> AgentBuddyTheme.warning
                COMPLETED -> AgentBuddyTheme.success
                FAILED -> AgentBuddyTheme.danger
                IDLE, INTERRUPTED -> AgentBuddyTheme.textSecondary
            }

    val needsAttention: Boolean
        get() = this == AWAITING_APPROVAL || this == AWAITING_INPUT
}

enum class BuddyConnectionState(val title: String) {
    DISCOVERING("正在查找主机…"),
    CONNECTING("正在连接…"),
    CONNECTED("已连接"),
    DISCONNECTED("已断开"),
    FAILED("连接失败"),
    ;

    val dotColor: Color
        get() =
            when (this) {
                CONNECTED -> AgentBuddyTheme.success
                CONNECTING, DISCOVERING -> AgentBuddyTheme.warning
                DISCONNECTED -> AgentBuddyTheme.textMuted
                FAILED -> AgentBuddyTheme.danger
            }
}

/** Filled status capsule: icon + text on a semantic surface. */
@Composable
fun BuddyStatusPill(
    state: BuddyTaskState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    title: String = state.title,
) {
    Row(
        modifier =
            modifier
                .background(state.fill, CircleShape)
                .padding(
                    horizontal = if (compact) BuddySpacing.xs else BuddySpacing.sm,
                    vertical = if (compact) 3.dp else 6.dp,
                ).clearAndSetSemantics { contentDescription = title },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(state.icon, contentDescription = null, tint = state.foreground, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
        Text(
            text = title,
            style = buddyTextStyle(if (compact) BuddyTextStyle.CAPTION else BuddyTextStyle.LABEL, FontWeight.Medium),
            color = state.foreground,
            maxLines = 1,
        )
    }
}

/**
 * Unfilled status line used inside a card that already carries the colour
 * ("○ 进行中" on the brand task card).
 */
@Composable
fun BuddyStatusLabel(
    state: BuddyTaskState,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    title: String = state.title,
) {
    val color = tint ?: state.foreground
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(state.icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Medium),
            color = color,
            maxLines = 1,
        )
    }
}

/** Dot + text connection indicator ("● 已连接"). */
@Composable
fun BuddyConnectionPill(
    state: BuddyConnectionState,
    modifier: Modifier = Modifier,
    title: String = state.title,
    filled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .then(
                    if (filled) {
                        Modifier
                            .defaultMinSize(minHeight = BuddySize.compactPill)
                            .background(AgentBuddyTheme.surfaceSoft, CircleShape)
                            .padding(horizontal = BuddySpacing.sm)
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(state.dotColor, CircleShape))
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Medium),
            color = AgentBuddyTheme.textPrimary,
            maxLines = 1,
        )
    }
}
