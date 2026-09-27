package com.akashark.agentbuddy.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.auth.ChatGPTOAuthActivity
import com.akashark.agentbuddy.android.state.ChatGPTOAuth
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppServerSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerEditSheet(
    server: AppServerSnapshot,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onTriggerSshReconnect: (SavedServer) -> Unit,
) {
    val context = LocalContext.current
    val appModel = LocalAppModel.current
    val scope = rememberCoroutineScope()

    val savedServers = remember { SavedServerStore.load(context) }
    val originalSaved = remember(savedServers, server.serverId) {
        savedServers.firstOrNull { it.id == server.serverId }
    }

    val resolvedMode = remember(originalSaved, server.isLocal) {
        when {
            server.isLocal -> ServerConnectionMode.LOCAL
            originalSaved?.websocketURL?.let(::isSettingsSlingshotUrl) == true -> ServerConnectionMode.SLINGSHOT
            originalSaved?.websocketURL != null -> ServerConnectionMode.WEBSOCKET
            originalSaved?.preferredConnectionMode == "ssh" || (originalSaved?.sshPort != null && originalSaved?.hasCodexServer == false) -> ServerConnectionMode.SSH
            else -> ServerConnectionMode.DIRECT_CODEX
        }
    }
    var displayName by remember { mutableStateOf(originalSaved?.name?.trim()?.takeIf { it.isNotEmpty() } ?: server.displayName) }
    var connectionMode by remember { mutableStateOf(resolvedMode) }
    var host by remember { mutableStateOf(originalSaved?.hostname?.trim()?.takeIf { it.isNotEmpty() } ?: server.host) }
    var codexPort by remember { mutableStateOf(originalSaved?.preferredCodexPort?.toString() ?: originalSaved?.port?.takeIf { it > 0 }?.toString() ?: "8390") }
    var websocketURL by remember { mutableStateOf(originalSaved?.websocketURL ?: "") }
    var sshPort by remember { mutableStateOf(originalSaved?.sshPort?.toString() ?: "22") }
    var wakeMAC by remember { mutableStateOf(originalSaved?.wakeMAC ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isReconnecting by remember { mutableStateOf(false) }
    var pendingSlingshotReconnect by remember { mutableStateOf<SavedServer?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun validateAndBuild(): SavedServer? = serverEditValidateAndBuild(
        server = server,
        originalSaved = originalSaved,
        displayName = displayName,
        connectionMode = connectionMode,
        host = host,
        codexPort = codexPort,
        websocketURL = websocketURL,
        sshPort = sshPort,
        wakeMAC = wakeMAC,
        onValidationError = { validationError = it },
    )

    fun persist(saved: SavedServer) = serverEditPersist(context, appModel, saved)

    suspend fun connectSlingshotSaved(saved: SavedServer, stepUpToken: String) =
        serverEditConnectSlingshotSaved(context, appModel, saved, stepUpToken)

    suspend fun reconnectSaved(saved: SavedServer) = serverEditReconnectSaved(context, appModel, saved)

    val slingshotStepUpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val saved = pendingSlingshotReconnect
        pendingSlingshotReconnect = null
        if (saved == null) {
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            validationError = result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
                ?: "远程控制授权已取消。"
            isReconnecting = false
            return@rememberLauncherForActivityResult
        }
        val stepUpToken = ChatGPTOAuthActivity.parseRemoteControlStepUpToken(result.data)
        if (stepUpToken == null) {
            validationError = "远程控制授权返回的凭据不完整。"
            isReconnecting = false
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            isReconnecting = true
            try {
                connectSlingshotSaved(saved, stepUpToken)
                onSave()
            } catch (e: Exception) {
                validationError = e.message
            } finally {
                isReconnecting = false
            }
        }
    }

    fun launchSlingshotStepUp(saved: SavedServer) {
        try {
            pendingSlingshotReconnect = saved
            slingshotStepUpLauncher.launch(
                ChatGPTOAuthActivity.createIntent(
                    context,
                    ChatGPTOAuth.createRemoteControlEnrollmentAttempt(),
                ),
            )
        } catch (e: Exception) {
            pendingSlingshotReconnect = null
            validationError = e.localizedMessage ?: e.message ?: "无法授权远程控制。"
        }
    }

    val isPaired = originalSaved?.alleycatNodeId != null || originalSaved?.alleycatAgentWire == "ssh-bridge"

    fun saveAndReconnect() {
        validationError = null
        val saved = validateAndBuild() ?: return
        persist(saved)
        // SSH mode requires interactive credentials, mirroring iOS:
        // hand off to the parent which will open SSHLoginDialog.
        if (connectionMode == ServerConnectionMode.SSH && !server.isLocal) {
            onTriggerSshReconnect(saved)
            return
        }
        scope.launch {
            isReconnecting = true
            try {
                reconnectSaved(saved)
                onSave()
            } catch (e: Exception) {
                isReconnecting = false
                if (
                    connectionMode == ServerConnectionMode.SLINGSHOT &&
                    ChatGPTOAuth.isRemoteControlAuthorizationRequired(e)
                ) {
                    launchSlingshotStepUp(saved)
                } else {
                    validationError = e.message
                }
            } finally {
                if (pendingSlingshotReconnect == null) {
                    isReconnecting = false
                }
            }
        }
    }

    BuddyBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ServerEditContent(
            state = ServerEditFormState(
                kind = when {
                    isPaired -> ServerEditKind.PAIRED
                    server.isLocal -> ServerEditKind.LOCAL
                    connectionMode == ServerConnectionMode.SLINGSHOT -> ServerEditKind.SLINGSHOT
                    else -> ServerEditKind.EDITABLE
                },
                displayName = displayName,
                mode = connectionMode,
                host = host,
                sshPort = sshPort,
                wakeMac = wakeMAC,
                codexPort = codexPort,
                websocketUrl = websocketURL,
                isLocal = server.isLocal,
                showReconnect = server.isLocal || !isPaired,
                isReconnecting = isReconnecting,
            ),
            actions = ServerEditFormActions(
                onDismiss = onDismiss,
                onDisplayNameChange = { displayName = it },
                onModeChange = { connectionMode = it },
                onHostChange = { host = it },
                onSshPortChange = { sshPort = it },
                onWakeMacChange = { wakeMAC = it },
                onCodexPortChange = { codexPort = it },
                onWebsocketUrlChange = { websocketURL = it },
                onSave = {
                    validationError = null
                    val saved = validateAndBuild()
                    if (saved != null) {
                        persist(saved)
                        onSave()
                    }
                },
                onSaveAndReconnect = ::saveAndReconnect,
            ),
            modifier = Modifier.imePadding(),
        )
    }

    validationError?.let { error ->
        SettingsAlertDialog(
            onDismissRequest = { validationError = null },
            title = "无效的服务器",
            confirmText = "确定",
            onConfirm = { validationError = null },
            text = { Text(error) },
        )
    }
}
