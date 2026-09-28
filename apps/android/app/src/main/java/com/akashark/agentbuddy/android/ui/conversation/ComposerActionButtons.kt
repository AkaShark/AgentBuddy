package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Send (idle), 「排队」 (turn running), or progress while a send is in flight. */
@Composable
internal fun ComposerSendButton(
    controls: ComposerControlsState,
    onSend: () -> Unit,
) {
    when {
        controls.sendAction == ComposerSendAction.QUEUE -> ComposerPill(
            text = "排队",
            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
            enabled = controls.canSend,
            contentDescription = "加入队列，当前步骤完成后发送",
            onClick = onSend,
        )
        controls.isCreating -> Box(
            modifier = Modifier
                .size(BuddySize.minHitTarget)
                .semantics { contentDescription = "正在发送" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(38.dp).background(AgentBuddyTheme.action, CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = AgentBuddyTheme.onAction,
                    strokeWidth = 2.dp,
                )
            }
        }
        else -> BuddyIconButton(
            icon = Icons.Outlined.ArrowUpward,
            contentDescription = "发送",
            onClick = onSend,
            tone = BuddyIconButtonTone.ACTION,
            diameter = 38.dp,
            enabled = controls.canSend,
        )
    }
}

/** Explicit stop while a turn runs; 「正在停止…」 until the snapshot says the turn is over. */
@Composable
internal fun ComposerStopButton(
    controls: ComposerControlsState,
    onStop: () -> Unit,
) {
    when {
        controls.isStopping -> Row(
            modifier = Modifier
                .heightIn(min = BuddySize.minHitTarget)
                .padding(horizontal = BuddySpacing.xs)
                .clearAndSetSemantics { contentDescription = "正在停止任务" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = AgentBuddyTheme.textSecondary,
                strokeWidth = 2.dp,
            )
            Text(
                text = "正在停止…",
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 1,
            )
        }
        controls.stopShowsLabel -> ComposerPill(
            text = "停止",
            icon = Icons.Filled.Stop,
            enabled = controls.canStop,
            contentDescription = "停止任务",
            onClick = onStop,
        )
        else -> BuddyIconButton(
            icon = Icons.Filled.Stop,
            contentDescription = "停止任务",
            onClick = onStop,
            tone = BuddyIconButtonTone.SOFT,
            iconSize = 16.dp,
            enabled = controls.canStop,
        )
    }
}

/** Action-filled capsule (36dp visual) inside a 48dp hit area. */
@Composable
internal fun ComposerPill(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val fill = if (enabled) AgentBuddyTheme.action else AgentBuddyTheme.disabled
    val content = if (enabled) AgentBuddyTheme.onAction else AgentBuddyTheme.onDisabled
    Box(
        modifier = Modifier
            .sizeIn(minWidth = BuddySize.minHitTarget, minHeight = BuddySize.minHitTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .background(fill, CircleShape)
                .padding(horizontal = BuddySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                color = content,
                maxLines = 1,
            )
        }
    }
}
