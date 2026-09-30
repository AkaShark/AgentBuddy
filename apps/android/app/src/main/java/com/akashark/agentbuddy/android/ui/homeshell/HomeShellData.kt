package com.akashark.agentbuddy.android.ui.homeshell

import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.common.runtimeSortIndex
import com.akashark.agentbuddy.android.ui.home.HomeDashboardSupport
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostPresentation
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostSummary
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostsHomeUiState
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectSummaries
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectsHomeUiState
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeHostChoice
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskList
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeUiState
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHostAvailability
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSnapshotRecord
import uniffi.codex_mobile_client.PinnedThreadKey

/** Stored sentinel for the in-process server; mapped for display only. */
private const val THIS_DEVICE_SENTINEL = "This Device"

fun hostDisplayName(name: String): String = if (name == THIS_DEVICE_SENTINEL) "本机" else name

/** Inputs of [buildHomeShellData]: Rust snapshot plus UI-only state. */
class HomeShellInputs(
    val snapshot: AppSnapshotRecord?,
    val pinned: List<PinnedThreadKey>,
    val hidden: List<PinnedThreadKey>,
    val cancelling: Map<String, String>,
    val hydrating: Set<String>,
    val selectedServerId: String?,
    val selectedProject: AppProject?,
    val projects: List<AppProject>,
    val showsDetail: Boolean,
    val savedServers: List<SavedServer>,
    val terminalNodeIds: Map<String, String>,
    val projectId: (serverId: String, cwd: String) -> String,
    val projectName: (cwd: String) -> String,
    val displayPath: (cwd: String, isLocal: Boolean) -> String,
)

/** Everything the three tabs render, projected once per snapshot change. */
class HomeShellData(
    val servers: List<AppServerSnapshot>,
    val allSessions: List<AppSessionSummary>,
    val visibleSessions: List<AppSessionSummary>,
    val runtimeKinds: List<AgentRuntimeKind>,
    val tasks: TasksHomeUiState,
    val projects: ProjectsHomeUiState,
    val hosts: HostsHomeUiState,
    /** Active turn id per task id, for the threads the snapshot has in detail. */
    val activeTurnIds: Map<String, String?>,
    val connectedServerIds: Set<String>,
) {
    /** Changes when the visible list or a host transport changes (re-hydrate). */
    val hydrationSignature: String =
        visibleSessions.joinToString("|") { HomeTaskPresentation.taskId(it.key) } + "#" +
            servers.sortedBy { it.serverId }.joinToString("|") { "${it.serverId}:${it.transportState}:${it.port}" }

    /** Changes when a visible task starts, stops or switches turn, or a host drops (prune stop markers). */
    val activitySignature: String =
        visibleSessions.joinToString("|") {
            val id = HomeTaskPresentation.taskId(it.key)
            "$id:${it.hasActiveTurn}:${activeTurnIds[id]}"
        } + "#" + connectedServerIds.sorted().joinToString("|")
}

fun buildHomeShellData(input: HomeShellInputs): HomeShellData {
    val snapshot = input.snapshot
    val servers = snapshot?.let { HomeDashboardSupport.sortedConnectedServers(it) }.orEmpty()
    // Every session on connected hosts (no sub-agents): search and project counts.
    val allSessions = snapshot?.let { HomeDashboardSupport.recentSessions(it, limit = Int.MAX_VALUE) }.orEmpty()
    val merged = HomeTaskList.merge(input.pinned, input.hidden, servers, allSessions)
    // The current project scopes the list only while its host is connected.
    val projectServerId = input.selectedProject?.serverId?.takeIf { id -> servers.any { it.serverId == id } }
    val scopedServerId = projectServerId ?: input.selectedServerId
    val visible = HomeTaskList.scoped(merged, scopedServerId)
    val activeTurnIds = HomeTaskPresentation.activeTurnIds(snapshot?.threads.orEmpty())
    val connectedServerIds = snapshot?.servers.orEmpty().filter { it.isConnected }.mapTo(HashSet()) { it.serverId }
    val items = HomeTaskPresentation.items(
        sessions = visible,
        pendingApprovals = snapshot?.pendingApprovals.orEmpty(),
        pendingInputs = snapshot?.pendingUserInputs.orEmpty(),
        pinnedKeys = input.pinned,
        cancellingIds = HomeTaskPresentation.pruneCancelling(input.cancelling, visible, activeTurnIds, connectedServerIds).keys,
        hydratingIds = input.hydrating,
        lineageByKey = HomeDashboardSupport.computeLineageMap(allSessions),
    )
    val allServers = snapshot?.servers.orEmpty()
    val tasks = TasksHomeUiState(
        hosts = servers.map { server ->
            HomeHostChoice(
                serverId = server.serverId,
                name = hostDisplayName(server.displayName),
                isConnected = server.health == AppServerHealth.CONNECTED,
                isConnecting = server.health == AppServerHealth.CONNECTING || server.connectionProgress != null,
            )
        },
        selectedServerId = scopedServerId,
        sections = HomeTaskPresentation.sections(items),
        availability = availability(allServers, input.savedServers.isNotEmpty()),
        showsDetail = input.showsDetail,
    )
    val runtimeKinds = servers
        .flatMap { server -> server.agentRuntimes.filter { it.available }.map { it.kind } }
        .distinct()
        .sortedBy { it.runtimeSortIndex }
    return HomeShellData(
        servers = servers,
        allSessions = allSessions,
        visibleSessions = visible,
        runtimeKinds = runtimeKinds,
        tasks = tasks,
        projects = projectsState(input, allServers, servers, allSessions),
        hosts = hostsState(input, allServers, allSessions),
        activeTurnIds = activeTurnIds,
        connectedServerIds = connectedServerIds,
    )
}

