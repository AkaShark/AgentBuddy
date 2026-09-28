package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.common.FormattedText
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BUDDY_CHROME_MAX_FONT_SCALE
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyRadius
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Row actions of 全部任务: the same menu opens from 「…」 and from a long press. */
internal class SessionRowActions(
    val onOpen: () -> Unit,
    val onToggleNode: () -> Unit,
    val onFork: () -> Unit,
    val onRename: () -> Unit,
    val onArchive: () -> Unit,
)

/**
 * Task row in the home-row style: status tile, two-line title, 「状态 · 项目 ·
 * 主机 · 时间」 subtitle, fork / sub-agent relation and tags, a fork-tree
 * toggle and a visible 「…」 menu so long press is never the only entry.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionRow(
    row: SessionRowUi,
    actions: SessionRowActions,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember(row.key) { mutableStateOf(false) }
    val indent = BuddySpacing.md * minOf(row.depth, 4)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (row.isActive) {
                    Modifier
                        .background(AgentBuddyTheme.surface, BuddyShapes.detailCard)
                        .border(1.dp, AgentBuddyTheme.border, BuddyShapes.detailCard)
                } else {
                    Modifier
                },
            )
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.depth > 0) {
            Box(
                Modifier
                    .padding(end = BuddySpacing.xs)
                    .width(1.dp)
                    .heightIn(min = BuddySize.listRow)
                    .fillMaxHeight()
                    .background(AgentBuddyTheme.border)
                    .clearAndSetSemantics {},
            )
        }
        // Enlarged text: the status tile moves above the text so the title
        // and subtitle keep the full row width.
        val stacked = LocalDensity.current.fontScale * LocalTextScale.current > BUDDY_CHROME_MAX_FONT_SCALE
        val rowModifier = Modifier
            .weight(1f)
            .clip(BuddyShapes.detailCard)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "打开任务",
                onLongClickLabel = "更多操作",
                onClick = actions.onOpen,
                onLongClick = { showMenu = true },
            )
            .semantics(mergeDescendants = true) {
                stateDescription = row.state.title
                selected = row.isActive
            }
            .heightIn(min = BuddySize.listRow)
            .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.sm)
        if (stacked) {
            Column(modifier = rowModifier, verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                SessionStatusTile(row.state)
                SessionRowTexts(row)
            }
        } else {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionStatusTile(row.state)
                SessionRowTexts(row, Modifier.weight(1f))
            }
        }
        if (row.hasChildren) {
            BuddyIconButton(
                icon = if (row.isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (row.isCollapsed) "展开子任务" else "收起子任务",
                onClick = actions.onToggleNode,
                tint = AgentBuddyTheme.textSecondary,
                iconSize = 20.dp,
            )
        }
        Box {
            BuddyIconButton(
                icon = Icons.Outlined.MoreHoriz,
                contentDescription = "更多操作",
                onClick = { showMenu = true },
                tint = AgentBuddyTheme.textSecondary,
                iconSize = 20.dp,
            )
            SessionRowMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                actions = actions,
            )
        }
    }
}

/** Title, subtitle, relation line and tags of a [SessionRow]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionRowTexts(row: SessionRowUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
        val titleStyle = buddyTextStyle(BuddyTextStyle.HEADING)
        ProvideTextStyle(titleStyle) {
            FormattedText(
                text = row.title,
                color = AgentBuddyTheme.textPrimary,
                fontSize = titleStyle.fontSize,
                maxLines = 2,
            )
        }
        Text(
            text = row.subtitle,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        row.relationLine?.let {
            Text(
                text = it,
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.isActive || row.subagentLabel != null || row.isFork) {
            BuddyChromeTypeLimit {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (row.isActive) SessionsRowTag("当前任务", Icons.Outlined.Visibility)
                    if (row.subagentLabel != null) {
                        SessionsRowTag(row.subagentLabel, Icons.Outlined.Groups)
                    } else if (row.isFork) {
                        SessionsRowTag("分叉", Icons.AutoMirrored.Outlined.CallSplit)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRowMenu(expanded: Boolean, onDismiss: () -> Unit, actions: SessionRowActions) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = AgentBuddyTheme.surface,
    ) {
        SessionRowMenuItem("分叉", Icons.AutoMirrored.Outlined.CallSplit) {
            onDismiss()
            actions.onFork()
        }
        SessionRowMenuItem("重命名", Icons.Outlined.Edit) {
            onDismiss()
            actions.onRename()
        }
        SessionRowMenuItem("归档", Icons.Outlined.Archive, destructive = true) {
            onDismiss()
            actions.onArchive()
        }
    }
}

@Composable
private fun SessionRowMenuItem(
    title: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) AgentBuddyTheme.danger else AgentBuddyTheme.textPrimary
    DropdownMenuItem(
        text = { Text(title, style = buddyTextStyle(BuddyTextStyle.BODY), color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.icon)) },
        onClick = onClick,
    )
}

/** Status tile: quiet (idle / completed) on surface with a hairline, others on their state fill. */
@Composable
internal fun SessionStatusTile(state: BuddyTaskState) {
    val quiet = state == BuddyTaskState.COMPLETED || state == BuddyTaskState.IDLE
    BuddyIconTile(
        content = BuddyTileContent.Symbol(state.icon),
        fill = if (quiet) AgentBuddyTheme.surface else state.fill,
        foreground = if (quiet) AgentBuddyTheme.textSecondary else state.foreground,
        modifier = if (quiet) {
            Modifier.border(1.dp, AgentBuddyTheme.border, RoundedCornerShape(BuddyRadius.tile))
        } else {
            Modifier
        },
    )
}

/** Small non-interactive tag inside a row: text plus an icon, never colour alone. */
@Composable
private fun SessionsRowTag(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .background(AgentBuddyTheme.surfaceSoft, CircleShape)
            .padding(horizontal = BuddySpacing.xs, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(12.dp))
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = AgentBuddyTheme.textSecondary,
            maxLines = 1,
        )
    }
}
