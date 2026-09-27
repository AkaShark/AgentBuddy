package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.RecentDirectoryEntry
import com.akashark.agentbuddy.android.ui.RecentDirectoryStore
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    serverId: String?,
    title: String,
    sessionsUiState: SessionsUiState,
    onOpenConversation: (ThreadKey) -> Unit,
    onNewSession: (() -> Unit)? = null,
    onBack: () -> Unit,
    onInfo: (() -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val connectedServerIds = remember(snapshot) {
        snapshot?.servers
            ?.filter { it.isConnected }
            ?.map { it.serverId }
            ?.sorted()
            .orEmpty()
    }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isForkingActiveThread by remember { mutableStateOf(false) }
    var hasLoadedInitialSessions by remember { mutableStateOf(false) }
    var pendingActiveSessionScroll by remember { mutableStateOf(false) }
    val derived = remember(
        snapshot,
        searchQuery,
        serverId,
        sessionsUiState.sortMode,
        sessionsUiState.showOnlyForks,
    ) {
        val summaries = snapshot?.sessionSummaries ?: emptyList()
        SessionsDerivation.derive(
            summaries = summaries,
            serverFilter = serverId,
            searchQuery = searchQuery,
            sortMode = sessionsUiState.sortMode,
            forkOnly = sessionsUiState.showOnlyForks,
        )
    }

    val listState = rememberLazyListState()

    fun scheduleActiveSessionScrollIfNeeded() {
        if (snapshot?.activeThread != null) {
            pendingActiveSessionScroll = true
        }
    }

    suspend fun scrollToActiveSessionIfNeeded() {
        val activeKey = snapshot?.activeThread ?: return
        if (!pendingActiveSessionScroll) return

        val activeThread = derived.filteredThreads.firstOrNull { it.key == activeKey } ?: run {
            pendingActiveSessionScroll = false
            return
        }

        val activeGroupKey = derived.workspaceGroupKeyByThreadKey[activeKey]
            ?: SessionsDerivation.workspaceGroupKey(activeThread)
        if (activeGroupKey in sessionsUiState.collapsedWorkspaceGroupKeys) {
            sessionsUiState.expandWorkspaceGroup(activeGroupKey)
            return
        }

        val collapsedAncestor = ancestorThreadKeys(activeKey, derived.parentByKey)
            .asReversed()
            .firstOrNull { it in sessionsUiState.collapsedSessionNodeKeys }
        if (collapsedAncestor != null) {
            sessionsUiState.expandSessionNode(collapsedAncestor)
            return
        }

        val flatIndex = flatListIndexForThread(
            groups = derived.groups,
            activeKey = activeKey,
            collapsedWorkspaceGroupKeys = sessionsUiState.collapsedWorkspaceGroupKeys,
            collapsedSessionNodeKeys = sessionsUiState.collapsedSessionNodeKeys,
        ) ?: run {
            pendingActiveSessionScroll = false
            return
        }

        pendingActiveSessionScroll = false
        listState.scrollToItem(flatIndex)
    }

    suspend fun loadSessions(force: Boolean = false) {
        if (isLoading) return
        if (!force && hasLoadedInitialSessions) return
        if (connectedServerIds.isEmpty()) {
            isLoading = false
            return
        }

        isLoading = true
        try {
            appModel.refreshSessions(connectedServerIds)
            hasLoadedInitialSessions = true
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    suspend fun forkThread(summary: uniffi.codex_mobile_client.AppSessionSummary) {
        if (isForkingActiveThread) return
        isForkingActiveThread = true
        try {
            val sourceKey = appModel.hydrateThreadPermissions(summary.key) ?: summary.key
            val newKey = appModel.client.forkThread(
                sourceKey.serverId,
                appModel.launchState.threadForkRequest(
                    sourceThreadId = sourceKey.threadId,
                    cwdOverride = summary.cwd,
                    threadKey = sourceKey,
                ),
            )
            appModel.store.setActiveThread(newKey)
            appModel.refreshThreadSnapshot(newKey)
            appModel.launchState.updateCurrentCwd(summary.cwd)
            onOpenConversation(newKey)
        } finally {
            isForkingActiveThread = false
        }
    }

    LaunchedEffect(connectedServerIds) {
        if (connectedServerIds.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        loadSessions(force = hasLoadedInitialSessions)

        // Seed recent directories from loaded sessions.
        val snap = appModel.snapshot.value
        if (snap != null) {
            val recentStore = RecentDirectoryStore(context)
            for (server in snap.servers) {
                val entries = snap.sessionSummaries
                    .filter { it.key.serverId == server.serverId && it.cwd.isNotBlank() }
                    .map { summary ->
                        RecentDirectoryEntry(
                            serverId = server.serverId,
                            path = summary.cwd,
                            lastUsedAtEpochMillis = (summary.updatedAt ?: 0L) * 1000L,
                            useCount = 0,
                        )
                    }
                if (entries.isNotEmpty()) {
                    recentStore.mergeSessionDirectories(server.serverId, entries)
                }
            }
        }

        scheduleActiveSessionScrollIfNeeded()
    }

    LaunchedEffect(snapshot?.activeThread) {
        scheduleActiveSessionScrollIfNeeded()
    }

    LaunchedEffect(derived.workspaceGroupKeys) {
        sessionsUiState.pruneWorkspaceGroupKeys(derived.workspaceGroupKeys.toSet())
    }

    LaunchedEffect(derived.allThreadKeys) {
        sessionsUiState.pruneSessionNodeKeys(derived.allThreadKeys.toSet())
    }

    LaunchedEffect(
        pendingActiveSessionScroll,
        derived.filteredThreadKeys,
        sessionsUiState.collapsedWorkspaceGroupKeys,
        sessionsUiState.collapsedSessionNodeKeys,
    ) {
        scrollToActiveSessionIfNeeded()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        SessionsTopBar(
            title = title,
            derived = derived,
            snapshot = snapshot,
            isForkingActiveThread = isForkingActiveThread,
            isLoading = isLoading,
            hasLoadedInitialSessions = hasLoadedInitialSessions,
            connectedServerIds = connectedServerIds,
            onBack = onBack,
            onForkThread = { summary -> scope.launch { forkThread(summary) } },
            onRefresh = { scope.launch { loadSessions(force = true) } },
            onInfo = onInfo,
        )

        if (serverId != null) {
            Button(
                onClick = { onNewSession?.invoke() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AgentBuddyTheme.accent,
                    contentColor = Color.Black,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("新建会话(对话)")
            }
        }

        // Search bar + filter chips
        SessionsSearchBar(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            sessionsUiState = sessionsUiState,
            onSortModeChanged = { scheduleActiveSessionScrollIfNeeded() },
        )

        SessionsListContent(
            derived = derived,
            isLoading = isLoading,
            listState = listState,
            sessionsUiState = sessionsUiState,
            appModel = appModel,
            onOpenConversation = onOpenConversation,
            onSessionNodeToggled = { scheduleActiveSessionScrollIfNeeded() },
            onForkThread = { summary -> scope.launch { forkThread(summary) } },
        )
    }
}

private fun flatListIndexForThread(
    groups: List<WorkspaceSessionGroup>,
    activeKey: ThreadKey,
    collapsedWorkspaceGroupKeys: Set<String>,
    collapsedSessionNodeKeys: Set<ThreadKey>,
): Int? {
    var flatIndex = 0
    for (group in groups) {
        val groupKey = SessionsDerivation.workspaceGroupKey(group.serverId, group.cwd)
        flatIndex += 1
        if (groupKey in collapsedWorkspaceGroupKeys) {
            continue
        }

        val visibleNodes = visibleSessionRows(group.nodes, collapsedSessionNodeKeys)
        val matchIndex = visibleNodes.indexOfFirst { it.summary.key == activeKey }
        if (matchIndex >= 0) {
            return flatIndex + matchIndex
        }
        flatIndex += visibleNodes.size
    }
    return null
}

private fun ancestorThreadKeys(
    key: ThreadKey,
    parentByKey: Map<ThreadKey, uniffi.codex_mobile_client.AppSessionSummary>,
): List<ThreadKey> {
    val ancestors = mutableListOf<ThreadKey>()
    val visited = mutableSetOf<ThreadKey>()
    var cursor = parentByKey[key]
    while (cursor != null && visited.add(cursor.key)) {
        ancestors += cursor.key
        cursor = parentByKey[cursor.key]
    }
    return ancestors
}
