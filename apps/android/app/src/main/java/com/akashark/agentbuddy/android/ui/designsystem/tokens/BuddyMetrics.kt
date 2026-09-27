package com.akashark.agentbuddy.android.ui.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale from the Mint design system: 4 8 12 16 20 24 32 48 64. */
object BuddySpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val huge = 64.dp

    /** Phone page gutter: 24, dropping to 16 on 320–359dp wide screens. */
    fun pageGutter(width: Dp): Dp = if (width > 0.dp && width < 360.dp) md else xl
}

/**
 * Component radii. Each component has its own rule; there is deliberately no
 * global "corner radius" knob (handoff P0-1).
 */
object BuddyRadius {
    /** Small controls: segmented items, small tiles. */
    val control = 12.dp

    /** Standard buttons (production value; the old 14 from page drafts is retired). */
    val button = 16.dp

    /** Collapsible detail summary card. */
    val detailCard = 16.dp

    /** Result card shown after an action completes. */
    val resultCard = 18.dp

    /** Inline confirmation / approval card. */
    val confirmCard = 20.dp

    /** Task and host cards. Fixed: do not scale with card height. */
    val card = 22.dp

    /** Composer container. Stays fixed while the editor grows. */
    val composer = 24.dp

    /** Top corners of bottom sheets (bottom corners are square). */
    val sheet = 30.dp

    /** Icon tiles inside rows (status tile, project initial). */
    val tile = 16.dp
}

/** Shapes that follow the component radius rules. */
object BuddyShapes {
    val button = RoundedCornerShape(BuddyRadius.button)
    val detailCard = RoundedCornerShape(BuddyRadius.detailCard)
    val resultCard = RoundedCornerShape(BuddyRadius.resultCard)
    val confirmCard = RoundedCornerShape(BuddyRadius.confirmCard)
    val card = RoundedCornerShape(BuddyRadius.card)
    val composer = RoundedCornerShape(BuddyRadius.composer)
    val control = RoundedCornerShape(BuddyRadius.control)

    /** Bottom sheet: 30 on top, square bottom (the platform owns the safe area). */
    val sheet = RoundedCornerShape(topStart = BuddyRadius.sheet, topEnd = BuddyRadius.sheet)

    /**
     * User message bubble: top-start / top-end / bottom-end / bottom-start =
     * 19 / 19 / 5 / 19. The small corner points at the sender; never collapse
     * it to four equal corners.
     */
    val userBubble =
        RoundedCornerShape(
            topStart = 19.dp,
            topEnd = 19.dp,
            bottomEnd = 5.dp,
            bottomStart = 19.dp,
        )
}

object BuddySize {
    /** Default control height (buttons, rows, inputs). */
    val control = 48.dp

    /** Minimum Android touch target; icons stay 20–24 and the hit area grows around them. */
    val minHitTarget = 48.dp

    val icon = 20.dp
    val iconLarge = 24.dp

    /**
     * Visual height of compact pills (model chip, status chip). The layout slot
     * around them still guarantees [minHitTarget].
     */
    val compactPill = 34.dp

    /** Leading tile in list rows. */
    val rowTile = 44.dp

    /** Minimum composer editor height. */
    val composerMinHeight = 48.dp

    /** Minimum list row height (tile + two lines). */
    val listRow = 64.dp
}
