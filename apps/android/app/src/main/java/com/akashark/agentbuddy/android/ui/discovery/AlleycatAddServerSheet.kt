package com.akashark.agentbuddy.android.ui.discovery

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.akashark.agentbuddy.android.core.bridge.UniffiInit
import com.akashark.agentbuddy.android.state.AlleycatCredentialStore
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.common.isBetaAgentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.codex_mobile_client.AppAlleycatAgentInfo
import uniffi.codex_mobile_client.AppAlleycatAgentWire
import uniffi.codex_mobile_client.AppAlleycatPairPayload
import uniffi.codex_mobile_client.AlleycatBridge

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
    androidx.compose.runtime.LaunchedEffect(startScanningOnAppear) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "添加远程主机",
                color = AgentBuddyTheme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss, enabled = !isConnecting) {
                Text("取消", color = AgentBuddyTheme.accent)
            }
        }

        SectionHeader(label = "配对")
        OutlinedButton(
            onClick = ::requestCameraAndScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (parsedParams == null) "扫描配对二维码" else "重新扫描二维码",
                color = AgentBuddyTheme.accent,
            )
        }
        if (cameraDenied) {
            Text(
                text = "扫描配对二维码需要相机权限。请在系统设置中授予权限，或在下方粘贴 JSON。",
                color = AgentBuddyTheme.warning,
                fontSize = 11.sp,
            )
        }

        AlleycatPastePairJsonSection(
            showPaste = showPaste,
            onTogglePaste = { showPaste = !showPaste },
            pasteJson = pasteJson,
            onPasteJsonChange = { pasteJson = it },
            onPasteFromClipboard = {
                clipboardManager.getText()?.text?.let { pasteJson = it }
            },
            parsedParams = parsedParams,
            onParse = { handleScannedPayload(pasteJson) },
        )

        parseError?.let { message ->
            Text(message, color = AgentBuddyTheme.warning, fontSize = 12.sp)
        }

        val params = parsedParams
        if (params != null) {
            AlleycatScannedHostSection(
                params = params,
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                agents = agents,
                availableAgents = availableAgents,
                selectedAgents = selectedAgents,
                selectedAgentNames = selectedAgentNames,
                isLoadingAgents = isLoadingAgents,
                onToggleAllAgents = {
                    selectedAgentNames = if (selectedAgents.size == availableAgents.size) {
                        emptySet()
                    } else {
                        availableAgents.map { it.name }.toSet()
                    }
                },
                onAgentCheckedChange = { agent, checked ->
                    if (agent.available) {
                        selectedAgentNames = if (checked) {
                            selectedAgentNames + agent.name
                        } else {
                            selectedAgentNames - agent.name
                        }
                    }
                },
            )
        }

        agentError?.let { message ->
            Text(message, color = AgentBuddyTheme.warning, fontSize = 12.sp)
        }

        Button(
            onClick = ::connect,
            enabled = canConnect,
            colors = ButtonDefaults.buttonColors(
                containerColor = AgentBuddyTheme.accent.copy(alpha = 0.18f),
                contentColor = AgentBuddyTheme.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AgentBuddyTheme.accent,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("连接")
        }

        connectError?.let { message ->
            Text(message, color = AgentBuddyTheme.danger, fontSize = 12.sp)
        }
    }
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
