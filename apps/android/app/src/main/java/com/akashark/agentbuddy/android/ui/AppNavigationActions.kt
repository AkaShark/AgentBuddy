package com.akashark.agentbuddy.android.ui

import android.content.Context
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.SavedThreadsStore
import com.akashark.agentbuddy.android.state.VoiceRuntimeController
import com.akashark.agentbuddy.android.state.connectionModeLabel
import com.akashark.agentbuddy.android.ui.home.HomeDashboardSupport
import com.akashark.agentbuddy.android.ui.homeshell.HomeHostActions
import com.akashark.agentbuddy.android.ui.homeshell.HomeShellActions
import com.akashark.agentbuddy.android.ui.sessions.DirectoryPickerServerOption
import com.akashark.agentbuddy.android.ui.sessions.SessionLaunchSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSnapshotRecord
import uniffi.codex_mobile_client.PinnedThreadKey

/** Hosts the directory picker can browse / start sessions on. */
fun connectedServerOptions(snapshot: AppSnapshotRecord?): List<DirectoryPickerServerOption> =
    snapshot?.let { snap ->
        HomeDashboardSupport.sortedConnectedServers(snap).map { server ->
            DirectoryPickerServerOption(id = server.serverId, name = server.displayName, sourceLabel = server.connectionModeLabel)
        }
    }.orEmpty()

/**
 * Navigation-level actions of the app root: starting sessions from the
 * directory picker, realtime voice from home, the remote shell launcher and
 * the [HomeShellActions] the home shell gets.
 */
class AppNavigationActions(
    private val appModel: AppModel,
    private val context: Context,
    private val scope: CoroutineScope,
    private val shell: AppShellState,
) {
    /** New session from the directory picker: start, record, pin, open. */
    suspend fun startNewSession(serverId: String, cwd: String) {
        val serverIsLocal = appModel.snapshot.value?.servers?.firstOrNull { it.serverId == serverId }?.isLocal == true
        val startedKey = appModel.startThread(serverId, appModel.launchState.threadStartRequest(cwd, serverIsLocal = serverIsLocal))
        RecentDirectoryStore(context).record(serverId, cwd)
        SavedThreadsStore.add(context, PinnedThreadKey(serverId = startedKey.serverId, threadId = startedKey.threadId))
        appModel.store.setActiveThread(startedKey)
        appModel.refreshThreadSnapshot(startedKey)
        val resolvedKey = appModel.ensureThreadLoaded(startedKey)
            ?: appModel.snapshot.value?.threads?.firstOrNull { it.key == startedKey }?.key
            ?: startedKey
        shell.navigateToConversation(resolvedKey)
    }

    /** Opens the directory picker on a connected host, or Discovery when none is connected. */
    fun openDirectoryPicker(preferredServerId: String?, forProject: Boolean = false) {
        val snapshot = appModel.snapshot.value
        val targetServerId = SessionLaunchSupport.defaultConnectedServerId(
            connectedServerIds = connectedServerOptions(snapshot).map { it.id },
            activeThreadKey = snapshot?.activeThread,
            preferredServerId = preferredServerId,
        )
        if (targetServerId == null) {
            shell.showDiscovery = true
        } else {
            shell.directoryPickerForProject = forProject
            shell.directoryPickerServerId = targetServerId
        }
    }

    /** Home voice button: prepare the pinned local voice thread, then open the call. */
    fun startHomeVoice() {
        if (shell.isStartingVoice) return
        shell.isStartingVoice = true
        scope.launch {
            try {
                val launchState = appModel.launchState.snapshot.value
                val threadKey = VoiceRuntimeController.shared.preparePinnedLocalVoiceThread(
                    appModel = appModel,
                    cwd = launchState.currentCwd.ifBlank { "~" },
                    model = launchState.selectedModel.ifBlank { null },
                )
                if (threadKey != null) shell.navigate(Route.RealtimeVoice(threadKey))
            } finally {
                shell.isStartingVoice = false
            }
        }
    }

    /** Remote shell for a paired alleycat host, or null when unavailable. */
    fun remoteShellLauncher(serverId: String, terminalEnabled: Boolean): (() -> Unit)? {
        if (!terminalEnabled) return null
        val nodeId = HomeHostActions.terminalNodeId(context, serverId) ?: return null
        return { shell.navigate(Route.Terminal(preferredAlleycatNodeId = nodeId)) }
    }

    fun homeShellActions(voiceEnabled: Boolean, terminalEnabled: Boolean): HomeShellActions =
        HomeShellActions(
            openConversation = shell::navigateToConversation,
            showAllTasks = { shell.navigate(Route.Sessions(serverId = null, title = "全部任务")) },
            openProjectPicker = { shell.showProjectPicker = true },
            openAccount = { serverId -> shell.showAccountForServer = serverId },
            startVoice = if (voiceEnabled) ::startHomeVoice else null,
            selectServer = { serverId ->
                shell.selectedServerId = serverId
                if (serverId == null) shell.selectedProject = null
            },
            selectProject = { project ->
                shell.selectedServerId = project.serverId
                shell.selectedProject = project
            },
            createProject = {
                openDirectoryPicker(preferredServerId = shell.selectedServerId, forProject = true)
            },
            pairWithQr = { shell.showQrPairing = true },
            showDiscovery = { shell.showDiscovery = true },
            showSettings = { shell.openSettings() },
            showApps = { shell.navigate(Route.Apps) },
            showTerminal = if (terminalEnabled) ({ shell.navigate(Route.Terminal()) }) else null,
            openHostTerminal = { nodeId -> shell.navigate(Route.Terminal(preferredAlleycatNodeId = nodeId)) },
            editHost = { serverId -> shell.openSettings(editServerId = serverId) },
        )
}
