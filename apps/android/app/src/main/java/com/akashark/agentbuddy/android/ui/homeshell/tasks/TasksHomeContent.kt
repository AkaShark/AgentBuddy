package com.akashark.agentbuddy.android.ui.homeshell.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyPageHeader
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySectionHeader
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Whether the 任务 tab can show tasks at all; offline is never a task failure. */
enum class TasksHostAvailability { NO_HOSTS, CONNECTING, OFFLINE, ONLINE }

/** Everything the 任务 tab renders, projected from the Rust snapshot. */
data class TasksHomeUiState(
    val hosts: List<HomeHostChoice>,
    val selectedServerId: String?,
    val sections: HomeTaskSections,
    val availability: TasksHostAvailability,
    val showsDetail: Boolean = false,
)

/** Empty-state and navigation callbacks of the 任务 tab. */
class TasksHomeCallbacks(
    val onSearch: () -> Unit,
    val onNewTask: () -> Unit,
    val onPairWithQr: () -> Unit,
    val onOtherConnections: () -> Unit,
    val onManageHosts: () -> Unit,
)

/**
 * 任务 tab: header, hero, then 「需要你处理」, 「正在进行」 (cards) and
 * 「接着上次」 (rows). Stateless: all data and actions come in.
 */
@Composable
fun TasksHomeContent(
    state: TasksHomeUiState,
    headerActions: TasksHeaderActions,
    taskHandlers: TaskActionHandlers,
    callbacks: TasksHomeCallbacks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val sections = state.sections
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gutter = BuddySpacing.pageGutter(maxWidth)
        val largeText = LocalDensity.current.fontScale * LocalTextScale.current > 1.3f
        val compactHero = !sections.isEmpty && (maxHeight < 600.dp || largeText)
        val reduceMotion = buddyReduceMotion
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = BuddySpacing.xs, bottom = BuddySpacing.xl),
        ) {
            item(key = "header") {
                TasksHomeHeader(state.hosts, state.selectedServerId, headerActions)
            }
            item(key = "hero") {
                TasksHero(
                    sections = sections,
                    compact = compactHero,
                    showsSearch = sections.isEmpty && state.availability == TasksHostAvailability.ONLINE,
                    onSearch = callbacks.onSearch,
                )
            }
            if (sections.isEmpty) {
                item(key = "empty") {
                    TasksEmptyContent(
                        availability = state.availability,
                        callbacks = callbacks,
                        modifier = Modifier.padding(top = BuddySpacing.xl),
                    )
                }
            } else {
                taskSections(sections, state.showsDetail, reduceMotion, taskHandlers, callbacks, headerActions.onShowAllTasks)
            }
        }
    }
}

private fun LazyListScope.taskSections(
    sections: HomeTaskSections,
    showsDetail: Boolean,
    reduceMotion: Boolean,
    handlers: TaskActionHandlers,
    callbacks: TasksHomeCallbacks,
    onShowAllTasks: () -> Unit,
) {
    val searchOn =
        when {
            sections.attention.isNotEmpty() -> "attention"
            sections.active.isNotEmpty() -> "active"
            else -> "recent"
        }
    cardSection("attention", "需要你处理", sections.attention, searchOn, showsDetail, reduceMotion, handlers, callbacks)
    cardSection("active", "正在进行", sections.active, searchOn, showsDetail, reduceMotion, handlers, callbacks)
    if (sections.recent.isNotEmpty()) {
        item(key = "recent-header") {
            BuddySectionHeader(
                title = "接着上次",
                count = sections.recent.size,
                modifier = Modifier.padding(top = BuddySpacing.lg),
            ) {
                BuddyChromeTypeLimit {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchOn == "recent") SearchButton(callbacks.onSearch)
                        AllTasksLink(onShowAllTasks)
                    }
                }
            }
        }
        itemsIndexed(sections.recent, key = { _, item -> "recent-${item.id}" }) { index, item ->
            Column(if (reduceMotion) Modifier else Modifier.animateItem()) {
                if (index > 0) BuddyDivider()
                TaskRow(item = item, handlers = handlers, showsDetail = showsDetail)
            }
        }
    }
}

private fun LazyListScope.cardSection(
    id: String,
    title: String,
    items: List<HomeTaskItem>,
    searchOn: String,
    showsDetail: Boolean,
    reduceMotion: Boolean,
    handlers: TaskActionHandlers,
    callbacks: TasksHomeCallbacks,
) {
    if (items.isEmpty()) return
    item(key = "$id-header") {
        BuddySectionHeader(title = title, count = items.size, modifier = Modifier.padding(top = BuddySpacing.md)) {
            if (searchOn == id) BuddyChromeTypeLimit { SearchButton(callbacks.onSearch) }
        }
    }
    itemsIndexed(items, key = { _, item -> "$id-${item.id}" }) { _, item ->
        ActiveTaskCard(
            item = item,
            handlers = handlers,
            showsDetail = showsDetail,
            modifier = (if (reduceMotion) Modifier else Modifier.animateItem()).padding(vertical = BuddySpacing.xs),
        )
    }
}

@Composable
private fun SearchButton(onSearch: () -> Unit) {
    BuddyIconButton(icon = Icons.Outlined.Search, contentDescription = "搜索任务", onClick = onSearch, iconSize = 22.dp)
}

@Composable
private fun AllTasksLink(onShowAllTasks: () -> Unit) {
    Row(
        modifier =
            Modifier
                .heightIn(min = BuddySize.minHitTarget)
                .clickable(role = Role.Button, onClick = onShowAllTasks)
                .padding(horizontal = BuddySpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("全部任务", style = buddyTextStyle(BuddyTextStyle.LABEL), color = AgentBuddyTheme.textSecondary)
        Icon(Icons.Outlined.NorthEast, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(16.dp))
    }
}

/**
 * Full brand copy on the empty home; once tasks exist it carries the count
 * summary, and on short screens / large text it shrinks to that summary so
 * the most urgent task stays visible without scrolling.
 */
@Composable
private fun TasksHero(
    sections: HomeTaskSections,
    compact: Boolean,
    showsSearch: Boolean,
    onSearch: () -> Unit,
) {
    val summary = HomeTaskPresentation.summary(running = sections.active.size, waiting = sections.attention.size)
    if (compact) {
        Text(
            text = summary,
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = BuddySpacing.xs),
        )
        return
    }
    BuddyPageHeader(
        title = "让想法，向前一步。",
        eyebrow = "你的想法，正在向前",
        subtitle = if (sections.isEmpty) "口袋里的编程搭档。" else summary,
        modifier = Modifier.padding(top = if (sections.isEmpty) BuddySpacing.xl else BuddySpacing.md),
    ) {
        if (showsSearch) BuddyChromeTypeLimit { SearchButton(onSearch) }
    }
}
