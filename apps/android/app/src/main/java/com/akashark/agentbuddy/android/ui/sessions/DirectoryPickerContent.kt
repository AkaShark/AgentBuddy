package com.akashark.agentbuddy.android.ui.sessions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.RecentDirectoryEntry

/// Loading / error state, or the recent-directory and folder list.
@Composable
internal fun ColumnScope.DirectoryPickerContent(
    isLoading: Boolean,
    errorMessage: String?,
    recentEntries: List<RecentDirectoryEntry>,
    filteredEntries: List<String>,
    searchQuery: String,
    selectedServerId: String,
    context: Context,
    isLocalServer: (String) -> Boolean,
    completeSelection: (String, String) -> Unit,
    navigateInto: (String) -> Unit,
    onRetry: () -> Unit,
    onShowServerMenu: () -> Unit,
    onClearRecents: () -> Unit,
) {
    var showRecentsMenu by remember { mutableStateOf(false) }

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中…", color = AgentBuddyTheme.textSecondary, fontSize = 13.sp)
            }
        }

        errorMessage != null -> {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("无法加载目录", color = AgentBuddyTheme.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "重试",
                        color = AgentBuddyTheme.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onRetry() },
                    )
                    Text(
                        text = "切换服务器",
                        color = AgentBuddyTheme.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onShowServerMenu() },
                    )
                }
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.background),
            ) {
                val mostRecentEntry = recentEntries.firstOrNull()
                if (mostRecentEntry != null && searchQuery.isBlank()) {
                    item("recent-continue") {
                        PickerRow(
                            icon = Icons.Default.CheckCircle,
                            title = "继续 ${(mostRecentEntry.path.substringAfterLast('/')).ifBlank { mostRecentEntry.path }}",
                            subtitle = com.akashark.agentbuddy.android.state.PathDisplay.display(
                                mostRecentEntry.path,
                                isLocalServer(selectedServerId),
                                context,
                            ),
                            accent = AgentBuddyTheme.accent,
                            onClick = { completeSelection(selectedServerId, mostRecentEntry.path) },
                        )
                    }
                }

                if (recentEntries.isNotEmpty() && searchQuery.isBlank()) {
                    item("recent-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("最近目录", color = AgentBuddyTheme.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { showRecentsMenu = true }) {
                                    Icon(Icons.Default.MoreHoriz, contentDescription = "最近目录选项", tint = AgentBuddyTheme.textMuted)
                                }
                                DropdownMenu(
                                    expanded = showRecentsMenu,
                                    onDismissRequest = { showRecentsMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("清除最近目录") },
                                        onClick = {
                                            showRecentsMenu = false
                                            onClearRecents()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    items(recentEntries, key = { "recent-${it.serverId}-${it.path}" }) { recent ->
                        val pretty = com.akashark.agentbuddy.android.state.PathDisplay.display(
                            recent.path,
                            isLocalServer(selectedServerId),
                            context,
                        )
                        PickerRow(
                            icon = Icons.Default.Folder,
                            title = recent.path.substringAfterLast('/').ifBlank { recent.path },
                            subtitle = "$pretty • ${relativeTime(recent.lastUsedAtEpochMillis)}",
                            accent = AgentBuddyTheme.textSecondary,
                            onClick = { completeSelection(selectedServerId, recent.path) },
                        )
                    }
                    item("recent-footer") {
                        Text(
                            text = "最近目录按已连接服务器分别保存。",
                            color = AgentBuddyTheme.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                if (filteredEntries.isEmpty()) {
                    item("empty") {
                        Text(
                            text = if (searchQuery.isBlank()) "没有子目录" else "没有与 \"$searchQuery\" 匹配的结果",
                            color = AgentBuddyTheme.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    }
                } else {
                    items(filteredEntries, key = { "entry-$it" }) { entry ->
                        PickerRow(
                            icon = Icons.Default.Folder,
                            title = entry,
                            subtitle = null,
                            accent = AgentBuddyTheme.accent,
                            onClick = { navigateInto(entry) },
                        )
                    }
                }
            }
        }
    }
}

private fun relativeTime(epochMillis: Long): String {
    val deltaMinutes = ((System.currentTimeMillis() - epochMillis).coerceAtLeast(0L) / 60000L)
    return when {
        deltaMinutes < 1L -> "刚刚"
        deltaMinutes < 60L -> "${deltaMinutes} 分钟前"
        deltaMinutes < 1440L -> "${deltaMinutes / 60L} 小时前"
        deltaMinutes < 10080L -> "${deltaMinutes / 1440L} 天前"
        else -> "${deltaMinutes / 10080L} 周前"
    }
}

@Composable
private fun PickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AgentBuddyTheme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = AgentBuddyTheme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
