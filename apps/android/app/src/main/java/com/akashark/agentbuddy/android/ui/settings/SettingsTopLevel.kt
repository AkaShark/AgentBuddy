package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.DebugSettings
import com.akashark.agentbuddy.android.state.PetOverlayController
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.state.SshAuthMethod
import com.akashark.agentbuddy.android.state.SshCredentialStore
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.state.isPromptable
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.ConversationPrefs
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.discovery.SSHLoginDialog
import com.akashark.agentbuddy.android.ui.discovery.SshHostKeyChangePrompt
import com.akashark.agentbuddy.android.ui.discovery.SshHostKeyChangedDialog
import com.akashark.agentbuddy.android.ui.home.DashboardZoomPrefs
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.AppServerSnapshot

@Composable
internal fun SettingsTopLevel(
    onDismiss: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenPets: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAccount: (serverId: String) -> Unit,
    onOpenApps: (() -> Unit)?,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val collapseTurns = ConversationPrefs.areTurnsCollapsed
    val dashboardZoomLevel by DashboardZoomPrefs.currentLevel.collectAsState()
    var renameTarget by remember { mutableStateOf<AppServerSnapshot?>(null) }
    var renameText by remember { mutableStateOf("") }

    val currentServer = remember(snapshot) {
        val activeServerId = snapshot?.activeThread?.serverId
        snapshot?.servers?.firstOrNull { it.serverId == activeServerId }
            ?: snapshot?.servers?.firstOrNull { it.isLocal }
            ?: snapshot?.servers?.firstOrNull()
    }

    var editTarget by remember { mutableStateOf<AppServerSnapshot?>(null) }
    var sshReconnectTarget by remember { mutableStateOf<SavedServer?>(null) }
    val sshCredentialStore = remember(context) { SshCredentialStore(context.applicationContext) }
    var pendingSshReconnect by remember { mutableStateOf<SshReconnectAttempt?>(null) }
    var sshHostKeyChange by remember { mutableStateOf<SshHostKeyChangePrompt?>(null) }
    var sshReconnectError by remember { mutableStateOf<String?>(null) }

    /** Guided SSH reconnect from [SSHLoginDialog]; returns an inline error or null. */
    suspend fun reconnectOverSsh(
        saved: SavedServer,
        credential: SavedSshCredential,
        rememberCredentials: Boolean,
    ): String? =
        try {
            if (rememberCredentials) {
                sshCredentialStore.save(saved.hostname, saved.resolvedSshPort, credential)
            } else {
                sshCredentialStore.delete(saved.hostname, saved.resolvedSshPort)
            }

            appModel.serverBridge.disconnectServer(saved.id)
            // Publish the removal, then watch before starting: the guided
            // connect can fail before `startRemoteOverSshConnect` returns, and
            // the watcher must never see the previous attempt's state.
            appModel.refreshSnapshot()
            pendingSshReconnect = SshReconnectAttempt(saved, credential, rememberCredentials)

            when (credential.method) {
                SshAuthMethod.PASSWORD -> appModel.serverBridge.startRemoteOverSshConnect(
                    serverId = saved.id,
                    displayName = saved.name,
                    host = saved.hostname,
                    port = saved.resolvedSshPort.toUShort(),
                    username = credential.username,
                    password = credential.password,
                    privateKeyPem = null,
                    passphrase = null,
                    unlockMacosKeychain = credential.unlockMacosKeychain,
                    acceptUnknownHost = true,
                    workingDir = null,
                )
                SshAuthMethod.KEY -> appModel.serverBridge.startRemoteOverSshConnect(
                    serverId = saved.id,
                    displayName = saved.name,
                    host = saved.hostname,
                    port = saved.resolvedSshPort.toUShort(),
                    username = credential.username,
                    password = null,
                    privateKeyPem = credential.privateKey,
                    passphrase = credential.passphrase,
                    unlockMacosKeychain = false,
                    acceptUnknownHost = true,
                    workingDir = null,
                )
            }
            appModel.refreshSnapshot()
            sshReconnectTarget = null
            null
        } catch (e: Exception) {
            pendingSshReconnect = null
            LLog.e("SettingsSheet", "SSH reconnect failed: ${e.message}", e)
            e.message ?: "SSH 重连失败"
        }

    // The guided reconnect reports failures through the server snapshot; a
    // changed host key there asks the user before trusting the new key (an
    // unreadable saved key offers to forget it), any other failure is shown.
    LaunchedEffect(snapshot, pendingSshReconnect) {
        val attempt = pendingSshReconnect ?: return@LaunchedEffect
        val serverSnapshot = snapshot?.servers?.firstOrNull { it.serverId == attempt.server.id }
            ?: return@LaunchedEffect
        if (serverSnapshot.isConnected) {
            pendingSshReconnect = null
        } else if (serverSnapshot.health == AppServerHealth.DISCONNECTED) {
            val progress = serverSnapshot.connectionProgress ?: return@LaunchedEffect
            val message = progress.terminalMessage ?: return@LaunchedEffect
            pendingSshReconnect = null
            val mismatch = progress.hostKeyMismatch?.takeIf { it.isPromptable }
            if (mismatch == null) {
                sshReconnectError = message
                return@LaunchedEffect
            }
            sshHostKeyChange = SshHostKeyChangePrompt(
                server = attempt.server,
                credential = attempt.credential,
                rememberCredentials = attempt.rememberCredentials,
                mismatch = mismatch,
            )
        }
    }

    val serverItems = remember(snapshot) { snapshot?.servers.orEmpty().map { it.toSettingsServerItem() } }
    val accountItem = currentServer?.let { server ->
        SettingsAccountItem(
            serverId = server.serverId,
            serverName = settingsServerDisplayName(server),
            status = when (val account = server.account) {
                is Account.Chatgpt -> account.email.ifEmpty { "ChatGPT 账户" }
                is Account.ApiKey -> "OpenAI API 密钥"
                null -> "未登录"
            },
        )
    }
    fun serverById(id: String) = snapshot?.servers?.firstOrNull { it.serverId == id }

    SettingsTopLevelContent(
        state = SettingsTopLevelState(
            monoFontEnabled = AgentBuddyThemeManager.monoFontEnabled,
            collapseTurns = collapseTurns,
            showHomeTaskDetails = dashboardZoomLevel >= HomeTaskDetailsLevel,
            petVisible = PetOverlayController.visible,
            petSubtitle = PetOverlayController.selectedPet?.displayName ?: "选择一个 Codex 宠物",
            showApps = onOpenApps != null,
            showDebug = DebugSettings.enabled,
            account = accountItem,
            servers = serverItems,
        ),
        actions = SettingsTopLevelActions(
            onDone = onDismiss,
            onOpenAppearance = onOpenAppearance,
            onSelectFont = AgentBuddyThemeManager::applyFont,
            onCollapseTurnsChange = { ConversationPrefs.setCollapseTurns(context, it) },
            onShowHomeTaskDetailsChange = { on ->
                DashboardZoomPrefs.setLevel(context, if (on) HomeTaskDetailsLevel else DashboardZoomPrefs.DEFAULT_LEVEL)
            },
            onPetVisibleChange = { PetOverlayController.setVisible(context, it) },
            onOpenPets = onOpenPets,
            onOpenApps = {
                onDismiss()
                onOpenApps?.invoke()
            },
            onOpenExperimental = onOpenExperimental,
            onOpenDebug = onOpenDebug,
            onOpenAccount = onOpenAccount,
            onEditServer = { id -> editTarget = serverById(id) },
            onRenameServer = { id ->
                serverById(id)?.let { server ->
                    renameText = server.displayName
                    renameTarget = server
                }
            },
            onRemoveServer = { id ->
                scope.launch {
                    SavedServerStore.remove(context, id)
                    appModel.sshSessionStore.close(id)
                    appModel.serverBridge.disconnectServer(id)
                    appModel.refreshSnapshot()
                }
            },
        ),
    )

    renameTarget?.let { server ->
        SettingsAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = "重命名服务器",
            confirmText = "保存",
            onConfirm = {
                val trimmed = renameText.trim()
                if (trimmed.isNotEmpty()) {
                    scope.launch {
                        SavedServerStore.rename(context, server.serverId, trimmed)
                        appModel.refreshSnapshot()
                    }
                    renameTarget = null
                }
            },
            dismissText = "取消",
            text = {
                SettingsTextField(value = renameText, onValueChange = { renameText = it }, label = "名称")
            },
        )
    }

    editTarget?.let { server ->
        ServerEditSheet(
            server = server,
            onDismiss = { editTarget = null },
            onSave = { editTarget = null },
            onTriggerSshReconnect = { saved ->
                editTarget = null
                sshReconnectTarget = saved
            },
        )
    }

    sshReconnectTarget?.let { saved ->
        SSHLoginDialog(
            server = saved,
            initialCredential = sshCredentialStore.load(saved.hostname, saved.resolvedSshPort),
            onDismiss = { sshReconnectTarget = null },
            onConnect = { credential, rememberCredentials ->
                reconnectOverSsh(saved, credential, rememberCredentials)
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
                    reconnectOverSsh(prompt.server, prompt.credential, prompt.rememberCredentials)
                        ?.let { sshReconnectError = it }
                }
            },
        )
    }

    sshReconnectError?.let { error ->
        SettingsAlertDialog(
            onDismissRequest = { sshReconnectError = null },
            title = "SSH 重连失败",
            confirmText = "确定",
            onConfirm = { sshReconnectError = null },
            text = { Text(error) },
        )
    }
}

/** Home card density at which task cards show progress, model and activity (iOS: homeZoomLevel >= 3). */
private const val HomeTaskDetailsLevel = 3

/** The guided SSH reconnect in flight, kept so a host-key refusal can be retried. */
private data class SshReconnectAttempt(
    val server: SavedServer,
    val credential: SavedSshCredential,
    val rememberCredentials: Boolean,
)
