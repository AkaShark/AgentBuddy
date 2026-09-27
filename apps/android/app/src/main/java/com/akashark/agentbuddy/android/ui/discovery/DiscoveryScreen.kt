package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.SshCredentialStore
import com.akashark.agentbuddy.android.state.isPromptable
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.AppDiscoveredServer

/**
 * Server discovery and connection screen.
 * Displays discovered + saved servers merged.
 */
@Composable
fun DiscoveryScreen(
    discoveredServers: List<AppDiscoveredServer>,
    isScanning: Boolean,
    scanProgress: Float = 0f,
    scanProgressLabel: String? = null,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val logTag = "DiscoveryScreen"
    val appModel = LocalAppModel.current
    val snapshotState = appModel.snapshot.collectAsState()
    val snapshot by snapshotState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sshCredentialStore = remember(context) { SshCredentialStore(context.applicationContext) }

    var showManualEntry by remember { mutableStateOf(false) }
    var showAlleycatSheet by remember { mutableStateOf(false) }
    var showSlingshotComputers by remember { mutableStateOf(false) }
    var pendingManualSshServer by remember { mutableStateOf<SavedServer?>(null) }
    val sshServerState = remember { mutableStateOf<SavedServer?>(null) }
    var sshServer by sshServerState
    val sshAgentContextState = remember { mutableStateOf<SshBridgeAgentContext?>(null) }
    val sshAgentContext by sshAgentContextState
    val connectionChoiceServerState = remember { mutableStateOf<SavedServer?>(null) }
    var connectionChoiceServer by connectionChoiceServerState
    val pendingAutoNavigateServerIdState = remember { mutableStateOf<String?>(null) }
    var pendingAutoNavigateServerId by pendingAutoNavigateServerIdState
    val wakingServerIdState = remember { mutableStateOf<String?>(null) }
    val connectErrorState = remember { mutableStateOf<String?>(null) }
    var connectError by connectErrorState
    val sshHostKeyChangeState = remember { mutableStateOf<SshHostKeyChangePrompt?>(null) }
    var sshHostKeyChange by sshHostKeyChangeState
    val guidedSshAttemptState = remember { mutableStateOf<GuidedSshAttempt?>(null) }
    var guidedSshAttempt by guidedSshAttemptState

    val savedServersState = remember { mutableStateOf(SavedServerStore.load(context)) }
    var savedServers by savedServersState
    LaunchedEffect(Unit) {
        savedServers = SavedServerStore.load(context)
    }

    LaunchedEffect(showManualEntry, pendingManualSshServer) {
        if (!showManualEntry && pendingManualSshServer != null) {
            sshServer = pendingManualSshServer
            pendingManualSshServer = null
        }
    }

    LaunchedEffect(snapshot, pendingAutoNavigateServerId) {
        val pendingServerId = pendingAutoNavigateServerId ?: return@LaunchedEffect
        val serverSnapshot = snapshot?.servers?.firstOrNull { it.serverId == pendingServerId } ?: return@LaunchedEffect
        if (serverSnapshot.isConnected) {
            pendingAutoNavigateServerId = null
            guidedSshAttempt = null
            onDismiss()
        } else if (serverSnapshot.health == AppServerHealth.DISCONNECTED) {
            val progress = serverSnapshot.connectionProgress
            progress?.terminalMessage?.let { message ->
                pendingAutoNavigateServerId = null
                val attempt = guidedSshAttempt?.takeIf { it.server.id == pendingServerId }
                guidedSshAttempt = null
                val mismatch = progress.hostKeyMismatch?.takeIf { it.isPromptable }
                if (attempt != null && mismatch != null) {
                    sshHostKeyChange = SshHostKeyChangePrompt(
                        server = attempt.server,
                        credential = attempt.credential,
                        rememberCredentials = sshCredentialStore.load(
                            attempt.server.hostname,
                            attempt.server.resolvedSshPort,
                        ) != null,
                        mismatch = mismatch,
                    )
                } else {
                    connectError = message
                }
            }
        }
    }

    val connectActions = DiscoveryConnectActions(
        appModel = appModel,
        context = context,
        logTag = logTag,
        onDismiss = onDismiss,
        snapshotState = snapshotState,
        savedServersState = savedServersState,
        wakingServerIdState = wakingServerIdState,
        connectionChoiceServerState = connectionChoiceServerState,
        sshServerState = sshServerState,
        connectErrorState = connectErrorState,
    )
    val sshLoginActions = SshLoginConnectActions(
        appModel = appModel,
        context = context,
        logTag = logTag,
        sshCredentialStore = sshCredentialStore,
        reloadSavedServers = { connectActions.reloadSavedServers() },
        sshServerState = sshServerState,
        sshAgentContextState = sshAgentContextState,
        pendingAutoNavigateServerIdState = pendingAutoNavigateServerIdState,
        sshHostKeyChangeState = sshHostKeyChangeState,
        guidedSshAttemptState = guidedSshAttemptState,
    )

    DiscoveryChooser(
        onPairWithAgentBuddy = { showAlleycatSheet = true },
        onConnectedComputers = { showSlingshotComputers = true },
        onSshOrCodexUrl = { showManualEntry = true },
    )

    if (showManualEntry) {
        ManualEntryDialog(
            onDismiss = { showManualEntry = false },
            onSubmit = { action ->
                when (action) {
                    is ManualEntryAction.Connect -> {
                        showManualEntry = false
                        scope.launch { connectActions.connectSelectedServer(action.server) }
                    }

                    is ManualEntryAction.ContinueWithSsh -> {
                        pendingManualSshServer = action.server
                        showManualEntry = false
                    }
                }
            },
        )
    }

    SlingshotConnectFlow(
        showComputers = showSlingshotComputers,
        scope = scope,
        logTag = logTag,
        reloadSavedServers = { connectActions.reloadSavedServers() },
        onCloseComputers = { showSlingshotComputers = false },
        onConnectError = { connectError = it },
        onDismiss = onDismiss,
    )

    connectionChoiceServer?.let { server ->
        DiscoveryConnectionChoiceDialog(
            server = server,
            onDismiss = { connectionChoiceServer = null },
            onUseCodexPort = { port ->
                connectionChoiceServer = null
                scope.launch { connectActions.connectDirectCodexPort(server, port) }
            },
            onUseSsh = {
                sshServer = server.withPreferredConnection("ssh")
                connectionChoiceServer = null
            },
        )
    }

    sshServer?.let { server ->
        SSHLoginDialog(
            server = server,
            initialCredential = sshCredentialStore.load(server.hostname, server.resolvedSshPort),
            onDismiss = { sshServer = null },
            onConnect = { credential, rememberCredentials ->
                sshLoginActions.connectViaSshLogin(server, credential, rememberCredentials)
            },
        )
    }

    sshHostKeyChange?.let { prompt ->
        SshHostKeyChangedDialog(
            mismatch = prompt.mismatch,
            onDismiss = { sshHostKeyChange = null },
            onConfirm = {
                sshHostKeyChange = null
                scope.launch {
                    sshLoginActions.connectViaSshLogin(prompt.server, prompt.credential, prompt.rememberCredentials)
                        ?.let { connectError = it }
                }
            },
        )
    }

    sshAgentContext?.let { agentContext ->
        SSHAgentPickerDialog(
            context = agentContext,
            onDismiss = {
                scope.launch {
                    sshLoginActions.dismissAgentPicker(agentContext)
                }
            },
            onUseCodex = {
                scope.launch {
                    sshLoginActions.useCodexFromAgentPicker(agentContext)
                }
            },
            onConnect = { selectedKinds ->
                sshLoginActions.connectAgentPickerSelection(agentContext, selectedKinds)
            },
        )
    }

    connectError?.let { message ->
        AlertDialog(
            onDismissRequest = { connectError = null },
            title = { Text("连接失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { connectError = null }) {
                    Text("确定")
                }
            },
        )
    }

    if (showAlleycatSheet) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showAlleycatSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AgentBuddyTheme.background,
        ) {
            AlleycatAddServerSheet(
                onDismiss = { showAlleycatSheet = false },
                startScanningOnAppear = true,
                onConnected = { result ->
                    showAlleycatSheet = false
                    scope.launch {
                        SavedServerStore.rememberAlleycat(
                            context = context,
                            serverId = result.serverId,
                            displayName = result.displayName,
                            nodeId = result.nodeId,
                            relay = result.params.relay,
                            agentName = result.agentName,
                            agentWire = alleycatWireStorageValue(result.agentWire),
                        )
                        connectActions.reloadSavedServers()
                        appModel.refreshSnapshot()
                        pendingAutoNavigateServerId = result.serverId
                    }
                },
            )
        }
    }
}
