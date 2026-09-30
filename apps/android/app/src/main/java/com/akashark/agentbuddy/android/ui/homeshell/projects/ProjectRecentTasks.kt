package com.akashark.agentbuddy.android.ui.homeshell.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import uniffi.codex_mobile_client.AppSessionSummary

/**
 * 「最近任务」 inside the current-project card: up to three rows that open the
 * task, then 「查看全部」 when the project has more than the rows shown (tasks
 * hidden from home still count). An offline host or a project with no tasks
 * shows a one-line note instead.
 */
@Composable
internal fun ProjectRecentTasks(
    summary: ProjectSummary,
    recent: List<AppSessionSummary>,
    hostOnline: Boolean,
    onOpenTask: (AppSessionSummary) -> Unit,
    onShowAll: () -> Unit,
    nowMs: Long = System.currentTimeMillis(),
) {
    when {
        !hostOnline -> ProjectCardNote("主机离线，连上后可以继续这个项目。")
        summary.taskCount == 0 -> ProjectCardNote("还没有任务，从「新建任务」开始。")
        else -> Column {
            if (recent.isNotEmpty()) {
                Text(
                    text = "最近任务",
                    style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                    color = AgentBuddyTheme.textSecondary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            recent.forEachIndexed { index, session ->
                if (index > 0) BuddyDivider()
                RecentTaskRow(session, nowMs) { onOpenTask(session) }
            }
            if (summary.taskCount > recent.size) {
                ShowAllTasksLink(count = summary.taskCount, onClick = onShowAll)
            }
        }
    }
}

@Composable
private fun RecentTaskRow(session: AppSessionSummary, nowMs: Long, onClick: () -> Unit) {
    val title = HomeTaskPresentation.title(session.displayTitle)
    val time = HomeTaskPresentation.cardTime(session, nowMs)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.minHitTarget)
                .clickable(onClickLabel = "打开任务", role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (time != null) {
            Text(
                text = time,
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 1,
            )
        }
        BuddyChevron()
    }
}

@Composable
private fun ShowAllTasksLink(count: Int, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .heightIn(min = BuddySize.minHitTarget)
                .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "查看全部 $count 个任务",
            style = buddyTextStyle(BuddyTextStyle.LABEL),
            color = AgentBuddyTheme.link,
        )
        Icon(Icons.Outlined.NorthEast, contentDescription = null, tint = AgentBuddyTheme.link, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ProjectCardNote(text: String) {
    Text(
        text = text,
        style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
        color = AgentBuddyTheme.textSecondary,
        modifier = Modifier.padding(vertical = BuddySpacing.xxs),
    )
}
