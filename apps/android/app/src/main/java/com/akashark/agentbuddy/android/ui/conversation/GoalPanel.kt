package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppThreadGoal
import uniffi.codex_mobile_client.AppThreadGoalStatus

@Composable
internal fun GoalPanel(goal: AppThreadGoal, actions: GoalCardActions) {
    val tint = when (goal.status) {
        AppThreadGoalStatus.ACTIVE -> AgentBuddyTheme.accent
        AppThreadGoalStatus.PAUSED -> AgentBuddyTheme.textMuted
        AppThreadGoalStatus.BLOCKED,
        AppThreadGoalStatus.USAGE_LIMITED,
        AppThreadGoalStatus.BUDGET_LIMITED -> AgentBuddyTheme.warning
        AppThreadGoalStatus.COMPLETE -> AgentBuddyTheme.success
    }
    val statusLabel = when (goal.status) {
        AppThreadGoalStatus.ACTIVE -> "进行中"
        AppThreadGoalStatus.PAUSED -> "已暂停"
        AppThreadGoalStatus.BLOCKED -> "已阻塞"
        AppThreadGoalStatus.USAGE_LIMITED -> "用量受限"
        AppThreadGoalStatus.BUDGET_LIMITED -> "已受限"
        AppThreadGoalStatus.COMPLETE -> "已完成"
    }
    val budgetProgress: Float? = goal.tokenBudget?.takeIf { it > 0 }?.let { budget ->
        (goal.tokensUsed.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
    }
    val budgetLabel = goal.tokenBudget?.takeIf { it > 0 }?.let { budget ->
        "${formatGoalTokens(goal.tokensUsed)} / ${formatGoalTokens(budget)}"
    }
    val progressTint = when {
        budgetProgress == null -> tint
        budgetProgress >= 1f -> AgentBuddyTheme.danger
        budgetProgress >= 0.85f -> AgentBuddyTheme.warning
        else -> tint
    }
    val progressTextTint = when {
        budgetProgress == null -> AgentBuddyTheme.textSecondary
        budgetProgress >= 1f -> AgentBuddyTheme.danger
        budgetProgress >= 0.85f -> AgentBuddyTheme.warning
        else -> AgentBuddyTheme.textSecondary
    }
    val canTogglePause = goal.status != AppThreadGoalStatus.COMPLETE
    val pauseResumeLabel: String? = when (goal.status) {
        AppThreadGoalStatus.ACTIVE -> "暂停目标"
        AppThreadGoalStatus.PAUSED -> "恢复目标"
        AppThreadGoalStatus.BLOCKED -> "恢复目标（忽略阻塞）"
        AppThreadGoalStatus.USAGE_LIMITED -> "恢复目标（忽略用量上限）"
        AppThreadGoalStatus.BUDGET_LIMITED -> "恢复目标（忽略上限）"
        AppThreadGoalStatus.COMPLETE -> null
    }

    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Pulsing status dot — only animates while the goal is active. Mirrors
    // the iOS pill's 0.35 ↔ 1.0 ease-in-out at 1.1s autoreverse.
    val pulse = rememberInfiniteTransition(label = "goalPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "goalPulseAlpha",
    )
    val statusDotAlpha = if (goal.status == AppThreadGoalStatus.ACTIVE) pulseAlpha else 1f
    val animatedProgress by animateFloatAsState(
        targetValue = budgetProgress ?: 0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "goalProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AgentBuddyTheme.codeBackground.copy(alpha = 0.92f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status pill — tappable to pause/resume (or override cap when
            // BUDGET_LIMITED). Disabled once the goal is COMPLETE.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(tint.copy(alpha = 0.14f))
                    .border(0.5.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .clickable(enabled = canTogglePause) { actions.togglePause() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(tint.copy(alpha = statusDotAlpha), CircleShape),
                )
                Text(
                    text = statusLabel.uppercase(),
                    color = tint,
                    fontSize = 10f.scaled,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BerkeleyMono,
                )
            }

            Text(
                text = goal.objective,
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showEditDialog = true },
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "目标操作",
                        tint = AgentBuddyTheme.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (pauseResumeLabel != null) {
                        DropdownMenuItem(
                            text = { Text(pauseResumeLabel, color = AgentBuddyTheme.textPrimary) },
                            onClick = {
                                showMenu = false
                                actions.togglePause()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("编辑目标", color = AgentBuddyTheme.textPrimary) },
                        onClick = {
                            showMenu = false
                            showEditDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("设置 token 预算", color = AgentBuddyTheme.textPrimary) },
                        onClick = {
                            showMenu = false
                            showBudgetDialog = true
                        },
                    )
                    if (goal.status != AppThreadGoalStatus.COMPLETE) {
                        DropdownMenuItem(
                            text = { Text("标记完成", color = AgentBuddyTheme.textPrimary) },
                            onClick = {
                                showMenu = false
                                actions.markComplete()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("清除目标", color = AgentBuddyTheme.danger) },
                        onClick = {
                            showMenu = false
                            showClearConfirm = true
                        },
                    )
                }
            }
        }

        if (budgetProgress != null) {
            val percent = (budgetProgress * 100).toInt()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(tint.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(progressTint.copy(alpha = 0.85f), progressTint),
                                ),
                                RoundedCornerShape(999.dp),
                            ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (budgetLabel != null) {
                        Text(
                            text = budgetLabel,
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = 10f.scaled,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BerkeleyMono,
                        )
                    }
                    Text(
                        text = "$percent%",
                        color = progressTextTint,
                        fontSize = 10f.scaled,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BerkeleyMono,
                    )
                }
            }
        }

        // Usage chips (tokens used + elapsed time). Visible whenever the goal
        // has any usage — including when no budget is set, so the user can
        // still see what the goal has consumed. Mirrors iOS
        // `ConversationComposerContentView.usageMetricsRow`.
        val hasUsage = goal.tokensUsed > 0 || goal.timeUsedSeconds > 0
        if (hasUsage) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (goal.tokensUsed > 0) {
                    Text(
                        text = "T ${formatGoalTokens(goal.tokensUsed)}",
                        color = AgentBuddyTheme.textSecondary,
                        fontSize = 10f.scaled,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BerkeleyMono,
                    )
                }
                if (goal.tokensUsed > 0 && goal.timeUsedSeconds > 0) {
                    Text(
                        text = "·",
                        color = AgentBuddyTheme.textMuted.copy(alpha = 0.6f),
                        fontSize = 10f.scaled,
                        fontFamily = BerkeleyMono,
                    )
                }
                if (goal.timeUsedSeconds > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = AgentBuddyTheme.textSecondary,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatGoalSeconds(goal.timeUsedSeconds),
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = 10f.scaled,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BerkeleyMono,
                        )
                    }
                }
                Spacer(Modifier.weight(1f, fill = false))
            }
        }
    }

    if (showEditDialog) {
        GoalTextInputDialog(
            title = "编辑目标",
            initial = goal.objective,
            placeholder = "你想完成什么？",
            singleLine = false,
            confirmLabel = "保存",
            onConfirm = { value ->
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) actions.setObjective(trimmed)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showBudgetDialog) {
        GoalTextInputDialog(
            title = "Token 预算",
            initial = goal.tokenBudget?.toString().orEmpty(),
            placeholder = "例如 50000",
            singleLine = true,
            keyboardNumeric = true,
            confirmLabel = "保存",
            helper = "达到上限时智能体将暂停。",
            onConfirm = { value ->
                val parsed = value.trim().toLongOrNull()
                if (parsed != null && parsed > 0) actions.setBudget(parsed)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false },
        )
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                Text(
                    text = "清除目标",
                    color = AgentBuddyTheme.danger,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            showClearConfirm = false
                            actions.clear()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    color = AgentBuddyTheme.textPrimary,
                    modifier = Modifier
                        .clickable { showClearConfirm = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            },
            title = {
                Text("清除这个目标？", color = AgentBuddyTheme.textPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "从此线程移除目标。智能体将停止跟踪目标进度。",
                    color = AgentBuddyTheme.textSecondary,
                )
            },
            containerColor = AgentBuddyTheme.surface,
        )
    }
}

private fun formatGoalTokens(value: Long): String =
    when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1fk".format(value / 1_000.0)
        else -> value.toString()
    }

private fun formatGoalSeconds(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val total = seconds.toInt()
    val minutes = total / 60
    val remainSecs = total % 60
    if (total < 3600) {
        return if (remainSecs == 0) "${minutes}m" else "${minutes}m ${remainSecs}s"
    }
    val hours = total / 3600
    val remainMins = (total % 3600) / 60
    return if (remainMins == 0) "${hours}h" else "${hours}h ${remainMins}m"
}
