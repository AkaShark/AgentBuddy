package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.settings.SettingsPageHeader
import uniffi.codex_mobile_client.AppConversationStats
import uniffi.codex_mobile_client.AppServerUsageStats
import uniffi.codex_mobile_client.RateLimitSnapshot

/** Task summary for the detail card. Timestamps are epoch seconds. */
internal data class TaskInfoHero(
    val title: String,
    val status: BuddyTaskState,
    val statusTitle: String,
    val partnerAndHost: String?,
    val model: String?,
    val effort: String?,
    val cwdDisplay: String?,
    val cwdFull: String?,
    val threadId: String,
    val createdAt: Long?,
    val updatedAt: Long?,
)

internal data class TaskContextUsage(val usedTokens: Long, val windowTokens: Long)

/** Server section rows, already mapped to display strings. */
internal data class TaskInfoServer(
    val name: String,
    val address: String,
    val mode: String,
    val connection: BuddyConnectionState,
    val connectionTitle: String,
    val accountEmail: String?,
    val planLabel: String?,
    val usesApiKey: Boolean,
    val models: List<String>,
)

/**
 * Everything the task / server info page renders. `hero == null` in task
 * mode means the thread is no longer in the snapshot.
 */
internal data class TaskInfoState(
    val isServerOnly: Boolean,
    val hero: TaskInfoHero?,
    val context: TaskContextUsage?,
    val stats: AppConversationStats?,
    val usage: AppServerUsageStats?,
    val rateLimits: RateLimitSnapshot?,
    val server: TaskInfoServer?,
    val canOpenShell: Boolean,
    val isForking: Boolean = false,
    val actionError: String? = null,
)

internal class TaskInfoActions(
    val onBack: () -> Unit,
    val onChangeWallpaper: () -> Unit,
    val onFork: () -> Unit,
    val onRename: () -> Unit,
    val onOpenShell: () -> Unit,
    val onDismissError: () -> Unit,
)

private val InfoMaxContentWidth = 640.dp

/**
 * 任务信息 / 服务器信息: inline title bar, then (task mode) the detail card,
 * actions, context window and stats, then server usage and server details.
 */
@Composable
internal fun ConversationInfoContent(
    state: TaskInfoState,
    actions: TaskInfoActions,
    modifier: Modifier = Modifier,
) {
    val gutter = BuddySpacing.pageGutter(LocalConfiguration.current.screenWidthDp.dp)
    Column(modifier = modifier.fillMaxSize().background(AgentBuddyTheme.background)) {
        SettingsPageHeader(
            title = if (state.isServerOnly) "服务器信息" else "任务信息",
            onBack = actions.onBack,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = InfoMaxContentWidth).fillMaxWidth(),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = BuddySpacing.xs, bottom = BuddySpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
            ) {
                val hero = state.hero
                if (!state.isServerOnly) {
                    item(key = "hero") {
                        if (hero != null) {
                            TaskInfoHeroCard(hero)
                        } else {
                            BuddyEmptyState(
                                icon = Icons.Outlined.SearchOff,
                                title = "找不到这个任务",
                                message = "它可能已被删除或归档。返回任务列表可以打开其他任务。",
                                actionTitle = "返回",
                                actionKind = BuddyButtonKind.SECONDARY,
                                onAction = actions.onBack,
                            )
                        }
                    }
                }
                state.actionError?.let { message ->
                    item(key = "error") {
                        BuddyBanner(
                            tone = BuddyBannerTone.DANGER,
                            message = message,
                            actionTitle = "知道了",
                            onAction = actions.onDismissError,
                        )
                    }
                }
                if (state.isServerOnly || hero != null) {
                    item(key = "actions") { TaskInfoActionRow(state, actions) }
                }
                state.context?.let { usage -> item(key = "context") { ContextWindowCard(usage) } }
                state.stats?.let { stats -> item(key = "stats") { ConversationStatsSection(stats) } }
                if (state.usage != null || state.rateLimits != null) {
                    item(key = "usage") { ServerUsageCard(usage = state.usage, rateLimits = state.rateLimits) }
                }
                state.server?.let { server -> item(key = "server") { ServerInfoCard(server) } }
            }
        }
    }
}

/** 壁纸 / 分叉 / 重命名 / 终端 as soft buttons; they wrap into a grid or a stack at large text. */
@Composable
private fun TaskInfoActionRow(
    state: TaskInfoState,
    actions: TaskInfoActions,
) {
    BuddyChromeTypeLimit {
        AdaptiveButtonRow {
            BuddyButton(
                text = "壁纸",
                onClick = actions.onChangeWallpaper,
                kind = BuddyButtonKind.SOFT,
                icon = Icons.Outlined.Wallpaper,
                fullWidth = false,
            )
            if (!state.isServerOnly) {
                BuddyButton(
                    text = "分叉",
                    onClick = actions.onFork,
                    kind = BuddyButtonKind.SOFT,
                    icon = Icons.AutoMirrored.Outlined.CallSplit,
                    isLoading = state.isForking,
                    fullWidth = false,
                )
                BuddyButton(
                    text = "重命名",
                    onClick = actions.onRename,
                    kind = BuddyButtonKind.SOFT,
                    icon = Icons.Outlined.Edit,
                    fullWidth = false,
                )
            }
            if (state.canOpenShell) {
                BuddyButton(
                    text = "终端",
                    onClick = actions.onOpenShell,
                    kind = BuddyButtonKind.SOFT,
                    icon = Icons.Outlined.Terminal,
                    fullWidth = false,
                )
            }
        }
    }
}

/**
 * Equal-width cells: every child in one row when the widest fits, otherwise
 * two per row, otherwise one per row. A short last row stretches to the
 * full width.
 */
@Composable
private fun AdaptiveButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = BuddySpacing.sm,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        if (measurables.isEmpty()) return@Layout layout(width, 0) {}
        val gap = spacing.roundToPx()
        val widest = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val columns =
            listOf(measurables.size, 2, 1)
                .filter { it <= measurables.size }
                .firstOrNull { it * widest + (it - 1) * gap <= width } ?: 1
        val rows =
            measurables.chunked(columns).map { row ->
                val cellWidth = ((width - (row.size - 1) * gap) / row.size).coerceAtLeast(0)
                row.map { it.measure(Constraints.fixedWidth(cellWidth)) }
            }
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val height = rowHeights.sum() + (rows.size - 1) * gap
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { index, row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + gap
                }
                y += rowHeights[index] + gap
            }
        }
    }
}
