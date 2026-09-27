package com.akashark.agentbuddy.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize

enum class BuddyIconButtonTone {
    /** Icon only, no fill. */
    PLAIN,

    /** surfaceSoft fill (composer "+", secondary header actions). */
    SOFT,

    /** surface fill with a hairline border (floating header actions). */
    SURFACE,

    /** action fill (send, voice). */
    ACTION,

    /** brand fill (selected / emphasised state). */
    BRAND,
}

private fun BuddyIconButtonTone.fill(enabled: Boolean): Color =
    when (this) {
        BuddyIconButtonTone.PLAIN -> Color.Transparent
        BuddyIconButtonTone.SOFT -> if (enabled) AgentBuddyTheme.surfaceSoft else AgentBuddyTheme.disabled
        BuddyIconButtonTone.SURFACE -> AgentBuddyTheme.surface
        BuddyIconButtonTone.ACTION -> if (enabled) AgentBuddyTheme.action else AgentBuddyTheme.disabled
        BuddyIconButtonTone.BRAND -> if (enabled) AgentBuddyTheme.brand else AgentBuddyTheme.disabled
    }

private fun BuddyIconButtonTone.tint(enabled: Boolean): Color =
    if (!enabled) {
        AgentBuddyTheme.onDisabled
    } else {
        when (this) {
            BuddyIconButtonTone.ACTION -> AgentBuddyTheme.onAction
            BuddyIconButtonTone.BRAND -> AgentBuddyTheme.onBrand
            else -> AgentBuddyTheme.textPrimary
        }
    }

/**
 * Circular icon button. The visual circle can be small (32–44dp) but the hit
 * area is always at least 48×48dp, and it is part of layout so neighbours never
 * overlap.
 */
@Composable
fun BuddyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BuddyIconButtonTone = BuddyIconButtonTone.PLAIN,
    diameter: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = BuddySize.minHitTarget, minHeight = BuddySize.minHitTarget)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        BuddyIconDisc(icon, tone, diameter, iconSize, enabled, tint)
    }
}

/** Icon button with an on/off state (read out as a toggle by TalkBack). */
@Composable
fun BuddyIconToggleButton(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = BuddySize.minHitTarget, minHeight = BuddySize.minHitTarget)
                .clip(CircleShape)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        BuddyIconDisc(
            icon = icon,
            tone = if (checked) BuddyIconButtonTone.BRAND else BuddyIconButtonTone.SOFT,
            diameter = diameter,
            iconSize = iconSize,
            enabled = enabled,
            tint = null,
        )
    }
}

@Composable
private fun BuddyIconDisc(
    icon: ImageVector,
    tone: BuddyIconButtonTone,
    diameter: Dp,
    iconSize: Dp,
    enabled: Boolean,
    tint: Color?,
) {
    Box(
        modifier =
            Modifier
                .size(diameter)
                .background(tone.fill(enabled), CircleShape)
                .then(
                    if (tone == BuddyIconButtonTone.SURFACE) {
                        Modifier.border(1.dp, AgentBuddyTheme.border, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint ?: tone.tint(true) else tone.tint(false),
            modifier = Modifier.size(iconSize),
        )
    }
}
