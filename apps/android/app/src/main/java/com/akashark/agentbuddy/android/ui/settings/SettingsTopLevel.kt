package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.DebugSettings
import com.akashark.agentbuddy.android.state.PetOverlayController
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.state.SshAuthMethod
import com.akashark.agentbuddy.android.state.SshCredentialStore
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.state.isPromptable
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.ConversationPrefs
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.discovery.SSHLoginDialog
import com.akashark.agentbuddy.android.ui.discovery.SshHostKeyChangePrompt
import com.akashark.agentbuddy.android.ui.discovery.SshHostKeyChangedDialog
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
            e.message ?: "SSH reconnect failed"
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

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Title
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("设置", color = AgentBuddyTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("完成", color = AgentBuddyTheme.accent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Support ──

        // ── Theme ──
        item { SectionHeader("主题") }
        item {
            NavRow(icon = Icons.Default.Palette, label = "外观", onClick = onOpenAppearance)
        }

        // ── Font ──
        item { SectionHeader("字体") }
        item {
            Column(
                Modifier.fillMaxWidth().background(AgentBuddyTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            ) {
                FontRow("Berkeley Mono", BerkeleyMono, AgentBuddyThemeManager.monoFontEnabled) { AgentBuddyThemeManager.applyFont(true) }
                HorizontalDivider(color = AgentBuddyTheme.divider)
                FontRow("System Default", FontFamily.Default, !AgentBuddyThemeManager.monoFontEnabled) { AgentBuddyThemeManager.applyFont(false) }
            }
        }

        // ── Conversation ──
        item { SectionHeader("对话") }
        item {
            SettingsRow(
                icon = { Text("⊟", color = AgentBuddyTheme.accent, fontSize = 16.sp) },
                label = "折叠回合", subtitle = "将之前的回合折叠为卡片",
                trailing = {
                    Switch(
                        checked = collapseTurns,
                        onCheckedChange = { ConversationPrefs.setCollapseTurns(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AgentBuddyTheme.accent),
                    )
                },
            )
        }

        // ── Pets ──
        item { SectionHeader("宠物") }
        item {
            SettingsRow(
                icon = { Icon(Icons.Default.Pets, null, tint = AgentBuddyTheme.accent, modifier = Modifier.size(18.dp)) },
                label = "唤醒宠物",
                subtitle = PetOverlayController.selectedPet?.displayName ?: "选择一个 Codex 宠物",
                trailing = {
                    Switch(
                        checked = PetOverlayController.visible,
                        onCheckedChange = { PetOverlayController.setVisible(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AgentBuddyTheme.accent),
                    )
                },
                onClick = onOpenPets,
            )
        }

        // ── Apps ──
        if (onOpenApps != null) {
            item { SectionHeader("应用") }
            item {
                NavRow(
                    icon = Icons.Default.Widgets,
                    label = "已保存的 App",
                    onClick = {
                        onDismiss()
                        onOpenApps()
                    },
                )
            }
        }

        // ── Experimental ──
        item { SectionHeader("实验性") }
        item {
            NavRow(icon = Icons.Default.Science, label = "实验性功能", onClick = onOpenExperimental)
        }

        // ── Debug ──
        if (DebugSettings.enabled) {
            item { SectionHeader("调试") }
            item {
                NavRow(icon = Icons.Default.Science, label = "调试设置", onClick = onOpenDebug)
            }
        }

        // ── Account ──
        item { SectionHeader("账户") }
        item {
            if (currentServer != null) {
                val accountStatus = when (val account = currentServer!!.account) {
                    is Account.Chatgpt -> account.email.ifEmpty { "ChatGPT 账户" }
                    is Account.ApiKey -> "OpenAI API 密钥"
                    null -> "未登录"
                }
                SettingsRow(
                    icon = { Text("@", color = AgentBuddyTheme.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                    label = currentServer!!.displayName,
                    subtitle = accountStatus,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = AgentBuddyTheme.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = { onOpenAccount(currentServer!!.serverId) },
                )
            } else {
                SettingsRow(label = "请先连接到服务器")
            }
        }

        // ── Servers ──
        item { SectionHeader("服务器") }
        val servers = snapshot?.servers ?: emptyList()
        if (servers.isEmpty()) {
            item { SettingsRow(label = "未连接服务器") }
        } else {
            items(servers, key = { it.serverId }) { server ->
                ServerSettingsRow(
                    server = server,
                    onRename = {
                        renameText = server.displayName
                        renameTarget = server
                    },
                    onEdit = {
                        editTarget = server
                    },
                    onRemove = {
                        scope.launch {
                            SavedServerStore.remove(context, server.serverId)
                            appModel.sshSessionStore.close(server.serverId)
                            appModel.serverBridge.disconnectServer(server.serverId)
                            appModel.refreshSnapshot()
                        }
                    },
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    renameTarget?.let { server ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名服务器") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    scope.launch {
                        SavedServerStore.rename(context, server.serverId, trimmed)
                        appModel.refreshSnapshot()
                    }
                    renameTarget = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
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
        AlertDialog(
            onDismissRequest = { sshReconnectError = null },
            title = { Text("SSH 重连失败") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { sshReconnectError = null }) {
                    Text("确定")
                }
            },
        )
    }

}

/** The guided SSH reconnect in flight, kept so a host-key refusal can be retried. */
private data class SshReconnectAttempt(
    val server: SavedServer,
    val credential: SavedSshCredential,
    val rememberCredentials: Boolean,
)
