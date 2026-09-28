package com.akashark.agentbuddy.android.ui.settings

import android.content.Context
import android.net.Uri
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.ChatGPTOAuth
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.toRecord
import uniffi.codex_mobile_client.AppServerSnapshot

internal enum class ServerConnectionMode(val label: String, val formHeader: String) {
    LOCAL("本地", "本地运行时"),
    SSH("SSH", "SSH 主机"),
    DIRECT_CODEX("Codex", "Codex 服务器"),
    WEBSOCKET("WebSocket", "Codex URL"),
    SLINGSHOT("Slingshot", "Slingshot"),
}

internal fun isSettingsSlingshotUrl(rawUrl: String): Boolean =
    runCatching { Uri.parse(rawUrl).scheme?.equals("slingshot", ignoreCase = true) == true }
        .getOrDefault(false)

private suspend fun loadSettingsSlingshotTokens(context: Context) =
    ChatGPTOAuth.requireStoredOrRefreshedTokens(
        context,
        "使用 Slingshot 连接前，请先用 ChatGPT 登录。",
    )

/**
 * Validates the [ServerEditSheet] form and builds the [SavedServer] to save.
 * Reports the first problem through [onValidationError] and returns null.
 */
internal fun serverEditValidateAndBuild(
    server: AppServerSnapshot,
    originalSaved: SavedServer?,
    displayName: String,
    connectionMode: ServerConnectionMode,
    host: String,
    codexPort: String,
    websocketURL: String,
    sshPort: String,
    wakeMAC: String,
    onValidationError: (String) -> Unit,
): SavedServer? {
    val name = displayName.trim()
    if (name.isEmpty()) {
        onValidationError("服务器名称不能为空。")
        return null
    }

    if (originalSaved?.alleycatNodeId != null || originalSaved?.alleycatAgentWire == "ssh-bridge") {
        // Paired server — only name is editable
        return originalSaved.copy(name = name)
    }

    return when (connectionMode) {
        ServerConnectionMode.LOCAL -> {
            SavedServer(
                id = server.serverId,
                name = name,
                hostname = "127.0.0.1",
                port = 0,
                codexPorts = emptyList(),
                sshPort = null,
                source = "local",
                hasCodexServer = true,
                wakeMAC = null,
                preferredConnectionMode = null,
                preferredCodexPort = null,
                sshPortForwardingEnabled = null,
                websocketURL = null,
                rememberedByUser = true,
            )
        }
        ServerConnectionMode.SSH -> {
            val resolvedHost = host.trim()
            if (resolvedHost.isEmpty()) {
                onValidationError("主机不能为空。")
                return null
            }
            val resolvedSSHPort = sshPort.trim().toIntOrNull()
            if (resolvedSSHPort == null || resolvedSSHPort !in 1..65535) {
                onValidationError("SSH 端口必须是有效数字。")
                return null
            }
            val wakeInput = wakeMAC.trim()
            val resolvedWakeMAC = SavedServer.normalizeWakeMac(wakeInput)
            if (wakeInput.isNotEmpty() && resolvedWakeMAC == null) {
                onValidationError("唤醒 MAC 地址格式应为 aa:bb:cc:dd:ee:ff。")
                return null
            }
            SavedServer(
                id = server.serverId,
                name = name,
                hostname = resolvedHost,
                port = 0,
                codexPorts = emptyList(),
                sshPort = resolvedSSHPort,
                source = "manual",
                hasCodexServer = false,
                wakeMAC = resolvedWakeMAC,
                preferredConnectionMode = "ssh",
                preferredCodexPort = null,
                sshPortForwardingEnabled = null,
                websocketURL = null,
                rememberedByUser = true,
            )
        }
        ServerConnectionMode.DIRECT_CODEX -> {
            val resolvedHost = host.trim()
            if (resolvedHost.isEmpty()) {
                onValidationError("主机不能为空。")
                return null
            }
            val resolvedCodexPort = codexPort.trim().toIntOrNull()
            if (resolvedCodexPort == null || resolvedCodexPort !in 1..65535) {
                onValidationError("Codex 端口必须是有效数字。")
                return null
            }
            SavedServer(
                id = server.serverId,
                name = name,
                hostname = resolvedHost,
                port = resolvedCodexPort,
                codexPorts = listOf(resolvedCodexPort),
                sshPort = null,
                source = "manual",
                hasCodexServer = true,
                wakeMAC = null,
                preferredConnectionMode = "directCodex",
                preferredCodexPort = resolvedCodexPort,
                sshPortForwardingEnabled = null,
                websocketURL = null,
                rememberedByUser = true,
            )
        }
        ServerConnectionMode.WEBSOCKET -> {
            val rawURL = websocketURL.trim()
            if (!rawURL.startsWith("ws://", ignoreCase = true) && !rawURL.startsWith("wss://", ignoreCase = true)) {
                onValidationError("请输入有效的 ws:// 或 wss:// URL。")
                return null
            }
            val uri = runCatching { java.net.URI(rawURL) }.getOrNull()
            if (uri == null || uri.host.isNullOrEmpty()) {
                onValidationError("请输入有效的 ws:// 或 wss:// URL。")
                return null
            }
            val resolvedPort = if (uri.port != -1) uri.port else null
            SavedServer(
                id = server.serverId,
                name = name,
                hostname = uri.host,
                port = resolvedPort ?: 0,
                codexPorts = if (resolvedPort != null) listOf(resolvedPort) else emptyList(),
                sshPort = null,
                source = "manual",
                hasCodexServer = true,
                wakeMAC = null,
                preferredConnectionMode = "directCodex",
                preferredCodexPort = resolvedPort,
                sshPortForwardingEnabled = null,
                websocketURL = rawURL,
                rememberedByUser = true,
            )
        }
        ServerConnectionMode.SLINGSHOT -> {
            val saved = originalSaved ?: run {
                onValidationError("请先移除并重新添加这台已连接的电脑。")
                return null
            }
            saved.copy(
                name = name,
                rememberedByUser = true,
            )
        }
    }
}

