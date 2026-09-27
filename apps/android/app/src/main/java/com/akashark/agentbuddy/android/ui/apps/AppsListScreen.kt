package com.akashark.agentbuddy.android.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.SavedAppsStore
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.BuddySwipeTone
import com.akashark.agentbuddy.android.ui.common.SwipeAction
import com.akashark.agentbuddy.android.ui.common.SwipeableRow
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.settings.SettingsFooter
import com.akashark.agentbuddy.android.ui.settings.SettingsPage
import com.akashark.agentbuddy.android.ui.settings.SettingsRow
import com.akashark.agentbuddy.android.ui.settings.SettingsRowDivider
import com.akashark.agentbuddy.android.ui.settings.settingsSection
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.SavedApp
import java.util.concurrent.TimeUnit

@Composable
fun AppsListScreen(
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by SavedAppsStore.apps.collectAsState()
    var renameTarget by remember { mutableStateOf<SavedApp?>(null) }

    LaunchedEffect(Unit) {
        SavedAppsStore.reload(context)
    }

    AppsListContent(
        apps = apps,
        onBack = onBack,
        onOpenApp = onOpenApp,
        onRename = { renameTarget = it },
        onDelete = { app ->
            scope.launch {
                try {
                    SavedAppsStore.delete(context, app.id)
                } catch (_: Exception) {}
            }
        },
        modifier = Modifier.systemBarsPadding(),
    )

    renameTarget?.let { app ->
        RenameAppDialog(
            currentTitle = app.title,
            onDismiss = { renameTarget = null },
            onRename = { title ->
                renameTarget = null
                scope.launch {
                    try {
                        SavedAppsStore.rename(context, app.id, title)
                    } catch (_: Exception) {}
                }
            },
        )
    }
}

/**
 * Saved apps as one settings-style group. Swipe right renames, swipe left
 * deletes (both also TalkBack actions); the footer says so.
 */
@Composable
internal fun AppsListContent(
    apps: List<SavedApp>,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    onRename: (SavedApp) -> Unit,
    onDelete: (SavedApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(title = "应用", onBack = onBack, modifier = modifier) {
        if (apps.isEmpty()) {
            item(key = "empty") {
                Spacer(Modifier.height(BuddySpacing.md))
                BuddyEmptyState(
                    icon = Icons.Outlined.GridView,
                    title = "暂无应用",
                    message = "在任务里让搭子做一个交互式小组件（带 app_id），它会自动保存到这里，以后可以直接打开。",
                    actionTitle = "返回去开始任务",
                    actionKind = BuddyButtonKind.SECONDARY,
                    onAction = onBack,
                )
            }
        } else {
            settingsSection(
                title = "已保存 ${apps.size} 个",
                key = "apps",
                footer = { SettingsFooter("向右滑动重命名，向左滑动删除。") },
            ) {
                apps.forEachIndexed { index, app ->
                    SwipeableRow(
                        leadingAction =
                            SwipeAction(
                                icon = Icons.Outlined.Edit,
                                label = "重命名",
                                tint = AgentBuddyTheme.swipeFill(BuddySwipeTone.LINK),
                                onTrigger = { onRename(app) },
                            ),
                        trailingAction =
                            SwipeAction(
                                icon = Icons.Outlined.Delete,
                                label = "删除",
                                tint = AgentBuddyTheme.swipeFill(BuddySwipeTone.DANGER),
                                onTrigger = { onDelete(app) },
                            ),
                    ) {
                        SettingsRow(
                            title = app.title.ifBlank { "未命名应用" },
                            subtitle = "${relativeTime(app.updatedAtMs)}更新",
                            onClick = { onOpenApp(app.id) },
                            leading = { BuddyIconTile(BuddyTileContent.Initial(monogramInitials(app.title))) },
                            trailing = { BuddyChevron() },
                            modifier = Modifier.background(AgentBuddyTheme.surface),
                        )
                    }
                    if (index < apps.lastIndex) {
                        SettingsRowDivider(startIndent = BuddySpacing.md + BuddySize.rowTile + BuddySpacing.sm)
                    }
                }
            }
        }
    }
}

/**
 * Monogram: first letter of the first two whitespace-separated words of the
 * title, uppercased. Mirrors iOS `AppsListView.monogramInitials` so avatars
 * stay recognizable across platforms. Falls back to `?` on empty titles.
 */
private fun monogramInitials(title: String): String {
    val words = title.trim().split("\\s+".toRegex()).take(2)
    val letters = words.mapNotNull { it.firstOrNull() }.joinToString("")
    return if (letters.isEmpty()) "?" else letters.uppercase()
}

private fun relativeTime(millis: Long): String {
    val delta = System.currentTimeMillis() - millis
    if (delta < 0) return "刚刚"
    val seconds = TimeUnit.MILLISECONDS.toSeconds(delta)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        seconds < 60 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        days < 7 -> "${days} 天前"
        else -> "${days / 7} 周前"
    }
}
