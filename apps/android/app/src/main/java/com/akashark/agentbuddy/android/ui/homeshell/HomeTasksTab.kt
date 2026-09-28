package com.akashark.agentbuddy.android.ui.homeshell

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TaskActionHandlers
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHeaderActions
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeContent
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeHeader
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksSearchCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksSearchContent
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksSearchUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 任务 tab state: the task list, plus the search mode that keeps the old
 * search overlay's behaviour — 250 ms debounce, server-side
 * `refreshThreadSearchSessions`, partner pills, pull-to-refresh with force
 * repair, fork clusters, and tap-to-pin.
 */
@Composable
fun HomeTasksTab(
    appModel: AppModel,
    data: HomeShellData,
    memory: HomeTaskMemory,
    taskActions: HomeTaskActions,
    taskHandlers: TaskActionHandlers,
    headerActions: TasksHeaderActions,
    callbacks: TasksHomeCallbacks,
    isSearching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var runtimeKind by rememberSaveable { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun closeSearch() {
        onSearchingChange(false)
        query = ""
        runtimeKind = null
    }

    BackHandler(enabled = isSearching) { closeSearch() }

    LaunchedEffect(data.runtimeKinds) {
        if (runtimeKind != null && runtimeKind !in data.runtimeKinds) runtimeKind = null
    }

    LaunchedEffect(isSearching, query, runtimeKind) {
        if (!isSearching) return@LaunchedEffect
        if (query.isNotBlank()) delay(250)
        isRefreshing = true
        runCatching { appModel.refreshThreadSearchSessions(query = query, runtimeKind = runtimeKind, forceRepair = false) }
        isRefreshing = false
    }

    if (!isSearching) {
        TasksHomeContent(
            state = data.tasks,
            headerActions = headerActions,
            taskHandlers = taskHandlers,
            callbacks = callbacks,
        )
        return
    }

    TasksSearchContent(
        state = TasksSearchUiState(
            query = query,
            sessions = data.allSessions,
            pinnedKeys = memory.pinned.toSet(),
            runtimeKinds = data.runtimeKinds,
            selectedRuntimeKind = runtimeKind,
            isRefreshing = isRefreshing,
        ),
        callbacks = TasksSearchCallbacks(
            onQueryChange = { query = it },
            onRuntimeSelected = { runtimeKind = it },
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    runCatching { appModel.refreshThreadSearchSessions(query = query, runtimeKind = runtimeKind, forceRepair = true) }
                    isRefreshing = false
                }
            },
            onPin = { session ->
                taskActions.pin(session.key, data.visibleSessions)
                runtimeKind = null
            },
            onUnpin = { session -> taskActions.unpin(session.key) },
            onClear = {
                query = ""
                runtimeKind = null
            },
            onCancel = ::closeSearch,
        ),
        header = { TasksHomeHeader(data.tasks.hosts, data.tasks.selectedServerId, headerActions) },
    )
}