internal fun serverEditPersist(context: Context, appModel: AppModel, saved: SavedServer) {
    val existing = SavedServerStore.load(context).toMutableList()
    existing.removeAll { it.id == saved.id }
    existing.add(saved)
    SavedServerStore.save(context, existing)
    appModel.reconnectController.setMultiClankerAndQuicEnabled(true)
    appModel.reconnectController.syncSavedServers(
        existing.filter { it.rememberedByUser }.map { it.toRecord(context) }
    )
    appModel.store.renameServer(saved.id, saved.name)
}

private suspend fun serverEditReconnect(context: Context, appModel: AppModel, serverId: String) {
    val servers = SavedServerStore.load(context).map { it.toRecord(context) }
    appModel.reconnectController.setMultiClankerAndQuicEnabled(true)
    appModel.reconnectController.syncSavedServers(servers)
    val result = appModel.reconnectController.reconnectServer(serverId)
    if (result.needsLocalAuthRestore) {
        appModel.restoreStoredLocalAuthState(result.serverId)
        runCatching { appModel.refreshSessions(listOf(result.serverId)) }
    }
    appModel.refreshSnapshot()
}

internal suspend fun serverEditConnectSlingshotSaved(
    context: Context,
    appModel: AppModel,
    saved: SavedServer,
    stepUpToken: String,
) {
    val websocketURL = saved.websocketURL?.takeIf(::isSettingsSlingshotUrl)
        ?: throw IllegalStateException("Saved server is not a Slingshot connection.")

    val tokens = loadSettingsSlingshotTokens(context)
    appModel.serverBridge.connectRemoteSlingshotUrlServer(
        saved.id,
        saved.name,
        websocketURL,
        tokens.accessToken,
        tokens.accountId,
        stepUpToken,
    )
    appModel.refreshSnapshot()
}

internal suspend fun serverEditReconnectSaved(context: Context, appModel: AppModel, saved: SavedServer) {
    if (saved.websocketURL?.let(::isSettingsSlingshotUrl) != true) {
        serverEditReconnect(context, appModel, saved.id)
        return
    }

    serverEditConnectSlingshotSaved(context, appModel, saved, "")
}
