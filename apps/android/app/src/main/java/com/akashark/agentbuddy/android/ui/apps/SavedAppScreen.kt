package com.akashark.agentbuddy.android.ui.apps

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.SavedAppsStore
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.SavedApp
import uniffi.codex_mobile_client.SavedAppWithPayload

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SavedAppScreen(
    appId: String,
    onBack: () -> Unit,
    onOpenConversation: ((uniffi.codex_mobile_client.ThreadKey) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appModel = LocalAppModel.current

    var payload by remember(appId) { mutableStateOf<SavedAppWithPayload?>(null) }
    var loadState by remember(appId) { mutableStateOf<LoadState>(LoadState.Loading) }
    var showMenu by remember { mutableStateOf(false) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteConfirmVisible by remember { mutableStateOf(false) }
    var showUpdateOverlay by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(appId, reloadTick) {
        loadState = LoadState.Loading
        try {
            val fetched = SavedAppsStore.getWithPayload(context, appId)
            if (fetched == null) {
                loadState = LoadState.Broken
            } else {
                payload = fetched
                loadState = LoadState.Ready
            }
        } catch (e: Exception) {
            loadState = LoadState.Failed(e.message ?: "无法加载应用。")
        }
    }

    DisposableEffect(appId) {
        onDispose {
            scope.launch { SavedAppsStore.flushPendingSave(context, appId) }
        }
    }

    val currentPayload = payload
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AgentBuddyTheme.background)
            .systemBarsPadding(),
    ) {
        val originThreadKey = currentPayload?.app?.let { app ->
            resolveOriginThreadKey(appModel, app)
        }
        TopBar(
            title = currentPayload?.app?.title.orEmpty(),
            onBack = onBack,
            onTitleClick = { renameDialogVisible = true },
            onUpdate = { showUpdateOverlay = true },
            onOpenMenu = { showMenu = true },
            onViewConversation = if (originThreadKey != null && onOpenConversation != null) {
                { onOpenConversation(originThreadKey) }
            } else null,
            isUpdating = isUpdating,
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    showMenu = false
                    renameDialogVisible = true
                },
            )
            DropdownMenuItem(
                text = { Text("删除", color = AgentBuddyTheme.danger) },
                onClick = {
                    showMenu = false
                    deleteConfirmVisible = true
                },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = loadState) {
                LoadState.Loading -> LoadingPlaceholder()
                LoadState.Broken -> BrokenPlaceholder(
                    onDelete = {
                        scope.launch {
                            try {
                                SavedAppsStore.delete(context, appId)
                            } catch (_: Exception) {}
                            onBack()
                        }
                    },
                )
                is LoadState.Failed -> FailurePlaceholder(state.message) {
                    reloadTick += 1
                }
                LoadState.Ready -> {
                    if (currentPayload != null) {
                        AppModeWebView(
                            payload = currentPayload,
                            dimmed = isUpdating,
                            appModel = appModel,
                        )
                    }
                }
            }

            if (isUpdating) {
                ShimmerOverlay()
            }

            if (showUpdateOverlay && currentPayload != null) {
                SavedAppUpdateOverlay(
                    currentTitle = currentPayload.app.title,
                    onDismiss = {
                        if (!isUpdating) showUpdateOverlay = false
                    },
                    onSubmit = { prompt ->
                        isUpdating = true
                        scope.launch {
                            val serverId = resolveServerId(appModel, currentPayload.app)
                            try {
                                if (serverId == null) {
                                    throw IllegalStateException(
                                        "没有已连接的服务器。请先连接一个再试。",
                                    )
                                }
                                SavedAppsStore.requestUpdate(
                                    context = context,
                                    serverId = serverId,
                                    appId = appId,
                                    prompt = prompt,
                                )
                                showUpdateOverlay = false
                                reloadTick += 1
                                android.widget.Toast.makeText(
                                    context,
                                    "应用已更新",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "更新失败：${e.message ?: "未知错误"}",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                isUpdating = false
                            }
                        }
                    },
                    isSubmitting = isUpdating,
                )
            }
        }
    }

    if (renameDialogVisible && currentPayload != null) {
        RenameAppDialog(
            currentTitle = currentPayload.app.title,
            onDismiss = { renameDialogVisible = false },
            onRename = { newTitle ->
                renameDialogVisible = false
                scope.launch {
                    try {
                        SavedAppsStore.rename(context, appId, newTitle)
                        reloadTick += 1
                    } catch (_: Exception) {}
                }
            },
        )
    }

    if (deleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = { deleteConfirmVisible = false },
            title = { Text("删除这个 app？") },
            text = {
                Text("它的 HTML 和已保存状态将从本设备移除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmVisible = false
                    scope.launch {
                        try {
                            SavedAppsStore.delete(context, appId)
                        } catch (_: Exception) {}
                        onBack()
                    }
                }) { Text("删除", color = AgentBuddyTheme.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmVisible = false }) { Text("取消") }
            },
        )
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data object Broken : LoadState
    data object Ready : LoadState
    data class Failed(val message: String) : LoadState
}

internal fun resolveServerId(
    appModel: com.akashark.agentbuddy.android.state.AppModel,
    app: SavedApp,
): String? {
    val snapshot = appModel.snapshot.value ?: return null
    val servers = snapshot.servers
    val origin = app.originThreadId?.let { threadId ->
        snapshot.sessionSummaries.firstOrNull { it.key.threadId == threadId }?.key?.serverId
    }
    if (origin != null && servers.any { it.serverId == origin }) return origin
    return snapshot.activeThread?.serverId
        ?: servers.firstOrNull { it.isLocal }?.serverId
        ?: servers.firstOrNull()?.serverId
}

/**
 * Resolve the origin thread key from a saved app, if the thread still exists
 * on a known server. Returns null when the thread has been deleted or the
 * app never recorded an origin.
 */
private fun resolveOriginThreadKey(
    appModel: com.akashark.agentbuddy.android.state.AppModel,
    app: SavedApp,
): uniffi.codex_mobile_client.ThreadKey? {
    val threadId = app.originThreadId ?: return null
    val snapshot = appModel.snapshot.value ?: return null
    return snapshot.sessionSummaries.firstOrNull { it.key.threadId == threadId }?.key
}
