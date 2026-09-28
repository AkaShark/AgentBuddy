package com.akashark.agentbuddy.android.ui.homeshell.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Actions available on a task. Shared by the visible 「…」 button and the
 * long-press on the card, so long press is never the only way in.
 */
class TaskActionHandlers(
    val open: (HomeTaskItem) -> Unit,
    val reply: (HomeTaskItem) -> Unit,
    val stop: (HomeTaskItem) -> Unit,
    val fork: (HomeTaskItem) -> Unit,
    val togglePin: (HomeTaskItem) -> Unit,
    val hide: (HomeTaskItem) -> Unit,
    val delete: (HomeTaskItem) -> Unit,
) {
    companion object {
        /** No-op handlers for fixtures. */
        val None = TaskActionHandlers({}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * The 「…」 trigger (48dp hit area) with the task menu anchored to it. The
 * caller owns [expanded] so a long-press on the card opens the same menu.
 */
@Composable
fun TaskActionsMenuButton(
    item: HomeTaskItem,
    handlers: TaskActionHandlers,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AgentBuddyTheme.textSecondary,
) {
    Box(modifier) {
        BuddyIconButton(
            icon = Icons.Outlined.MoreHoriz,
            contentDescription = "更多操作",
            onClick = { onExpandedChange(true) },
            tint = tint,
            iconSize = 20.dp,
        )
        TaskActionsDropdown(item, handlers, expanded, onDismiss = { onExpandedChange(false) })
    }
}

@Composable
private fun TaskActionsDropdown(
    item: HomeTaskItem,
    handlers: TaskActionHandlers,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val isRunning = item.session.hasActiveTurn
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = AgentBuddyTheme.surface,
    ) {
        TaskMenuItem("回复", Icons.AutoMirrored.Outlined.Reply) {
            onDismiss()
            handlers.reply(item)
        }
        if (isRunning) {
            TaskMenuItem("停止", Icons.Outlined.StopCircle, enabled = item.state != BuddyTaskState.STOPPING) {
                onDismiss()
                handlers.stop(item)
            }
        }
        TaskMenuItem("分叉", Icons.AutoMirrored.Outlined.CallSplit, enabled = !isRunning) {
            onDismiss()
            handlers.fork(item)
        }
        TaskMenuItem(if (item.isPinned) "取消固定" else "固定", Icons.Outlined.PushPin) {
            onDismiss()
            handlers.togglePin(item)
        }
        TaskMenuItem("隐藏", Icons.Outlined.VisibilityOff) {
            onDismiss()
            handlers.hide(item)
        }
        HorizontalDivider(color = AgentBuddyTheme.border)
        TaskMenuItem("删除", Icons.Outlined.Delete, destructive = true) {
            onDismiss()
            handlers.delete(item)
        }
    }
}

@Composable
private fun TaskMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) AgentBuddyTheme.danger else AgentBuddyTheme.textPrimary
    DropdownMenuItem(
        text = { Text(text, style = buddyTextStyle(BuddyTextStyle.BODY)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        enabled = enabled,
        colors =
            MenuDefaults.itemColors(
                textColor = color,
                leadingIconColor = color,
                disabledTextColor = AgentBuddyTheme.onDisabled,
                disabledLeadingIconColor = AgentBuddyTheme.onDisabled,
            ),
    )
}