private fun availability(servers: List<AppServerSnapshot>, hasSavedHosts: Boolean): TasksHostAvailability =
    when {
        servers.any { it.health == AppServerHealth.CONNECTED } -> TasksHostAvailability.ONLINE
        servers.any {
            it.health == AppServerHealth.CONNECTING || it.health == AppServerHealth.UNKNOWN || it.connectionProgress != null
        } -> TasksHostAvailability.CONNECTING
        servers.isEmpty() && hasSavedHosts -> TasksHostAvailability.CONNECTING
        servers.isEmpty() -> TasksHostAvailability.NO_HOSTS
        else -> TasksHostAvailability.OFFLINE
    }

private fun projectsState(
    input: HomeShellInputs,
    allServers: List<AppServerSnapshot>,
    connectedServers: List<AppServerSnapshot>,
    sessions: List<AppSessionSummary>,
): ProjectsHomeUiState {
    val selected = input.selectedProject
    // A project picked from the directory picker has no task yet; keep it listed.
    val projects = if (selected != null && input.projects.none { it.id == selected.id }) listOf(selected) + input.projects else input.projects
    val serverNames = allServers.associate { it.serverId to hostDisplayName(it.displayName) }
    val isLocal = allServers.associate { it.serverId to it.isLocal }
    val summaries = ProjectSummaries.build(
        projects = projects,
        sessions = sessions,
        serverNames = serverNames,
        idFor = input.projectId,
        nameFor = input.projectName,
        pathFor = { input.displayPath(it.cwd, isLocal[it.serverId] == true) },
    )
    val hero = ProjectSummaries.hero(summaries, selected?.id)
    return ProjectsHomeUiState(
        hero = hero,
        others = summaries.filter { it.id != hero?.id },
        hasHosts = allServers.any { it.health == AppServerHealth.CONNECTED },
        // Tasks hidden from home stay out of the card too (they still count).
        heroRecent = hero?.let { summary ->
            val hidden = input.hidden.toSet()
            ProjectSummaries.recentTasks(summary.project, sessions.filter { HomeTaskList.pinKey(it.key) !in hidden }, input.projectId)
        }.orEmpty(),
        // Same test as the Tasks scoping above, so the two tabs agree while a host reconnects.
        heroHostOnline = hero == null || connectedServers.any { it.serverId == hero.project.serverId },
    )
}

/**
 * Connected hosts first, then by name, one entry per host:port. Sorting runs
 * before the dedupe so a stale disconnected entry never hides the connected
 * one for the same address.
 */
internal fun hostsInDisplayOrder(servers: List<AppServerSnapshot>): List<AppServerSnapshot> {
    val seen = HashSet<String>()
    return servers
        .sortedWith(compareBy<AppServerSnapshot> { if (it.health == AppServerHealth.CONNECTED) 0 else 1 }.thenBy { it.displayName.lowercase() })
        .filter { seen.add("${it.host.lowercase()}:${it.port}") }
}

private fun hostsState(
    input: HomeShellInputs,
    allServers: List<AppServerSnapshot>,
    sessions: List<AppSessionSummary>,
): HostsHomeUiState {
    val savedById = input.savedServers.associateBy { it.id }
    val hosts = hostsInDisplayOrder(allServers)
        .map { server ->
            val saved = savedById[server.serverId]
            HostSummary(
                serverId = server.serverId,
                name = hostDisplayName(server.displayName),
                isLocal = server.isLocal,
                connection = HostPresentation.connectionState(server.health),
                sourceTitle = HostPresentation.sourceTitle(
                    isLocal = server.isLocal,
                    source = saved?.source,
                    hasAlleycatNode = !saved?.alleycatNodeId.isNullOrBlank(),
                    preferredConnectionMode = saved?.preferredConnectionMode,
                    hasWebsocketUrl = !saved?.websocketURL.isNullOrBlank(),
                ),
                partners = server.agentRuntimes.filter { it.available }.map { runtime ->
                    runtime.displayName.ifBlank { runtime.kind.runtimeLabel }
                },
                runningCount = sessions.count { it.key.serverId == server.serverId && it.hasActiveTurn },
                hasTerminal = input.terminalNodeIds.containsKey(server.serverId),
            )
        }
    val primary = HostPresentation.primary(hosts, input.selectedProject?.serverId ?: input.selectedServerId)
    return HostsHomeUiState(primary = primary, others = hosts.filter { it.serverId != primary?.serverId })
}
