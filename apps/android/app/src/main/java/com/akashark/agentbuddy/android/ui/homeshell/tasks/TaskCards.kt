package com.akashark.agentbuddy.android.ui.homeshell.tasks

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.BuddySwipeTone
import com.akashark.agentbuddy.android.ui.common.SwipeAction
import com.akashark.agentbuddy.android.ui.common.SwipeableRow
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChipTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyStatusLabel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.home.InlineStats

/**
 * Card for a task that is running, stopping or waiting on the user. Running
 * tasks use the brand surface; tasks that need a decision use the warning
 * surface so they read as "your turn" at a glance.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ActiveTaskCard(
    item: HomeTaskItem,
    handlers: TaskActionHandlers,
    modifier: Modifier = Modifier,
    showsDetail: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val attention = item.state.needsAttention
    val primary = if (attention) AgentBuddyTheme.textPrimary else AgentBuddyTheme.onBrand
    val title = HomeTaskPresentation.title(item.session.displayTitle)
    val step = HomeTaskPresentation.latestStep(item.session, item.session.displayTitle)
    val activity = HomeTaskPresentation.activitySummary(item.session)

    TaskSwipe(item, handlers, modifier, BuddyShapes.card) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .buddyCard(if (attention) BuddySurfaceTone.WARNING else BuddySurfaceTone.BRAND, padding = null)
                    .taskGestures(item, handlers, onLongPress = { menuOpen = true })
                    .padding(start = BuddySpacing.lg, end = BuddySpacing.lg, top = BuddySpacing.xxs, bottom = BuddySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BuddyStatusLabel(state = item.state, tint = if (attention) null else primary)
                Spacer(Modifier.weight(1f))
                TaskTimeOrProgress(item, primary)
                TaskActionsMenuButton(
                    item = item,
                    handlers = handlers,
                    expanded = menuOpen,
                    onExpandedChange = { menuOpen = it },
                    tint = primary.copy(alpha = 0.8f),
                    modifier = Modifier.offset(x = BuddySpacing.sm),
                )
            }
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.TITLE),
                color = primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (step != null) {
                Text(
                    text = step,
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = primary.copy(alpha = 0.85f),
                    maxLines = if (showsDetail) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TaskChips(item, showsDetail, chipTone = if (attention) BuddyChipTone.OUTLINE else BuddyChipTone.ON_BRAND)
            if (showsDetail) {
                TaskDetailLines(item, tint = primary.copy(alpha = 0.8f))
            }
            if (item.pendingApprovalCount > 0 || activity != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(primary.copy(alpha = 0.14f)))
                TaskCardFooter(item, activity, if (attention) AgentBuddyTheme.warning else primary)
            }
        }
    }
}

/** Compact row for tasks that are not running (「接着上次」). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    item: HomeTaskItem,
    handlers: TaskActionHandlers,
    modifier: Modifier = Modifier,
    showsDetail: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val quiet = item.state == BuddyTaskState.COMPLETED || item.state == BuddyTaskState.IDLE
    val step = if (showsDetail) HomeTaskPresentation.latestStep(item.session, item.session.displayTitle) else null

    TaskSwipe(item, handlers, modifier, shape = null) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.background)
                    .taskGestures(item, handlers, onLongPress = { menuOpen = true })
                    .heightIn(min = BuddySize.listRow)
                    .padding(vertical = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BuddyIconTile(
                content = BuddyTileContent.Symbol(item.state.icon),
                fill = if (quiet) AgentBuddyTheme.surface else item.state.fill,
                foreground = if (quiet) AgentBuddyTheme.textSecondary else item.state.foreground,
                modifier =
                    if (quiet) Modifier.border(1.dp, AgentBuddyTheme.border, RoundedCornerShape(16.dp)) else Modifier,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = HomeTaskPresentation.title(item.session.displayTitle),
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = HomeTaskPresentation.rowSubtitle(item),
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (step != null) {
                    Text(
                        text = step,
                        style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                        color = AgentBuddyTheme.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showsDetail) {
                    TaskDetailLines(item, tint = AgentBuddyTheme.textSecondary)
                }
            }
            if (item.isHydrating) {
                Box(Modifier.size(BuddySize.minHitTarget), contentAlignment = Alignment.Center) {
                    LoadingSpinner(AgentBuddyTheme.textSecondary)
                }
            } else {
                TaskActionsMenuButton(
                    item = item,
                    handlers = handlers,
                    expanded = menuOpen,
                    onExpandedChange = { menuOpen = it },
                )
            }
        }
    }
}

/** Right swipe replies, left swipe hides: the same actions as the menu. */
@Composable
private fun TaskSwipe(
    item: HomeTaskItem,
    handlers: TaskActionHandlers,
    modifier: Modifier,
    shape: Shape?,
    content: @Composable () -> Unit,
) {
    SwipeableRow(
        leadingAction = SwipeAction(
            icon = Icons.AutoMirrored.Outlined.Reply,
            label = "回复",
            tint = Color.White,
            fill = AgentBuddyTheme.swipeFill(BuddySwipeTone.LINK),
            onTrigger = { handlers.reply(item) },
        ),
        trailingAction = SwipeAction(
            icon = Icons.Outlined.VisibilityOff,
            label = "隐藏",
            tint = Color.White,
            fill = AgentBuddyTheme.swipeFill(BuddySwipeTone.NEUTRAL),
            onTrigger = { handlers.hide(item) },
        ),
        modifier = if (shape != null) modifier.clip(shape) else modifier,
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.taskGestures(
    item: HomeTaskItem,
    handlers: TaskActionHandlers,
    onLongPress: () -> Unit,
): Modifier =
    combinedClickable(
        onClickLabel = "打开任务",
        onLongClickLabel = "更多操作",
        onClick = { handlers.open(item) },
        onLongClick = onLongPress,
    ).semantics {
        stateDescription = item.state.title
        customActions = listOf(
            CustomAccessibilityAction("回复") { handlers.reply(item); true },
            CustomAccessibilityAction("隐藏") { handlers.hide(item); true },
            CustomAccessibilityAction("更多操作") { onLongPress(); true },
        )
    }

@Composable
private fun TaskTimeOrProgress(item: HomeTaskItem, primary: Color) {
    if (item.isHydrating) {
        LoadingSpinner(primary)
        return
    }
    val time = HomeTaskPresentation.cardTime(item.session) ?: return
    Text(
        text = time,
        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        color = primary.copy(alpha = 0.8f),
        maxLines = 1,
    )
}

@Composable
private fun LoadingSpinner(color: Color) {
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp).semantics { stateDescription = "正在加载" },
        color = color,
        strokeWidth = 2.dp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskChips(item: HomeTaskItem, showsDetail: Boolean, chipTone: BuddyChipTone) {
    val session = item.session
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        HomeTaskPresentation.projectName(session.cwd)?.let { BuddyChip(it, tone = chipTone) }
        BuddyChip(session.agentRuntimeKind.runtimeLabel, tone = chipTone)
        if (showsDetail) {
            session.model.trim().takeIf { it.isNotEmpty() }?.let { BuddyChip(it, tone = chipTone) }
            item.lineage?.let { BuddyChip("分叉 ${it.branchIndex}/${it.branchTotal}", icon = Icons.AutoMirrored.Outlined.CallSplit, tone = chipTone) }
        }
    }
}

/** Detail mode (「首页显示任务详情」): goal and turn statistics. */
@Composable
private fun TaskDetailLines(item: HomeTaskItem, tint: Color) {
    item.session.goal?.let { goal ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Flag, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = "目标：${goal.objective}",
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    InlineStats(session = item.session, isActive = item.session.hasActiveTurn, tint = tint)
}

@Composable
private fun TaskCardFooter(item: HomeTaskItem, activity: String?, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        val (icon, text) =
            if (item.pendingApprovalCount > 0) {
                Icons.Outlined.Shield to "${item.pendingApprovalCount} 个操作等待你确认"
            } else {
                Icons.AutoMirrored.Outlined.CallSplit to activity.orEmpty()
            }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Outlined.NorthEast, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}
