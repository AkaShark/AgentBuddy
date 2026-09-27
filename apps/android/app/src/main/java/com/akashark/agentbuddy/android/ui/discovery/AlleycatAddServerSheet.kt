package com.akashark.agentbuddy.android.ui.discovery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.akashark.agentbuddy.android.core.bridge.UniffiInit
import com.akashark.agentbuddy.android.state.AlleycatCredentialStore
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.common.isBetaAgentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.codex_mobile_client.AlleycatBridge
import uniffi.codex_mobile_client.AppAlleycatAgentInfo
import uniffi.codex_mobile_client.AppAlleycatAgentWire
import uniffi.codex_mobile_client.AppAlleycatPairPayload

data class AlleycatConnectedTarget(
    val serverId: String,
    val nodeId: String,
    val displayName: String,
    val params: AppAlleycatPairPayload,
    val agentName: String,
    val agentWire: AppAlleycatAgentWire,
)

internal const val LOG_TAG = "AlleycatSheet"

@Composable
fun AlleycatAddServerSheet(
    onDismiss: () -> Unit,
    onConnected: (AlleycatConnectedTarget) -> Unit,
    startScanningOnAppear: Boolean = false,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialStore = remember(context) {
        AlleycatCredentialStore(context.applicationContext)
    }
    val alleycatBridge = remember { AlleycatBridge() }

    var displayName by remember { mutableStateOf("") }
    var parsedParams by remember { mutableStateOf<AppAlleycatPairPayload?>(null) }
    var agents by remember { mutableStateOf<List<AppAlleycatAgentInfo>>(emptyList()) }
    var selectedAgentNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingAgents by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var agentError by remember { mutableStateOf<String?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteJson by remember { mutableStateOf("") }
    var cameraDenied by remember { mutableStateOf(false) }

    fun loadAgents(params: AppAlleycatPairPayload) {
        isLoadingAgents = true
        agentError = null
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    UniffiInit.ensure(context.applicationContext)
                    appModel.serverBridge.listAlleycatAgents(params)
                }
                if (parsedParams?.nodeId == params.nodeId) {
                    agents = loaded
                    selectedAgentNames = loaded
                        .filter { it.available && !isBetaAgentName(it.name, it.displayName) }
                        .map { it.name }
                        .toSet()
                    isLoadingAgents = false
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "listAlleycatAgents failed", e)
                if (parsedParams?.nodeId == params.nodeId) {
                    agents = emptyList()
                    selectedAgentNames = emptySet()
                    isLoadingAgents = false
                    agentError = e.message ?: "无法列出智能体"
                }
            }
        }
    }

    fun handleScannedPayload(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        try {
            val params = alleycatBridge.parsePairPayload(trimmed)
            parsedParams = params
            displayName = suggestedDisplayName(params)
            agents = emptyList()
            selectedAgentNames = emptySet()
            parseError = null
            agentError = null
            connectError = null
            loadAgents(params)
        } catch (e: Exception) {
            parsedParams = null
            agents = emptyList()
            selectedAgentNames = emptySet()
            parseError = e.message ?: "配对数据无效"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraDenied = false
            showScanner = true
        } else {
            cameraDenied = true
        }
    }

    fun requestCameraAndScan() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                cameraDenied = false
                showScanner = true
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var autoStartTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(startScanningOnAppear) {
        if (startScanningOnAppear && !autoStartTriggered) {
            autoStartTriggered = true
            requestCameraAndScan()
        }
    }

    fun connect() {
        val params = parsedParams ?: return
        val selectedAgents = agents.filter { it.available && it.name in selectedAgentNames }
        val fallbackAgent = selectedAgents.firstOrNull() ?: return
        val trimmedDisplay = displayName.trim()
        val resolvedName = trimmedDisplay.ifEmpty { suggestedDisplayName(params) }
        val serverId = "alleycat:${params.nodeId}"

        isConnecting = true
        connectError = null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    UniffiInit.ensure(context.applicationContext)
                    appModel.serverBridge.connectRemoteOverAlleycat(
                        serverId = serverId,
                        displayName = resolvedName,
                        params = params,
                        agentName = fallbackAgent.name,
                        selectedAgentNames = selectedAgents.map { it.name },
                        wire = fallbackAgent.wire,
                    )
                }
                runCatching {
                    credentialStore.saveToken(params.nodeId, params.token)
                }.onFailure {
                    Log.w(LOG_TAG, "Alleycat token save failed", it)
                }
                isConnecting = false
                onConnected(
                    AlleycatConnectedTarget(
                        serverId = result.serverId,
                        nodeId = result.nodeId,
                        displayName = resolvedName,
                        params = params,
                        agentName = result.agentName,
                        agentWire = fallbackAgent.wire,
                    )
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "connectRemoteOverAlleycat failed", e)
                isConnecting = false
                connectError = e.message ?: "无法连接"
            }
        }
    }

    val availableAgents = agents.filter { it.available }
    val selectedAgents = agents.filter { it.available && it.name in selectedAgentNames }
    val canConnect = !isConnecting && !isLoadingAgents && parsedParams != null && selectedAgents.isNotEmpty()

    if (showScanner) {
        QrScannerScreen(
            onScanned = { payload ->
                showScanner = false
                handleScannedPayload(payload)
            },
            onCancel = { showScanner = false },
        )
        return
    }

    AlleycatPairContent(
        state = AlleycatPairViewState(
            params = parsedParams,
            displayName = displayName,
            agentOptions = agents.map { agent ->
                DiscoveryAgentOption(
                    key = agent.name,
                    kind = agent.name,
                    title = agent.displayName,
                    detail = alleycatWireStorageValue(agent.wire),
                    isBeta = isBetaAgentName(agent.name, agent.displayName),
                    selectable = agent.available,
                    selected = agent.name in selectedAgentNames,
                    unavailableNote = "不可用",
                )
            },
            hasAvailableAgents = availableAgents.isNotEmpty(),
            allAgentsSelected = selectedAgents.size == availableAgents.size,
            isLoadingAgents = isLoadingAgents,
            parseError = parseError,
            agentError = agentError,
            connectError = connectError,
            isConnecting = isConnecting,
            canConnect = canConnect,
            cameraDenied = cameraDenied,
            showPaste = showPaste,
            pasteJson = pasteJson,
        ),
        actions = AlleycatPairCallbacks(
            onCancel = onDismiss,
            onScan = ::requestCameraAndScan,
            onOpenSettings = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { Log.w(LOG_TAG, "open app settings failed", it) }
            },
            onTogglePaste = { showPaste = !showPaste },
            onPasteJsonChange = { pasteJson = it },
            onPasteFromClipboard = {
                clipboardManager.getText()?.text?.let { pasteJson = it }
            },
            onParse = { handleScannedPayload(pasteJson) },
            onDisplayNameChange = { displayName = it },
            onToggleAllAgents = {
                selectedAgentNames = if (selectedAgents.size == availableAgents.size) {
                    emptySet()
                } else {
                    availableAgents.map { it.name }.toSet()
                }
            },
            onAgentToggle = { option, checked ->
                val agent = agents.firstOrNull { it.name == option.key }
                if (agent != null && agent.available) {
                    selectedAgentNames = if (checked) {
                        selectedAgentNames + agent.name
                    } else {
                        selectedAgentNames - agent.name
                    }
                }
            },
            onConnect = ::connect,
        ),
    )
}

internal fun shortNodeId(raw: String): String =
    if (raw.length <= 16) raw else raw.take(8) + "..." + raw.takeLast(8)

private fun suggestedDisplayName(params: AppAlleycatPairPayload): String =
    params.hostName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Alleycat ${shortNodeId(params.nodeId)}"

fun alleycatWireStorageValue(wire: AppAlleycatAgentWire): String = when (wire) {
    AppAlleycatAgentWire.WEBSOCKET -> "websocket"
    AppAlleycatAgentWire.JSONL -> "jsonl"
}
