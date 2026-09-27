package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

@Composable
internal fun ComposerActiveTaskSummary(summary: ActiveTaskSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AgentBuddyTheme.codeBackground.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\u2610",
            color = AgentBuddyTheme.accent,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "进行中的任务",
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary.progress,
                    color = AgentBuddyTheme.accent,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BerkeleyMono,
                )
            }
            Text(
                text = summary.label,
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CollaborationModeChip(
    mode: uniffi.codex_mobile_client.AppModeKind,
    onClick: () -> Unit,
) {
    val label = when (mode) {
        uniffi.codex_mobile_client.AppModeKind.PLAN -> "计划"
        uniffi.codex_mobile_client.AppModeKind.DEFAULT -> "默认"
    }
    val container = if (mode == uniffi.codex_mobile_client.AppModeKind.PLAN) {
        AgentBuddyTheme.accent
    } else {
        AgentBuddyTheme.surfaceLight
    }
    val contentColor = if (mode == uniffi.codex_mobile_client.AppModeKind.PLAN) {
        Color.Black
    } else {
        AgentBuddyTheme.textPrimary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "打开协作模式选择器",
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun PlanProgressPanel(
    progress: uniffi.codex_mobile_client.AppPlanProgressSnapshot,
) {
    var expanded by remember(progress.turnId) { mutableStateOf(true) }
    val completed = remember(progress.plan) {
        progress.plan.count { it.status == uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED }
    }
    val currentStepLabel = remember(progress.plan) {
        val currentStep = progress.plan.firstOrNull {
            it.status == uniffi.codex_mobile_client.AppPlanStepStatus.IN_PROGRESS
        } ?: progress.plan.firstOrNull {
            it.status == uniffi.codex_mobile_client.AppPlanStepStatus.PENDING
        } ?: progress.plan.lastOrNull {
            it.status == uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED
        }

        currentStep?.step?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (progress.plan.isEmpty()) "暂无计划任务" else "计划完成"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AgentBuddyTheme.codeBackground.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Text(
                text = if (expanded) "计划进度" else "计划",
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$completed/${progress.plan.size}",
                color = AgentBuddyTheme.accent,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BerkeleyMono,
            )
            if (!expanded) {
                Text(
                    text = currentStepLabel,
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起计划进度" else "展开计划进度",
                tint = AgentBuddyTheme.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            progress.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                Text(
                    text = explanation,
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                )
            }
            progress.plan.forEachIndexed { index, step ->
                val icon = when (step.status) {
                    uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED -> "✓"
                    uniffi.codex_mobile_client.AppPlanStepStatus.IN_PROGRESS -> "●"
                    uniffi.codex_mobile_client.AppPlanStepStatus.PENDING -> "○"
                }
                val tint = when (step.status) {
                    uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED -> AgentBuddyTheme.success
                    uniffi.codex_mobile_client.AppPlanStepStatus.IN_PROGRESS -> AgentBuddyTheme.warning
                    uniffi.codex_mobile_client.AppPlanStepStatus.PENDING -> AgentBuddyTheme.textMuted
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = icon,
                        color = tint,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${index + 1}.",
                        color = AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BerkeleyMono,
                    )
                    Text(
                        text = step.step,
                        color = AgentBuddyTheme.textPrimary,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
                    )
                }
            }
        }
    }
}
