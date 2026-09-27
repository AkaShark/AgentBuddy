package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Plain data for the conversation header (the gallery renders it from fixtures). */
data class ConversationHeaderState(
    val title: String,
    val subtitle: String,
    val connection: BuddyConnectionState,
    val isPlanMode: Boolean = false,
    val isFullAccess: Boolean = false,
    val isFastMode: Boolean = false,
    val isReloading: Boolean = false,
    val canShowInfo: Boolean = true,
)

/** Header actions; every former header control lives in the 「…」 menu. */
class ConversationHeaderActions(
    val onBack: () -> Unit,
    /** Title tap: shows or hides the partner / model panel (as the old model label did). */
    val onToggleModelPanel: () -> Unit,
    val onOpenModelPanel: () -> Unit,
    val onOpenPermissions: () -> Unit,
    val onOpenPlanMode: () -> Unit,
    val onReload: () -> Unit,
    val onOpenInfo: () -> Unit,
)

/**
 * Two-line Mint header: task title, then 「搭档 · 主机」 with the connection
 * dot (plus the state in words when not connected), the plan marker and the
 * full-access warning. Back and 「…」 are 48dp targets.
 */
@Composable
fun ConversationHeaderContent(
    state: ConversationHeaderState,
    actions: ConversationHeaderActions,
    modifier: Modifier = Modifier,
    menuInitiallyExpanded: Boolean = false,
) {
    BuddyChromeTypeLimit {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = BuddySpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BuddyIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                onClick = actions.onBack,
                iconSize = 22.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BuddySize.minHitTarget)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "选择搭档、模型与权限",
                        onClick = actions.onToggleModelPanel,
                    )
                    .padding(horizontal = BuddySpacing.xxs, vertical = BuddySpacing.xxs),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.title,
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                HeaderSubtitleRow(state)
            }
            HeaderMenu(state = state, actions = actions, initiallyExpanded = menuInitiallyExpanded)
        }
    }
}

@Composable
private fun HeaderSubtitleRow(state: ConversationHeaderState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionDot(state.connection)
        if (state.isFastMode) {
            Icon(
                Icons.Outlined.Bolt,
                contentDescription = "快速模式",
                tint = AgentBuddyTheme.warning,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = state.subtitle,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = if (state.connection == BuddyConnectionState.FAILED) AgentBuddyTheme.danger else AgentBuddyTheme.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (state.isPlanMode) {
            Text(
                text = "计划",
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.SemiBold),
                color = AgentBuddyTheme.onBrand,
                maxLines = 1,
                modifier = Modifier
                    .background(AgentBuddyTheme.brand, CircleShape)
                    .padding(horizontal = 6.dp),
            )
        }
        if (state.isFullAccess) {
            Icon(
                Icons.Outlined.LockOpen,
                contentDescription = "完全访问",
                tint = AgentBuddyTheme.danger,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 8dp dot; pulses while connecting unless the user removed animations. */
@Composable
private fun ConnectionDot(connection: BuddyConnectionState) {
    val pulse = connection == BuddyConnectionState.CONNECTING && !buddyReduceMotion
    val alpha = if (pulse) {
        val transition = rememberInfiniteTransition(label = "headerDotPulse")
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000), RepeatMode.Reverse),
            label = "headerDotAlpha",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(8.dp)
            .background(connection.dotColor.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun HeaderMenu(
    state: ConversationHeaderState,
    actions: ConversationHeaderActions,
    initiallyExpanded: Boolean,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Box {
        if (state.isReloading) {
            Box(Modifier.size(BuddySize.minHitTarget), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AgentBuddyTheme.textSecondary,
                )
            }
        } else {
            BuddyIconButton(
                icon = Icons.Outlined.MoreHoriz,
                contentDescription = "更多",
                onClick = { expanded = true },
                iconSize = 22.dp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AgentBuddyTheme.surface,
        ) {
            val item: @Composable (String, ImageVector, Boolean, Boolean, () -> Unit) -> Unit =
                { label, icon, enabled, checked, onClick ->
                    DropdownMenuItem(
                        text = {
                            Text(label, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal))
                        },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(BuddySize.icon)) },
                        trailingIcon = if (checked) {
                            { Icon(Icons.Outlined.Check, contentDescription = "已开启", modifier = Modifier.size(BuddySize.icon)) }
                        } else {
                            null
                        },
                        enabled = enabled,
                        onClick = {
                            expanded = false
                            onClick()
                        },
                    )
                }
            item("模型与推理", Icons.Outlined.Tune, true, false, actions.onOpenModelPanel)
            item("权限", Icons.Outlined.Shield, true, false, actions.onOpenPermissions)
            item("计划模式", Icons.Outlined.Checklist, true, state.isPlanMode, actions.onOpenPlanMode)
            item("重新加载", Icons.Outlined.Refresh, !state.isReloading, false, actions.onReload)
            if (state.canShowInfo) item("任务信息", Icons.Outlined.Info, true, false, actions.onOpenInfo)
        }
    }
}
