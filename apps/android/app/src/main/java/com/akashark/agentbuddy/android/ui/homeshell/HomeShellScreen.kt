package com.akashark.agentbuddy.android.ui.homeshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.PathDisplay
import com.akashark.agentbuddy.android.state.SavedAppsStore
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.ui.AgentBuddyFeature
import com.akashark.agentbuddy.android.ui.ExperimentalFeatures
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.home.DashboardZoomPrefs
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostActionHandlers
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostsHomeCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostsHomeContent
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectsHomeCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectsHomeContent
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TaskActionHandlers
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHeaderActions
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.newtask.NewTaskHostOption
import com.akashark.agentbuddy.android.ui.homeshell.newtask.NewTaskSheet
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.projectDefaultLabel
import uniffi.codex_mobile_client.projectIdFor

/**
 * Home shell (任务 / 项目 / 主机) bound to the app model. Reads the Rust
 * snapshot, projects it for the three tabs and routes every action to
 * [HomeTaskActions], [HomeHostActions] or the root [actions].
 */
@Composable
fun HomeShellScreen(
    actions: HomeShellActions,
    memory: HomeTaskMemory,
    selectedServerId: String?,
    selectedProject: AppProject?,
    projects: List<AppProject>,
    isStartingVoice: Boolean,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by appModel.snapshot.collectAsState()
    val zoomLevel by DashboardZoomPrefs.currentLevel.collectAsState()
    val savedApps by SavedAppsStore.apps.collectAsState()
    var tab by rememberSaveable { mutableStateOf(HomeShellTab.TASKS) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var showNewTask by rememberSaveable { mutableStateOf(false) }
    val dialogs = remember { HomeShellDialogState() }
    val onError: (String, String) -> Unit = { title, message -> dialogs.notice = HomeShellNotice(title, message) }
    val taskActions = remember(appModel, memory) { HomeTaskActions(appModel, context, scope, memory, onError) }
    val hostActions = remember(appModel) { HomeHostActions(appModel, context, scope, onError) }
    val terminalEnabled = ExperimentalFeatures.isEnabled(AgentBuddyFeature.TERMINAL)

    LaunchedEffect(Unit) {
        memory.reload(context)
        // Saved apps stay fresh through AppModel updates; reload on entry
        // catches changes that arrived while home was off screen.
        runCatching { SavedAppsStore.reload(context) }
    }

    val serverIds = snapshot?.servers.orEmpty().map { it.serverId }
    val savedServers = remember(serverIds) { SavedServerStore.load(context) }
    val terminalNodeIds = remember(serverIds, terminalEnabled) {
        if (!terminalEnabled) emptyMap() else serverIds.mapNotNull { id -> HomeHostActions.terminalNodeId(context, id)?.let { id to it } }.toMap()
    }
    val data = remember(
        snapshot, memory.pinned, memory.hidden, memory.cancelling, memory.hydrating,
        selectedServerId, selectedProject, projects, zoomLevel, savedServers, terminalNodeIds,
    ) {
        buildHomeShellData(
            HomeShellInputs(
                snapshot = snapshot,
                pinned = memory.pinned,
                hidden = memory.hidden,
                cancelling = memory.cancelling,
                hydrating = memory.hydrating,
                selectedServerId = selectedServerId,
                selectedProject = selectedProject,
                projects = projects,
                showsDetail = zoomLevel >= 3,
                savedServers = savedServers,
                terminalNodeIds = terminalNodeIds,
                projectId = ::projectIdFor,
                projectName = ::projectDefaultLabel,
                displayPath = { cwd, isLocal -> PathDisplay.display(cwd, isLocal, context) },
            ),
        )
    }

    LaunchedEffect(data.hydrationSignature, memory.pinned) {
        taskActions.hydratePinned(data.visibleSessions, data.servers)
    }
    LaunchedEffect(data.activitySignature) {
        memory.cancelling = HomeTaskPresentation.pruneCancelling(
            memory.cancelling,
            data.visibleSessions,
            data.activeTurnIds,
            data.connectedServerIds,
        )
    }

    val taskHandlers = TaskActionHandlers(
        open = { item -> taskActions.open(item.session, actions.openConversation) },
        reply = { item -> dialogs.replyTarget = item.session },
        stop = { item -> taskActions.stop(item.key) },
        fork = { item -> taskActions.fork(item.session, actions.openConversation) },
        togglePin = { item -> if (item.isPinned) taskActions.unpin(item.key) else taskActions.pin(item.key, data.visibleSessions) },
        hide = { item -> taskActions.hide(item.key) },
        delete = { item -> dialogs.deleteTarget = item.session },
    )
    val headerActions = TasksHeaderActions(
        onSelectHost = { host ->
            if (host.isConnected || host.serverId == selectedServerId) actions.selectServer(host.serverId) else hostActions.reconnect(host.serverId)
        },
        onClearHost = { actions.selectServer(null) },
        onManageHosts = { tab = HomeShellTab.HOSTS },
        onPairWithQr = actions.pairWithQr,
        onShowSettings = actions.showSettings,
        onShowAllTasks = actions.showAllTasks,
        onShowApps = if (savedApps.isNotEmpty()) actions.showApps else null,
        onShowTerminal = actions.showTerminal,
    )
    val newTask = { showNewTask = true }
    val tasksCallbacks = TasksHomeCallbacks(
        onSearch = { isSearching = true },
        onNewTask = newTask,
        onPairWithQr = actions.pairWithQr,
        onOtherConnections = actions.showDiscovery,
        onManageHosts = { tab = HomeShellTab.HOSTS },
    )
    val hostHandlers = HostActionHandlers(
        onTap = { host ->
            if (host.connection == BuddyConnectionState.CONNECTED) {
                actions.selectServer(host.serverId)
                tab = HomeShellTab.TASKS
            } else {
                hostActions.reconnect(host.serverId)
            }
        },
        onStartTask = { host ->
            actions.selectServer(host.serverId)
            showNewTask = true
        },
        onReconnect = { host -> hostActions.reconnect(host.serverId) },
        onRestart = { host -> hostActions.restart(host.serverId, host.isLocal) },
        onRename = { host -> dialogs.renameTarget = host },
        onEdit = { host -> actions.editHost(host.serverId) },
        onRemove = { host -> dialogs.removeTarget = host },
        onTerminal = { host -> terminalNodeIds[host.serverId]?.let(actions.openHostTerminal) },
    )

    val tabStates = rememberSaveableStateHolder()
    HomeShellScaffold(
        selectedTab = tab,
        onSelectTab = { tab = it },
        composerPill = if (isSearching && tab == HomeShellTab.TASKS) {
            null
        } else {
            {
                HomeComposerPill(
                    onCompose = newTask,
                    onVoice = actions.startVoice,
                    isStartingVoice = isStartingVoice,
                    isVoiceActive = snapshot?.voiceSession?.phase != null,
                )
            }
        },
    ) { page ->
        tabStates.SaveableStateProvider(page.name) {
            when (page) {
                HomeShellTab.TASKS -> HomeTasksTab(
                    appModel = appModel,
                    data = data,
                    memory = memory,
                    taskActions = taskActions,
                    taskHandlers = taskHandlers,
                    headerActions = headerActions,
                    callbacks = tasksCallbacks,
                    isSearching = isSearching,
                    onSearchingChange = { isSearching = it },
                )
                HomeShellTab.PROJECTS -> ProjectsHomeContent(
                    state = data.projects,
                    callbacks = ProjectsHomeCallbacks(
                        onNewTask = { project ->
                            actions.selectProject(project)
                            showNewTask = true
                        },
                        onSelect = actions.selectProject,
                        onCreateProject = actions.createProject,
                        onManageHosts = { tab = HomeShellTab.HOSTS },
                        onOpenTask = { session -> taskActions.open(session, actions.openConversation) },
                        onShowProjectTasks = { summary -> actions.showProjectTasks(summary.project, summary.name) },
                    ),
                )
                HomeShellTab.HOSTS -> HostsHomeContent(
                    state = data.hosts,
                    handlers = hostHandlers,
                    callbacks = HostsHomeCallbacks(onAddHost = actions.showDiscovery, onPairWithQr = actions.pairWithQr),
                )
            }
        }
    }

    HomeShellDialogs(state = dialogs, taskActions = taskActions, hostActions = hostActions)

    if (showNewTask) {
        val launchable = data.servers.filter { it.health == AppServerHealth.CONNECTED }.map {
            NewTaskHostOption(serverId = it.serverId, name = hostDisplayName(it.displayName))
        }
        NewTaskSheet(
            // A project on an offline host would only fail to start.
            project = selectedProject?.takeIf { project -> launchable.any { it.serverId == project.serverId } },
            hosts = launchable,
            // With a single connected host there is nothing to choose; show it
            // as the sheet's host without changing the home filter.
            selectedServerId = selectedServerId ?: launchable.singleOrNull()?.serverId,
            draft = memory.newTaskDraft,
            onSelectServer = { actions.selectServer(it) },
            onOpenProjectPicker = actions.openProjectPicker,
            onThreadCreated = { key -> taskActions.pin(key, data.visibleSessions) },
            onLoginRequired = actions.openAccount,
            onDismiss = { showNewTask = false },
        )
    }
}
