package com.akashark.agentbuddy.android.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Button hierarchy from the Mint design system. A screen keeps one strongest
 * action: use [PRIMARY] (or [BRAND] on neutral pages) once per view.
 */
enum class BuddyButtonKind {
    /** action / onAction: the main decision ("允许一次", "开始任务"). */
    PRIMARY,

    /** brand / onBrand: emphasis without being the final step ("扫码连接"). */
    BRAND,

    /** surface / textPrimary with a hairline border ("拒绝", "稍后处理"). */
    SECONDARY,

    /** surfaceSoft / textPrimary, no border ("在这台主机开始任务"). */
    SOFT,

    /** Text-only link action. */
    QUIET,

    /** dangerSurface / danger. */
    DESTRUCTIVE,
}

internal fun BuddyButtonKind.containerColor(enabled: Boolean): Color =
    if (!enabled) {
        if (this == BuddyButtonKind.QUIET) Color.Transparent else AgentBuddyTheme.disabled
    } else {
        when (this) {
            BuddyButtonKind.PRIMARY -> AgentBuddyTheme.action
            BuddyButtonKind.BRAND -> AgentBuddyTheme.brand
            BuddyButtonKind.SECONDARY -> AgentBuddyTheme.surface
            BuddyButtonKind.SOFT -> AgentBuddyTheme.surfaceSoft
            BuddyButtonKind.QUIET -> Color.Transparent
            BuddyButtonKind.DESTRUCTIVE -> AgentBuddyTheme.dangerSurface
        }
    }

internal fun BuddyButtonKind.contentColor(enabled: Boolean): Color =
    if (!enabled) {
        AgentBuddyTheme.onDisabled
    } else {
        when (this) {
            BuddyButtonKind.PRIMARY -> AgentBuddyTheme.onAction
            BuddyButtonKind.BRAND -> AgentBuddyTheme.onBrand
            BuddyButtonKind.SECONDARY, BuddyButtonKind.SOFT -> AgentBuddyTheme.textPrimary
            BuddyButtonKind.QUIET -> AgentBuddyTheme.link
            BuddyButtonKind.DESTRUCTIVE -> AgentBuddyTheme.danger
        }
    }

/**
 * 48dp minimum height, 20dp horizontal padding, radius 16, Label type. Grows
 * with the font size instead of clipping (two lines at most). While
 * [isLoading] it shows a spinner, keeps its label so the width does not jump,
 * and ignores repeat taps.
 */
@Composable
fun BuddyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BuddyButtonKind = BuddyButtonKind.PRIMARY,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    fullWidth: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = buddyReduceMotion
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.98f else 1f,
        animationSpec = BuddyMotion.PRESS.spec(reduceMotion),
        label = "buddyButtonPress",
    )
    val looksEnabled = enabled || isLoading
    val content = kind.contentColor(looksEnabled)
    val shape = BuddyShapes.button

    Row(
        modifier =
            modifier
                .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minHeight = BuddySize.control)
                .scale(pressScale)
                .graphicsLayer { alpha = if (pressed) 0.9f else 1f }
                .clip(shape)
                .background(kind.containerColor(looksEnabled), shape)
                .then(
                    if (kind == BuddyButtonKind.SECONDARY) {
                        Modifier.border(1.dp, AgentBuddyTheme.border, shape)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && !isLoading,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { if (isLoading) stateDescription = "处理中" }
                .padding(
                    horizontal = if (kind == BuddyButtonKind.QUIET) BuddySpacing.xs else BuddySpacing.lg,
                    vertical = BuddySpacing.xs,
                ),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = content,
                strokeWidth = 2.dp,
            )
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(BuddySize.icon))
        }
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
            color = content,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        }
    }
}
