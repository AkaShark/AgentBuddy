package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.PathDisplay
import com.akashark.agentbuddy.android.state.VoiceRuntimeController
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.RecentDirectoryEntry
import com.akashark.agentbuddy.android.ui.RecentDirectoryStore
import com.akashark.agentbuddy.android.ui.homeshell.forkSessionThread
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectSummaries
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.projectIdFor

/**
 * 全部任务: every task on the connected hosts, grouped by project, with
 * search, host / fork filters and sorting. Refreshes sessions on entry, seeds
 * [RecentDirectoryStore] from them and scrolls to the open task.
 */
@Composable
fun SessionsScreen(
    serverId: String?,
    title: String,
    sessionsUiState: SessionsUiState,
    /** Limits the list to this project on [serverId] (no host filter shown). */
    projectCwd: String? = null,
    onOpenConversation: (ThreadKey) -> Unit,
    onNewSession: (() -> Unit)? = null,
    onBack: () -> Unit,
    onInfo: (() -> Unit)? = null,
    /** The home's 「正在停止…」 markers (task id → turn id), so both lists agree. */
    stopMarkers: Map<String, String> = emptyMap(),
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
    // A project page is scoped to its host up front, so it starts without a host filter.
    var serverFilterId by remember(serverId, projectCwd) { mutableStateOf(if (projectCwd == null) serverId else null) }
    val projectId = remember(serverId, projectCwd) {
        if (serverId != null && projectCwd != null) projectIdFor(serverId, projectCwd) else null
    }
    var renameTarget by remember { mutableStateOf<AppSessionSummary?>(null) }
    var archiveTarget by remember { mutableStateOf<AppSessionSummary?>(null) }
    val voiceController = remember { VoiceRuntimeController.shared }
    var isLoading by remember { mutableStateOf(false) }
    var isForkingActiveThread by remember { mutableStateOf(false) }
    var forkError by remember { mutableStateOf<String?>(null) }
    var hasLoadedInitialSessions by remember { mutableStateOf(false) }
    var pendingActiveSessionScroll by remember { mutableStateOf(false) }
    val derived = remember(
        snapshot,
        searchQuery,
        serverFilterId,
        projectId,
        sessionsUiState.sortMode,
        sessionsUiState.showOnlyForks,
    ) {
        val all = snapshot?.sessionSummaries ?: emptyList()
        // Sub-agent runs stay in, nested under their parent as on 全部任务.
        val summaries = if (projectId != null && serverId != null) {
            ProjectSummaries.tasksOf(projectId, serverId, all, ::projectIdFor)
        } else {
            all
        }
        SessionsDerivation.derive(
            summaries = summaries,
            serverFilter = serverFilterId,
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
        listState.scrollToItem(SESSIONS_HEADER_ITEM_COUNT + flatIndex)
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

    suspend fun forkThread(summary: AppSessionSummary) {
        if (isForkingActiveThread) return
        isForkingActiveThread = true
        try {
            onOpenConversation(forkSessionThread(appModel, summary))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            forkError = e.message ?: "分叉任务失败"
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

    // A host filter pointing at a host that went away falls back to all hosts.
    LaunchedEffect(connectedServerIds) {
        val filter = serverFilterId
        if (filter != null && filter != serverId && filter !in connectedServerIds) {
            serverFilterId = null
        }
    }

    val summaries = snapshot?.sessionSummaries.orEmpty()
    fun summaryFor(key: ThreadKey): AppSessionSummary? = summaries.firstOrNull { it.key == key }
    val activeSummary = snapshot?.activeThread?.let { activeKey -> summaries.firstOrNull { it.key == activeKey } }
    val localServerIds = snapshot?.servers?.filter { it.isLocal }?.map { it.serverId }?.toSet().orEmpty()
    val stoppingIds = HomeTaskPresentation.pruneCancelling(
        cancelling = stopMarkers,
        sessions = summaries,
        activeTurnIds = HomeTaskPresentation.activeTurnIds(snapshot?.threads.orEmpty()),
        connectedServerIds = connectedServerIds.toSet(),
    ).keys
    val viewState = SessionsViewState(
        title = title,
        totalCount = derived.totalCount,
        filteredCount = derived.filteredCount,
        connectedHostCount = connectedServerIds.size,
        groups = buildSessionsGroups(
            derived = derived,
            allSummaries = summaries,
            activeKey = snapshot?.activeThread,
            collapsedGroupKeys = sessionsUiState.collapsedWorkspaceGroupKeys,
            collapsedNodeKeys = sessionsUiState.collapsedSessionNodeKeys,
            pathLabel = { groupServerId, cwd ->
                PathDisplay.display(cwd, groupServerId in localServerIds, context)
            },
            approvalIds = HomeTaskPresentation.approvalCounts(snapshot?.pendingApprovals.orEmpty()).keys,
            inputIds = HomeTaskPresentation.inputIds(snapshot?.pendingUserInputs.orEmpty()),
            stoppingIds = stoppingIds,
        ),
        isLoading = isLoading,
        hasLoadedInitialSessions = hasLoadedInitialSessions,
        searchQuery = searchQuery,
        serverOptions = snapshot?.servers
            ?.takeIf { projectCwd == null }
            ?.filter { it.isConnected }
            ?.sortedBy { it.serverId }
            ?.map { SessionsServerOption(it.serverId, sessionsHostLabel(it.displayName)) }
            .orEmpty(),
        serverFilterId = serverFilterId,
        showOnlyForks = sessionsUiState.showOnlyForks,
        sortMode = sessionsUiState.sortMode,
        canForkCurrent = activeSummary?.let { !it.hasActiveTurn },
        isForkingCurrent = isForkingActiveThread,
        showsInfo = onInfo != null,
        canCreateTask = onNewSession != null,
    )

    SessionsContent(
        state = viewState,
        listState = listState,
        actions = SessionsCallbacks(
            onBack = onBack,
            onRefresh = { scope.launch { loadSessions(force = true) } },
            onInfo = onInfo,
            onForkCurrent = { activeSummary?.let { summary -> scope.launch { forkThread(summary) } } },
            onNewTask = onNewSession,
            onConnectHost = onNewSession,
            onSearchQueryChange = { searchQuery = it },
            onSelectServer = { serverFilterId = it },
            onToggleForksOnly = { sessionsUiState.showOnlyForks = !sessionsUiState.showOnlyForks },
            onSelectSort = { mode ->
                sessionsUiState.sortMode = mode
                scheduleActiveSessionScrollIfNeeded()
            },
            onClearFilters = {
                serverFilterId = null
                sessionsUiState.showOnlyForks = false
            },
            onToggleGroup = { groupKey -> sessionsUiState.toggleWorkspaceGroup(groupKey) },
            onToggleNode = { key ->
                sessionsUiState.toggleSessionNode(key)
                scheduleActiveSessionScrollIfNeeded()
            },
            onOpen = { key ->
                summaryFor(key)?.let { summary ->
                    appModel.launchState.updateCurrentCwd(summary.cwd)
                    onOpenConversation(summary.key)
                }
            },
            onFork = { key -> summaryFor(key)?.let { summary -> scope.launch { forkThread(summary) } } },
            onRename = { key -> renameTarget = summaryFor(key) },
            onArchive = { key -> archiveTarget = summaryFor(key) },
        ),
    )

    renameTarget?.let { summary ->
        SessionRenameDialog(
            summary = summary,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                scope.launch { renameSession(appModel, summary, newName) }
            },
        )
    }

    forkError?.let { message ->
        SessionForkErrorDialog(message = message, onDismiss = { forkError = null })
    }

    archiveTarget?.let { summary ->
        SessionArchiveDialog(
            summary = summary,
            onDismiss = { archiveTarget = null },
            onConfirm = {
                archiveTarget = null
                scope.launch { archiveSession(appModel, voiceController, summary) }
            },
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
    parentByKey: Map<ThreadKey, AppSessionSummary>,
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
