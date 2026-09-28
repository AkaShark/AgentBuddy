package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Widest content column for sheets and pages on tablets. */
internal val MintContentMaxWidth: Dp = 640.dp

/** Page gutter for the current window width (24, or 16 under 360dp). */
@Composable
internal fun mintPageGutter(): Dp = BuddySpacing.pageGutter(LocalConfiguration.current.screenWidthDp.dp)

/**
 * Title row of a Mint sheet: title, optional extra actions and one quiet text
 * action ("取消" / "关闭"). The row is compact chrome, so its type is capped;
 * the optional [subtitle] below keeps growing with the font size.
 */
@Composable
internal fun DiscoverySheetHeader(
    title: String,
    actionTitle: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
        BuddyChromeTypeLimit {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = buddyTextStyle(BuddyTextStyle.TITLE),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 2,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                trailing()
                BuddyButton(
                    text = actionTitle,
                    onClick = onAction,
                    kind = BuddyButtonKind.QUIET,
                    enabled = actionEnabled,
                    fullWidth = false,
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

/**
 * Sheet layout: header, a scrolling body that shrinks to its content, and an
 * optional bottom bar that stays visible (the primary action). Content is
 * capped at [MintContentMaxWidth] and centred on wide screens.
 */
@Composable
internal fun DiscoverySheetScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    contentSpacing: Dp = BuddySpacing.xl,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = mintPageGutter()
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = MintContentMaxWidth).fillMaxWidth()) {
            Box(Modifier.padding(start = gutter, end = gutter - BuddySpacing.xs, bottom = BuddySpacing.sm)) {
                header()
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter)
                    .padding(top = BuddySpacing.xs, bottom = BuddySpacing.xl),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                content = content,
            )
            if (bottomBar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AgentBuddyTheme.background)
                        .padding(horizontal = gutter, vertical = BuddySpacing.sm),
                ) {
                    bottomBar()
                }
            }
        }
    }
}

/**
 * Mint alert: heading title, body text (and optional extra content), one
 * primary / brand / destructive confirm button and a secondary dismiss
 * button. The buttons wrap onto separate lines when they do not fit.
 */
@Composable
internal fun MintAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = AgentBuddyTheme.textPrimary,
    confirmKind: BuddyButtonKind = BuddyButtonKind.PRIMARY,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    dismissTitle: String? = "取消",
    onDismiss: () -> Unit = onDismissRequest,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = BuddyShapes.card,
        containerColor = AgentBuddyTheme.surface,
        icon = icon?.let {
            { Icon(it, contentDescription = null, tint = iconTint) }
        },
        title = {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            ) {
                if (message != null) {
                    Text(
                        text = message,
                        style = buddyTextStyle(BuddyTextStyle.BODY),
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
                content?.invoke(this)
            }
        },
        confirmButton = {
            BuddyButton(
                text = confirmTitle,
                onClick = onConfirm,
                kind = confirmKind,
                enabled = confirmEnabled,
                isLoading = confirmLoading,
                fullWidth = false,
                modifier = Modifier.widthIn(min = BuddySize.minHitTarget * 2),
            )
        },
        dismissButton = dismissTitle?.let {
            {
                BuddyButton(
                    text = it,
                    onClick = onDismiss,
                    kind = BuddyButtonKind.SECONDARY,
                    enabled = !confirmLoading,
                    fullWidth = false,
                    modifier = Modifier.widthIn(min = BuddySize.minHitTarget * 2),
                )
            }
        },
    )
}
