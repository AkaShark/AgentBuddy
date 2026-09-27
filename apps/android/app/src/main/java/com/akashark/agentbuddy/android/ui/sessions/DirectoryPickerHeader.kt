package com.akashark.agentbuddy.android.ui.sessions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme

/// Title, server switcher, hidden-folder toggle, folder search and the
/// up / go-to-path / breadcrumb row.
@Composable
internal fun DirectoryPickerHeader(
    servers: List<DirectoryPickerServerOption>,
    selectedServer: DirectoryPickerServerOption?,
    selectedServerId: String,
    showServerMenu: Boolean,
    onShowServerMenuChange: (Boolean) -> Unit,
    onSelectServer: (String) -> Unit,
    showHiddenDirectories: Boolean,
    onToggleHiddenDirectories: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentPath: String,
    context: Context,
    isLocalServer: (String) -> Boolean,
    pathSegments: (String) -> List<Pair<String, String>>,
    onNavigateUp: () -> Unit,
    onOpenGoToPath: () -> Unit,
    onOpenPath: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "选择目录",
            color = AgentBuddyTheme.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedServer?.let { "已连接服务器：${it.name} • ${it.sourceLabel}" } ?: "未选择服务器",
                color = if (selectedServer == null) AgentBuddyTheme.textMuted else AgentBuddyTheme.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Box {
                Text(
                    text = "切换服务器",
                    color = AgentBuddyTheme.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = servers.isNotEmpty()) { onShowServerMenuChange(true) },
                )
                DropdownMenu(
                    expanded = showServerMenu,
                    onDismissRequest = { onShowServerMenuChange(false) },
                ) {
                    servers.forEach { server ->
                        DropdownMenuItem(
                            text = { Text("${server.name} • ${server.sourceLabel}") },
                            onClick = {
                                onShowServerMenuChange(false)
                                onSelectServer(server.id)
                            },
                        )
                    }
                }
            }

            IconButton(onClick = onToggleHiddenDirectories) {
                Icon(
                    imageVector = if (showHiddenDirectories) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (showHiddenDirectories) "隐藏隐藏文件夹" else "显示隐藏文件夹",
                    tint = if (showHiddenDirectories) AgentBuddyTheme.accent else AgentBuddyTheme.textSecondary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
                .border(1.dp, AgentBuddyTheme.border.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = AgentBuddyTheme.textMuted)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text("搜索文件夹", color = AgentBuddyTheme.textMuted, fontSize = 13.sp)
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(AgentBuddyTheme.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "清除搜索", tint = AgentBuddyTheme.textMuted)
                }
            }
        }

        val homeAnchorLocal = remember(selectedServerId, context) {
            if (isLocalServer(selectedServerId)) com.akashark.agentbuddy.android.state.HomeAnchor.path(context) else null
        }
        val canGoUp = currentPath != "/" && currentPath.isNotEmpty() && currentPath != homeAnchorLocal
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    text = "上一级",
                    color = if (canGoUp) AgentBuddyTheme.accent else AgentBuddyTheme.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
                        .clickable(enabled = canGoUp) { onNavigateUp() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            item {
                Text(
                    text = "跳转到路径",
                    color = AgentBuddyTheme.accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
                        .clickable { onOpenGoToPath() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            items(pathSegments(currentPath)) { segment ->
                val isCurrent = segment.second == currentPath
                Text(
                    text = segment.first,
                    color = if (isCurrent) Color.Black else AgentBuddyTheme.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(
                            if (isCurrent) AgentBuddyTheme.accent else AgentBuddyTheme.surface,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onOpenPath(segment.second) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/// Current path + Cancel / Select folder buttons.
@Composable
internal fun DirectoryPickerFooter(
    currentPath: String,
    onDismiss: () -> Unit,
    onSelectCurrentPath: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = currentPath.ifBlank { "选择一个文件夹以开始新会话。" },
            color = if (currentPath.isBlank()) AgentBuddyTheme.textSecondary else AgentBuddyTheme.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgentBuddyTheme.surface,
                    contentColor = AgentBuddyTheme.textPrimary,
                ),
            ) {
                Text("取消")
            }
            Button(
                onClick = onSelectCurrentPath,
                enabled = currentPath.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentPath.isNotBlank()) AgentBuddyTheme.accent else AgentBuddyTheme.surface,
                    contentColor = if (currentPath.isNotBlank()) Color.Black else AgentBuddyTheme.textMuted,
                ),
            ) {
                Text("选择文件夹")
            }
        }
    }
}
