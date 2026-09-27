package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.home.HomeDashboardSupport
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppArchiveThreadRequest
import uniffi.codex_mobile_client.AppRenameThreadRequest

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionNodeRow(
    node: SessionTreeNode,
    hasChildren: Boolean,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onClick: () -> Unit,
    onFork: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val scope = rememberCoroutineScope()
    val voiceController = remember { com.akashark.agentbuddy.android.state.VoiceRuntimeController.shared }
    val summary = node.summary
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (node.depth * 16).dp)
                .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .let { modifier ->
                        if (hasChildren) {
                            modifier.clickable(onClick = onToggleCollapse)
                        } else {
                            modifier
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (hasChildren) {
                    Icon(
                        if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        contentDescription = if (isCollapsed) "展开子会话" else "折叠子会话",
                        tint = AgentBuddyTheme.textMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))

            // Active turn indicator
            if (summary.hasActiveTurn) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AgentBuddyTheme.accent),
                )
                Spacer(Modifier.width(6.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                com.akashark.agentbuddy.android.ui.common.FormattedText(
                    text = summary.displayTitle,
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.model?.let { model ->
                        Text(
                            text = model.substringAfterLast('/'),
                            color = AgentBuddyTheme.textMuted,
                            fontSize = 10.sp,
                        )
                    }
                    summary.agentDisplayLabel?.let { label ->
                        Text(
                            text = label,
                            color = AgentBuddyTheme.accent,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Text(
                text = HomeDashboardSupport.relativeTime(summary.updatedAt),
                color = AgentBuddyTheme.textMuted,
                fontSize = 10.sp,
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("分叉") },
                onClick = {
                    showMenu = false
                    onFork()
                },
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = { showMenu = false; showRenameDialog = true },
            )
            DropdownMenuItem(
                text = { Text("归档") },
                onClick = { showMenu = false; showArchiveDialog = true },
            )
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(summary.title ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    scope.launch {
                        try {
                            appModel.client.renameThread(
                                summary.key.serverId,
                                AppRenameThreadRequest(
                                    threadId = summary.key.threadId,
                                    name = newName,
                                ),
                            )
                            appModel.refreshThreadSnapshot(summary.key)
                        } catch (_: Exception) {}
                    }
                }) { Text("重命名") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            },
        )
    }

    // Archive confirmation dialog
    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text("归档会话") },
            text = { Text("确定要归档这个会话吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    scope.launch {
                        try {
                            voiceController.stopVoiceSessionIfActive(appModel, summary.key)
                            voiceController.clearPinnedLocalVoiceThreadIfMatches(appModel, summary.key)
                            if (appModel.snapshot.value?.activeThread == summary.key) {
                                appModel.store.setActiveThread(null)
                            }
                            appModel.client.archiveThread(
                                summary.key.serverId,
                                AppArchiveThreadRequest(threadId = summary.key.threadId),
                            )
                            appModel.refreshSnapshot()
                        } catch (_: Exception) {}
                    }
                }) { Text("归档", color = AgentBuddyTheme.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text("取消") }
            },
        )
    }

    Spacer(Modifier.height(4.dp))
}
