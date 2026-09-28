package com.akashark.agentbuddy.android.ui.homeshell.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyListRow
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyPageHeader
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySectionHeader
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppProject

data class ProjectsHomeUiState(
    val hero: ProjectSummary?,
    val others: List<ProjectSummary>,
    val hasHosts: Boolean,
)

class ProjectsHomeCallbacks(
    val onNewTask: (AppProject) -> Unit,
    val onSelect: (AppProject) -> Unit,
    val onCreateProject: () -> Unit,
    val onManageHosts: () -> Unit,
)

/**
 * 项目 tab: the current (or most recent) project with a direct 「新建任务」,
 * then the other known projects. Tapping a row makes it the current project.
 */
@Composable
fun ProjectsHomeContent(
    state: ProjectsHomeUiState,
    callbacks: ProjectsHomeCallbacks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gutter = BuddySpacing.pageGutter(maxWidth)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = BuddySpacing.lg, bottom = BuddySpacing.xl),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "header") {
                BuddyPageHeader(
                    title = "项目",
                    eyebrow = "每个想法都有位置",
                    subtitle = "把上下文留下，下次接着做。",
                ) {
                    BuddyIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "新建项目",
                        onClick = callbacks.onCreateProject,
                        tone = BuddyIconButtonTone.SURFACE,
                        diameter = 44.dp,
                        iconSize = 22.dp,
                        enabled = state.hasHosts,
                    )
                }
            }
            item(key = "hero") {
                Box(Modifier.padding(top = BuddySpacing.xl)) {
                    val hero = state.hero
                    if (hero != null) {
                        ProjectHeroCard(hero) { callbacks.onNewTask(hero.project) }
                    } else {
                        ProjectsEmptyState(state.hasHosts, callbacks)
                    }
                }
            }
            if (state.others.isNotEmpty()) {
                item(key = "others-header") {
                    BuddySectionHeader(
                        title = if (state.hero == null) "你的项目" else "其他项目",
                        modifier = Modifier.padding(top = BuddySpacing.xl),
                    )
                }
                itemsIndexed(state.others, key = { _, summary -> summary.id }) { index, summary ->
                    Column {
                        if (index > 0) BuddyDivider()
                        ProjectRow(summary, callbacks)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsEmptyState(hasHosts: Boolean, callbacks: ProjectsHomeCallbacks) {
    if (!hasHosts) {
        BuddyEmptyState(
            icon = Icons.Outlined.FolderOff,
            title = "先连接一台主机",
            message = "项目是已连接电脑上的文件夹。先配对电脑，再选择工作文件夹。",
            actionTitle = "查看主机",
            actionIcon = Icons.Outlined.Laptop,
            actionKind = BuddyButtonKind.SECONDARY,
            onAction = callbacks.onManageHosts,
        )
    } else {
        BuddyEmptyState(
            icon = Icons.Outlined.CreateNewFolder,
            title = "暂无项目",
            message = "在主机上选择一个文件夹，在那里开始的任务会归到这里。",
            actionTitle = "新建项目",
            actionIcon = Icons.Outlined.Add,
            onAction = callbacks.onCreateProject,
        )
    }
}

@Composable
private fun ProjectHeroCard(summary: ProjectSummary, onNewTask: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().buddyCard(BuddySurfaceTone.SOFT),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BuddyIconTile(content = BuddyTileContent.BrandMark, fill = AgentBuddyTheme.surface, size = 48.dp)
            Spacer(Modifier.weight(1f))
            if (summary.runningCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = BuddySize.compactPill)
                            .background(AgentBuddyTheme.surface, CircleShape)
                            .padding(horizontal = BuddySpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${summary.runningCount} 个任务进行中",
                        style = buddyTextStyle(BuddyTextStyle.LABEL),
                        color = AgentBuddyTheme.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs)) {
            Text(
                text = summary.name,
                style = buddyTextStyle(BuddyTextStyle.TITLE),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(summary.hostName, summary.displayPath).joinToString(" · "),
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BuddyButton(text = "新建任务", onClick = onNewTask, icon = Icons.Outlined.Add)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(summary: ProjectSummary, callbacks: ProjectsHomeCallbacks) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        BuddyListRow(
            title = summary.name,
            subtitle = ProjectSummaries.rowSubtitle(summary),
            tile = { BuddyIconTile(content = BuddyTileContent.Initial(summary.initial)) },
            accessory = { BuddyChevron() },
            modifier =
                Modifier
                    .combinedClickable(
                        onClickLabel = "设为当前项目",
                        onLongClickLabel = "更多操作",
                        onClick = { callbacks.onSelect(summary.project) },
                        onLongClick = { menuOpen = true },
                    ).semantics { stateDescription = "${summary.taskCount} 个任务" },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = AgentBuddyTheme.surface) {
            DropdownMenuItem(
                text = { Text("在此项目新建任务", style = buddyTextStyle(BuddyTextStyle.BODY)) },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = AgentBuddyTheme.textPrimary) },
                onClick = {
                    menuOpen = false
                    callbacks.onNewTask(summary.project)
                },
            )
            DropdownMenuItem(
                text = { Text("设为当前项目", style = buddyTextStyle(BuddyTextStyle.BODY)) },
                leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AgentBuddyTheme.textPrimary) },
                onClick = {
                    menuOpen = false
                    callbacks.onSelect(summary.project)
                },
            )
        }
    }
}
