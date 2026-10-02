package com.akashark.agentbuddy.android.ui.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyRadius
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Semantic card fills. Content is grouped with whitespace first; a card is
 * used when something is a unit the user acts on (task, host, confirmation).
 */
enum class BuddySurfaceTone {
    /** surface with a hairline border (host cards, detail summaries). */
    SURFACE,

    /** surfaceSoft, no border (project hero, result card). */
    SOFT,

    /** brand (the current / running task). */
    BRAND,

    /** warningSurface (confirmation needed). */
    WARNING,

    SUCCESS,
    DANGER,
    ;

    val fill: Color
        get() =
            when (this) {
                SURFACE -> AgentBuddyTheme.surface
                SOFT -> AgentBuddyTheme.surfaceSoft
                BRAND -> AgentBuddyTheme.brand
                WARNING -> AgentBuddyTheme.warningSurface
                SUCCESS -> AgentBuddyTheme.successSurface
                DANGER -> AgentBuddyTheme.dangerSurface
            }

    val hasBorder: Boolean
        get() = this == SURFACE
}

/** Wraps content in a card with a semantic fill and a fixed component radius. */
fun Modifier.buddyCard(
    tone: BuddySurfaceTone = BuddySurfaceTone.SURFACE,
    shape: Shape = BuddyShapes.card,
    padding: Dp? = BuddySpacing.lg,
): Modifier {
    var result = clip(shape).background(tone.fill, shape)
    if (tone.hasBorder) result = result.border(1.dp, AgentBuddyTheme.border, shape)
    return if (padding != null) result.padding(padding) else result
}

/** Page background for Mint screens. */
fun Modifier.buddyPageBackground(): Modifier = background(AgentBuddyTheme.background)

enum class BuddyChipTone {
    /** surfaceSoft (on page or white cards). */
    SOFT,

    /** Subtle contrasting fill on the brand card. */
    ON_BRAND,

    /** surface with border. */
    OUTLINE,
}

/** Capsule tag for project / partner names. End caps are half the visual height. */
@Composable
fun BuddyChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: BuddyChipTone = BuddyChipTone.SOFT,
) {
    val fill =
        when (tone) {
            BuddyChipTone.SOFT -> AgentBuddyTheme.surfaceSoft
            BuddyChipTone.ON_BRAND -> AgentBuddyTheme.brandChipFill
            BuddyChipTone.OUTLINE -> AgentBuddyTheme.surface
        }
    val content = if (tone == BuddyChipTone.ON_BRAND) AgentBuddyTheme.onBrand else AgentBuddyTheme.textPrimary
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = 32.dp)
                .background(fill, CircleShape)
                .then(
                    if (tone == BuddyChipTone.OUTLINE) Modifier.border(1.dp, AgentBuddyTheme.border, CircleShape) else Modifier,
                ).padding(horizontal = BuddySpacing.sm + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        }
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Leading content of [BuddyIconTile]. */
sealed interface BuddyTileContent {
    data class Symbol(val icon: ImageVector) : BuddyTileContent

    data class Initial(val letter: String) : BuddyTileContent

    data object BrandMark : BuddyTileContent
}

/** Rounded square tile used at the leading edge of rows and cards. */
@Composable
fun BuddyIconTile(
    content: BuddyTileContent,
    modifier: Modifier = Modifier,
    fill: Color = AgentBuddyTheme.surfaceSoft,
    foreground: Color = AgentBuddyTheme.textPrimary,
    size: Dp = BuddySize.rowTile,
) {
    val radius = if (content == BuddyTileContent.BrandMark) {
        size * (BuddyRadius.splashMark.value / BuddySize.splashMark.value)
    } else {
        min(BuddyRadius.tile, size * 0.36f)
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier =
            modifier
                .size(size)
                .background(fill, shape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            is BuddyTileContent.Symbol ->
                Icon(content.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(size * 0.46f))
            is BuddyTileContent.Initial ->
                Text(
                    text = content.letter,
                    color = foreground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.42f).sp,
                    maxLines = 1,
                )
            BuddyTileContent.BrandMark ->
                Icon(BuddyLinkIcon, contentDescription = null, tint = foreground, modifier = Modifier.size(size * 0.80f))
        }
    }
}

/**
 * Tappable context chip (host / project / partner): surfaceSoft capsule at
 * compact-pill height inside a 48dp hit area. The press ripple is drawn on the
 * capsule itself; drawn on the taller hit area it did not match the pill.
 */
@Composable
fun BuddyContextChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
) {
    val content = if (enabled) AgentBuddyTheme.textPrimary else AgentBuddyTheme.onDisabled
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .heightIn(min = BuddySize.minHitTarget)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = BuddySize.compactPill)
                    .clip(CircleShape)
                    .background(if (enabled) AgentBuddyTheme.surfaceSoft else AgentBuddyTheme.disabled)
                    .indication(interaction, LocalIndication.current)
                    .padding(horizontal = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
        }
    }
}
