package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppRenameThreadRequest

@Composable
fun ConversationInfoScreen(
    threadKey: ThreadKey? = null,
    serverId: String? = null,
    onBack: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onOpenShell: (() -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var showRenameDialog by remember(threadKey) { mutableStateOf(false) }
    var renameText by remember(threadKey) { mutableStateOf("") }

    val isServerOnly = threadKey == null
    val resolvedServerId = threadKey?.serverId ?: serverId

    val thread = remember(snapshot, threadKey) {
        if (threadKey == null) null
        else snapshot?.threads?.find { it.key == threadKey }
    }
    val server = remember(snapshot, resolvedServerId) {
        snapshot?.servers?.find { it.serverId == resolvedServerId }
    }

    val stats = remember(thread) { thread?.stats }
    val serverUsage = remember(server) { server?.usageStats }
    val rateLimits = remember(server) { server?.rateLimits }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgentBuddyTheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = AgentBuddyTheme.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isServerOnly) "服务器信息" else "会话信息",
                color = AgentBuddyTheme.textPrimary,
                fontSize = 16f.scaled,
                fontWeight = FontWeight.SemiBold,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Section A: Thread Details (thread mode only)
            if (!isServerOnly) {
                item {
                    ThreadDetailsSection(thread = thread, isLocal = server?.isLocal == true)
                }
            }

            // Action buttons row
            if (!isServerOnly) {
                item {
                    ActionButtonsRow(
                        onChangeWallpaper = onChangeWallpaper,
                        onFork = {
                            scope.launch {
                                val t = thread ?: return@launch
                                val tk = threadKey ?: return@launch
                                try {
                                    val newKey = appModel.client.forkThread(
                                        tk.serverId,
                                        appModel.launchState.threadForkRequest(
                                            sourceThreadId = tk.threadId,
                                            cwdOverride = t.info.cwd,
                                            threadKey = tk,
                                        ),
                                    )
                                    appModel.store.setActiveThread(newKey)
                                    appModel.refreshThreadSnapshot(newKey)
                                } catch (_: Exception) {}
                            }
                        },
                        onRename = {
                            renameText = thread?.info?.title.orEmpty()
                            showRenameDialog = true
                        },
                    )
                }
            }

            // Server-only: show just the Wallpaper button
            if (isServerOnly) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ActionCircleButton(
                            icon = Icons.Default.Image,
                            label = "壁纸",
                            onClick = onChangeWallpaper,
                        )
                        if (onOpenShell != null) {
                            ActionCircleButton(
                                icon = Icons.Outlined.Terminal,
                                label = "Shell",
                                onClick = onOpenShell,
                            )
                        }
                    }
                }
            }

            // Context window bar (thread mode only)
            if (!isServerOnly && thread != null) {
                item {
                    ContextWindowBar(thread = thread)
                }
            }

            // Per-conversation stats (thread mode only)
            if (!isServerOnly && stats != null) {
                item {
                    StatsGrid(stats = stats)
                }
            }

            // Section B: Server-Wide Charts
            if (serverUsage != null) {
                item {
                    SectionHeader("服务器用量")
                }

                if (serverUsage.tokensByThread.isNotEmpty()) {
                    item {
                        TokenUsageChart(data = serverUsage.tokensByThread)
                    }
                }

                if (serverUsage.activityByDay.isNotEmpty()) {
                    item {
                        ActivityChart(data = serverUsage.activityByDay)
                    }
                }

                if (serverUsage.modelUsage.isNotEmpty()) {
                    item {
                        ModelBreakdownChart(data = serverUsage.modelUsage)
                    }
                }
            }

            if (rateLimits != null) {
                item {
                    RateLimitGauge(rateLimits = rateLimits)
                }
            }

            // Section C: Server Info
            if (server != null) {
                item {
                    ServerInfoSection(server = server)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showRenameDialog && threadKey != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    showRenameDialog = false
                    scope.launch {
                        try {
                            appModel.client.renameThread(
                                threadKey.serverId,
                                AppRenameThreadRequest(
                                    threadId = threadKey.threadId,
                                    name = trimmed,
                                ),
                            )
                            appModel.refreshThreadSnapshot(threadKey)
                        } catch (_: Exception) {}
                    }
                }) {
                    Text("重命名")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ActionButtonsRow(
    onChangeWallpaper: () -> Unit,
    onFork: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionCircleButton(
            icon = Icons.Default.Image,
            label = "壁纸",
            onClick = onChangeWallpaper,
        )
        ActionCircleButton(
            icon = Icons.Default.ContentCopy,
            label = "分叉",
            onClick = onFork,
        )
        ActionCircleButton(
            icon = Icons.Default.Edit,
            label = "重命名",
            onClick = onRename,
        )
    }
}

@Composable
private fun ActionCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(AgentBuddyTheme.surface, RoundedCornerShape(14.dp)),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
            fontWeight = FontWeight.Medium,
        )
    }
}
