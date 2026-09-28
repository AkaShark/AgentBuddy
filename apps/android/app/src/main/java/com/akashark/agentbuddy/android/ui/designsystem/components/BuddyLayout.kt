package com.akashark.agentbuddy.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** "正在进行 01" style header: heading text, optional count, optional trailing action. */
@Composable
fun BuddySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = BuddySize.minHitTarget),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.HEADING),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (count != null) {
            Text(
                text = "%02d".format(count),
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * Page title block: optional eyebrow, title, optional subtitle, optional
 * trailing action aligned with the title.
 */
@Composable
fun BuddyPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    titleStyle: BuddyTextStyle = BuddyTextStyle.DISPLAY,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            if (eyebrow != null) {
                Text(
                    text = eyebrow,
                    style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
            Text(
                text = title,
                style = buddyTextStyle(titleStyle),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
        }
        trailing()
    }
}

/**
 * Tile + title + subtitle + accessory row used for recent tasks, projects and
 * settings-like lists. At least 64dp tall; title and subtitle up to two lines.
 */
@Composable
fun BuddyListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    tile: (@Composable () -> Unit)? = null,
    accessory: @Composable () -> Unit = { BuddyChevron() },
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.listRow)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
                .padding(vertical = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        accessory()
    }
}

/** Trailing chevron for navigable rows. */
@Composable
fun BuddyChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = AgentBuddyTheme.textSecondary,
        modifier = modifier.size(BuddySize.icon),
    )
}

/** Hairline divider in the decorative border colour. */
@Composable
fun BuddyDivider(
    modifier: Modifier = Modifier,
    startIndent: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = startIndent)
                .height(1.dp)
                .background(AgentBuddyTheme.border)
                .clearAndSetSemantics {},
    )
}

/** Horizontal gap helper for rows built from design tokens. */
@Composable
fun BuddyHSpacer(width: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.width(width))
}
