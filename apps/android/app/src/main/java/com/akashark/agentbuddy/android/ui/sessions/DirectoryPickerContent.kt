package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyRadius
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.discovery.MintButtonPair

/// Loading / error state, or the recent-directory and folder list.
@Composable
internal fun DirectoryPickerList(
    state: DirectoryPickerViewState,
    actions: DirectoryPickerCallbacks,
    gutter: Dp,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AgentBuddyTheme.textSecondary,
                )
                Text("加载中…", style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal), color = AgentBuddyTheme.textSecondary)
            }
        }

        state.errorMessage != null -> LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(gutter)) {
            item("error") { DirectoryPickerError(message = state.errorMessage, actions = actions) }
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = BuddySpacing.sm, bottom = BuddySpacing.lg),
        ) {
            val showRecents = state.recents.isNotEmpty() && state.searchQuery.isBlank()
            state.continueEntry?.takeIf { state.searchQuery.isBlank() }?.let { recent ->
                item("recent-continue") {
                    DirectoryRow(
                        icon = Icons.Outlined.PlayArrow,
                        tileFill = AgentBuddyTheme.brand,
                        tileForeground = AgentBuddyTheme.onBrand,
                        title = "在 ${recent.title} 中继续",
                        subtitle = recent.pathDisplay,
                        shape = groupShape(0, 1),
                        onClick = { actions.onSelectRecent(recent.path) },
                    )
                }
            }
            if (showRecents) {
                item("recent-header") { RecentsHeader(onClearRecents = actions.onClearRecents) }
                itemsIndexed(state.recents, key = { _, it -> "recent-${it.path}" }) { index, recent ->
                    Column {
                        if (index > 0) DirectoryDivider()
                        DirectoryRow(
                            icon = Icons.Outlined.History,
                            title = recent.title,
                            subtitle = "${recent.pathDisplay} • ${recent.timeLabel}",
                            shape = groupShape(index, state.recents.size),
                            onClick = { actions.onSelectRecent(recent.path) },
                        )
                    }
                }
                item("recent-footer") {
                    Text(
                        text = "最近目录按已连接服务器分别保存。",
                        style = buddyTextStyle(BuddyTextStyle.CAPTION),
                        color = AgentBuddyTheme.textSecondary,
                        modifier = Modifier.padding(vertical = BuddySpacing.xs),
                    )
                }
            }
            folderItems(state, actions)
        }
    }
}

private fun LazyListScope.folderItems(state: DirectoryPickerViewState, actions: DirectoryPickerCallbacks) {
    item("folders-header") { SectionCaption("文件夹", Modifier.padding(top = BuddySpacing.sm)) }
    if (state.folders.isEmpty()) {
        item("empty") {
            val query = state.searchQuery.trim()
            if (query.isEmpty()) {
                BuddyEmptyState(
                    icon = Icons.Outlined.FolderOpen,
                    title = "无子目录",
                    message = if (state.showHiddenDirectories) {
                        "这个文件夹里没有子文件夹。可以直接选择此文件夹，或返回上一级。"
                    } else {
                        "这个文件夹里没有可见的子文件夹。可以直接选择此文件夹，或显示隐藏文件夹。"
                    },
                    actionTitle = if (state.canGoUp) "上一级" else null,
                    actionIcon = Icons.Outlined.ArrowUpward,
                    actionKind = BuddyButtonKind.SECONDARY,
                    onAction = actions.onNavigateUp,
                )
            } else {
                BuddyEmptyState(
                    icon = Icons.Outlined.Cancel,
                    title = "没有与“$query”匹配的结果",
                    message = "换个名称试试，或清除搜索查看这里的全部文件夹。",
                    actionTitle = "清除搜索",
                    actionKind = BuddyButtonKind.SECONDARY,
                    onAction = { actions.onSearchQueryChange("") },
                )
            }
        }
    } else {
        itemsIndexed(state.folders, key = { _, it -> "entry-$it" }) { index, entry ->
            Column {
                if (index > 0) DirectoryDivider()
                DirectoryRow(
                    icon = Icons.Outlined.Folder,
                    title = entry,
                    subtitle = null,
                    shape = groupShape(index, state.folders.size),
                    showsChevron = true,
                    onClick = { actions.onOpenFolder(entry) },
                )
            }
        }
    }
}

@Composable
private fun DirectoryPickerError(message: String, actions: DirectoryPickerCallbacks) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.SOFT),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        BuddyIconTile(
            content = BuddyTileContent.Symbol(Icons.Outlined.WarningAmber),
            fill = AgentBuddyTheme.warningSurface,
            foreground = AgentBuddyTheme.warning,
            size = 48.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            Text(
                text = "无法加载此文件夹。",
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "$message\n重试一次，或换一台已连接的主机。",
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        MintButtonPair(
            first = { buttonModifier ->
                BuddyButton(text = "重试", onClick = actions.onRetry, icon = Icons.Outlined.Refresh, modifier = buttonModifier)
            },
            second = { buttonModifier ->
                BuddyButton(
                    text = "更改服务器",
                    onClick = { actions.onShowServerMenuChange(true) },
                    kind = BuddyButtonKind.SECONDARY,
                    modifier = buttonModifier,
                )
            },
        )
    }
}

@Composable
private fun RecentsHeader(onClearRecents: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionCaption("最近目录", Modifier.weight(1f))
        Box {
            BuddyIconButton(
                icon = Icons.Outlined.MoreHoriz,
                contentDescription = "最近目录选项",
                onClick = { showMenu = true },
                tint = AgentBuddyTheme.textSecondary,
            )
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = AgentBuddyTheme.surface,
            ) {
                DropdownMenuItem(
                    text = { Text("清除最近目录", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.danger) },
                    onClick = {
                        showMenu = false
                        onClearRecents()
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionCaption(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        color = AgentBuddyTheme.textSecondary,
        modifier = modifier
            .padding(vertical = BuddySpacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun DirectoryDivider() {
    Box(Modifier.background(AgentBuddyTheme.surface)) {
        BuddyDivider(startIndent = BuddySpacing.md + 40.dp + BuddySpacing.md)
    }
}

/** Corner shape for row [index] of a [count]-row group on one surface card. */
private fun groupShape(index: Int, count: Int): Shape {
    val r = BuddyRadius.detailCard
    val top = if (index == 0) r else 0.dp
    val bottom = if (index == count - 1) r else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/// Folder tile, name, optional path line (code type), optional chevron.
@Composable
private fun DirectoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    shape: Shape,
    onClick: () -> Unit,
    tileFill: Color = AgentBuddyTheme.surfaceSoft,
    tileForeground: Color = AgentBuddyTheme.textPrimary,
    showsChevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AgentBuddyTheme.surface, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuddyIconTile(BuddyTileContent.Symbol(icon), fill = tileFill, foreground = tileForeground, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = buddyTextStyle(if (subtitle == null) BuddyTextStyle.BODY else BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = buddyTextStyle(BuddyTextStyle.CODE),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showsChevron) BuddyChevron(Modifier.size(BuddySize.icon))
    }
}

internal fun directoryRelativeTime(epochMillis: Long): String {
    val deltaMinutes = ((System.currentTimeMillis() - epochMillis).coerceAtLeast(0L) / 60000L)
    return when {
        deltaMinutes < 1L -> "刚刚"
        deltaMinutes < 60L -> "${deltaMinutes} 分钟前"
        deltaMinutes < 1440L -> "${deltaMinutes / 60L} 小时前"
        deltaMinutes < 10080L -> "${deltaMinutes / 1440L} 天前"
        else -> "${deltaMinutes / 10080L} 周前"
    }
}
