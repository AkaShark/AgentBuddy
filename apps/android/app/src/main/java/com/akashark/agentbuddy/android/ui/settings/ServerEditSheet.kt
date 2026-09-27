package com.akashark.agentbuddy.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.auth.ChatGPTOAuthActivity
import com.akashark.agentbuddy.android.state.ChatGPTOAuth
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AgentBuddyTheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("编辑服务器", color = AgentBuddyTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("完成", color = AgentBuddyTheme.accent) }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionHeader("名称")
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("服务器名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 14.sp),
                    )
                }

                item {
                    SectionHeader(connectionMode.formHeader)

                    if (originalSaved?.alleycatNodeId != null || originalSaved?.alleycatAgentWire == "ssh-bridge") {
                        Text(
                            "此已配对服务器使用已保存的配对元数据。可在此处编辑其显示名称，或移除后重新添加以更改配对。",
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else if (server.isLocal) {
                        Text(
                            "本设备的本地运行时由系统自动管理。",
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else if (connectionMode == ServerConnectionMode.SLINGSHOT) {
                        Text(
                            "这台已连接的电脑来自 ChatGPT，使用你登录的账户。可在此处编辑其显示名称，或移除后重新添加以更换电脑。",
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else {
                        // Mode selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgentBuddyTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val modes = listOf(
                                ServerConnectionMode.SSH,
                                ServerConnectionMode.DIRECT_CODEX,
                                ServerConnectionMode.WEBSOCKET,
                            )
                            modes.forEach { mode ->
                                val selected = mode == connectionMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AgentBuddyTheme.accent else Color.Transparent)
                                        .clickable { connectionMode = mode }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        mode.label,
                                        color = if (selected) AgentBuddyTheme.onAccentStrong else AgentBuddyTheme.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        when (connectionMode) {
                            ServerConnectionMode.SSH -> {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("主机名或 IP") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = sshPort,
                                    onValueChange = { sshPort = it },
                                    label = { Text("SSH 端口") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = wakeMAC,
                                    onValueChange = { wakeMAC = it },
                                    label = { Text("唤醒 MAC（可选）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            ServerConnectionMode.DIRECT_CODEX -> {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("主机名或 IP") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = codexPort,
                                    onValueChange = { codexPort = it },
                                    label = { Text("Codex 端口") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            ServerConnectionMode.WEBSOCKET -> {
                                OutlinedTextField(
                                    value = websocketURL,
                                    onValueChange = { websocketURL = it },
                                    label = { Text("ws://主机:端口 或 wss://...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            else -> Unit
                        }

                        if (connectionMode == ServerConnectionMode.WEBSOCKET) {
                            Text(
                                "尽量优先使用 SSH。如果你手动运行 codex，请绑定 loopback 并自行建立隧道；除非你清楚自己在做什么，否则不要将其直接暴露到互联网。",
                                color = AgentBuddyTheme.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                item {
                    if (isReconnecting) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = AgentBuddyTheme.accent, strokeWidth = 2.dp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    validationError = null
                                    val saved = validateAndBuild()
                                    if (saved != null) {
                                        persist(saved)
                                        onSave()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AgentBuddyTheme.accent),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("保存", color = AgentBuddyTheme.onAccentStrong)
                            }
                            if (server.isLocal || (originalSaved?.alleycatNodeId == null && originalSaved?.alleycatAgentWire != "ssh-bridge")) {
                                Button(
                                    onClick = {
                                        validationError = null
                                        val saved = validateAndBuild()
                                        if (saved != null) {
                                            persist(saved)
                                            // SSH mode requires interactive credentials, mirroring iOS:
                                            // hand off to the parent which will open SSHLoginDialog.
                                            if (connectionMode == ServerConnectionMode.SSH && !server.isLocal) {
                                                onTriggerSshReconnect(saved)
                                                return@Button
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
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AgentBuddyTheme.accentStrong),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (server.isLocal) "保存并重启" else "保存并重连",
                                        color = AgentBuddyTheme.background,
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    validationError?.let { error ->
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = { Text("无效的服务器") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { validationError = null }) {
                    Text("确定")
                }
            },
        )
    }
}
