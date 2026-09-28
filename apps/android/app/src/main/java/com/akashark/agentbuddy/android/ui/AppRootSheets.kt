package com.akashark.agentbuddy.android.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.LocalAccountLoginRequiredException
import com.akashark.agentbuddy.android.state.NetworkDiscovery
import com.akashark.agentbuddy.android.ui.discovery.AlleycatAddServerSheet
import com.akashark.agentbuddy.android.ui.discovery.AlleycatPairingWatcher
import com.akashark.agentbuddy.android.ui.discovery.DiscoveryScreen
import com.akashark.agentbuddy.android.ui.discovery.saveAlleycatPairing
import com.akashark.agentbuddy.android.ui.home.ProjectPickerSheet
import com.akashark.agentbuddy.android.ui.sessions.DirectoryPickerSheet
import com.akashark.agentbuddy.android.ui.settings.AccountSheet
import com.akashark.agentbuddy.android.ui.settings.SettingsSheet
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AppSnapshotRecord
import uniffi.codex_mobile_client.projectIdFor

/**
 * Root bottom sheets: Discovery, QR pairing, Settings, directory picker,
 * project picker and account. They are not routes; [AppShellState] holds
 * which one is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRootSheets(
    shell: AppShellState,
    appModel: AppModel,
    snapshot: AppSnapshotRecord?,
    projects: List<AppProject>,
    networkDiscovery: NetworkDiscovery,
    navActions: AppNavigationActions,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverOptions = remember(snapshot) { connectedServerOptions(snapshot) }
    var pairedServerId by remember { mutableStateOf<String?>(null) }

    if (shell.showDiscovery) {
        val discoveredServers by networkDiscovery.servers.collectAsState()
        val isScanning by networkDiscovery.isScanning.collectAsState()
        val scanProgress by networkDiscovery.scanProgress.collectAsState()
        val scanProgressLabel by networkDiscovery.scanProgressLabel.collectAsState()
        val closeDiscovery = {
            shell.showDiscovery = false
            networkDiscovery.stopScanning()
        }
        LaunchedEffect(Unit) { networkDiscovery.startScanning(context) }
        BuddyBottomSheet(onDismissRequest = closeDiscovery) {
            DiscoveryScreen(
                discoveredServers = discoveredServers,
                isScanning = isScanning,
                scanProgress = scanProgress,
                scanProgressLabel = scanProgressLabel,
                onRefresh = { networkDiscovery.startScanning(context) },
                onDismiss = closeDiscovery,
            )
        }
    }

    // 「扫码连接」 opens QR pairing directly, without the Discovery chooser.
    if (shell.showQrPairing) {
        BuddyBottomSheet(onDismissRequest = { shell.showQrPairing = false }) {
            AlleycatAddServerSheet(
                onDismiss = { shell.showQrPairing = false },
                startScanningOnAppear = true,
                onConnected = { result ->
                    shell.showQrPairing = false
                    scope.launch {
                        saveAlleycatPairing(context, result)
                        appModel.refreshSnapshot()
                        pairedServerId = result.serverId
                    }
                },
            )
        }
    }
    // Same post-pair check as 添加主机: a terminal connect error is shown.
    pairedServerId?.let { serverId ->
        AlleycatPairingWatcher(
            serverId = serverId,
            onConnected = { pairedServerId = null },
            onFinished = { pairedServerId = null },
        )
    }

    if (shell.showSettings) {
        BuddyBottomSheet(onDismissRequest = { shell.showSettings = false }) {
            SettingsSheet(
                onDismiss = shell::closeSettings,
                onOpenAccount = { serverId ->
                    shell.closeSettings()
                    shell.showAccountForServer = serverId
                },
                initialSubScreen = shell.settingsStartDestination,
                initialEditServerId = shell.settingsEditServerId,
                onInitialEditShown = { shell.settingsEditServerId = null },
                onOpenApps = {
                    shell.closeSettings()
                    shell.navigate(Route.Apps)
                },
            )
        }
    }

    shell.directoryPickerServerId?.let { initialServerId ->
        val close = {
            shell.directoryPickerServerId = null
            shell.directoryPickerForProject = false
        }
        BuddyBottomSheet(onDismissRequest = close) {
            DirectoryPickerSheet(
                servers = serverOptions,
                initialServerId = initialServerId,
                onSelect = { serverId, cwd ->
                    val forProject = shell.directoryPickerForProject
                    close()
                    if (forProject) {
                        // "New project": a temporary project until a task runs there.
                        shell.selectedServerId = serverId
                        val id = projectIdFor(serverId, cwd)
                        shell.selectedProject = projects.firstOrNull { it.id == id }
                            ?: AppProject(id = id, serverId = serverId, cwd = cwd, lastUsedAtMs = null)
                        RecentDirectoryStore(context).record(serverId, cwd)
                    } else {
                        scope.launch {
                            runCatching { navActions.startNewSession(serverId, cwd) }.onFailure { error ->
                                if (error is LocalAccountLoginRequiredException) shell.showAccountForServer = error.serverId
                            }
                        }
                    }
                },
                onDismiss = close,
            )
        }
    }

    if (shell.showProjectPicker) {
        BuddyBottomSheet(onDismissRequest = { shell.showProjectPicker = false }) {
            val serverNames = remember(snapshot) { snapshot?.servers?.associate { it.serverId to it.displayName }.orEmpty() }
            val isLocalById = remember(snapshot) { snapshot?.servers?.associate { it.serverId to it.isLocal }.orEmpty() }
            ProjectPickerSheet(
                projects = projects,
                selectedProjectId = shell.selectedProject?.id,
                serverNamesById = serverNames,
                isLocalById = isLocalById,
                onSelect = { project ->
                    shell.selectedServerId = project.serverId
                    shell.selectedProject = project
                },
                onCreateNew = {
                    shell.showProjectPicker = false
                    navActions.openDirectoryPicker(preferredServerId = shell.selectedServerId, forProject = true)
                },
                onDismiss = { shell.showProjectPicker = false },
            )
        }
    }

    shell.showAccountForServer?.let { serverId ->
        BuddyBottomSheet(onDismissRequest = { shell.showAccountForServer = null }) {
            AccountSheet(serverId = serverId, onDismiss = { shell.showAccountForServer = null })
        }
    }
}
