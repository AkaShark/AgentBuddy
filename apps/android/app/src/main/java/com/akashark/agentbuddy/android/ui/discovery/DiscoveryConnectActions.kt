package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.util.LLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppSnapshotRecord

/**
 * Server connect actions of [DiscoveryScreen]. A new instance is built on every
 * composition, like the local functions it replaces, so it always sees the
 * current callbacks; screen state is shared through the passed states.
 */
internal class DiscoveryConnectActions(
    private val appModel: AppModel,
    private val context: Context,
    private val logTag: String,
    private val onDismiss: () -> Unit,
    snapshotState: State<AppSnapshotRecord?>,
    savedServersState: MutableState<List<SavedServer>>,
    wakingServerIdState: MutableState<String?>,
    connectionChoiceServerState: MutableState<SavedServer?>,
    sshServerState: MutableState<SavedServer?>,
    connectErrorState: MutableState<String?>,
) {
    private val snapshot by snapshotState
    private var savedServers by savedServersState
    private var wakingServerId by wakingServerIdState
    private var connectionChoiceServer by connectionChoiceServerState
    private var sshServer by sshServerState
    private var connectError by connectErrorState

    suspend fun reloadSavedServers() {
        savedServers = SavedServerStore.load(context)
    }

    private suspend fun prepareServerForSelection(entry: SavedServer): SavedServer {
        if (entry.source == "local" || entry.websocketURL != null) {
            return entry
        }

        wakingServerId = entry.id
        try {
            return when (
                val wakeResult = waitForWakeSignal(
                    host = entry.hostname,
                    preferredCodexPort = entry.directCodexPort ?: entry.availableDirectCodexPorts.firstOrNull(),
                    preferredSshPort = entry.sshPort ?: if (entry.canConnectViaSsh) entry.resolvedSshPort else null,
                    timeoutMillis = if (entry.hasCodexServer) 12_000L else 18_000L,
                    wakeMac = entry.wakeMAC,
                )
            ) {
                is WakeSignalResult.Codex -> entry.copy(
                    port = wakeResult.port,
                    codexPorts = listOf(wakeResult.port) + entry.availableDirectCodexPorts.filter { it != wakeResult.port },
                    hasCodexServer = true,
                    preferredConnectionMode = entry.preferredConnectionMode,
                    preferredCodexPort = wakeResult.port,
                ).normalizedForPersistence()

                is WakeSignalResult.Ssh -> entry.copy(
                    port = wakeResult.port,
                    sshPort = wakeResult.port,
                    hasCodexServer = false,
                    preferredConnectionMode = "ssh",
                    preferredCodexPort = null,
                ).normalizedForPersistence()

                WakeSignalResult.None -> entry
            }
        } finally {
            wakingServerId = null
        }
    }

    private suspend fun connectPreparedRemoteUrl(prepared: SavedServer) {
        val websocketURL = prepared.websocketURL ?: return
        if (isSlingshotUrl(websocketURL)) {
            val tokens = loadSlingshotTokens(context)
            appModel.serverBridge.connectRemoteSlingshotUrlServer(
                prepared.id,
                prepared.name,
                websocketURL,
                tokens.accessToken,
                tokens.accountId,
                "",
            )
        } else {
            appModel.serverBridge.connectRemoteUrlServer(
                prepared.id,
                prepared.name,
                websocketURL,
            )
        }
    }

    suspend fun connectSelectedServer(entry: SavedServer) {
        if (wakingServerId != null && wakingServerId != entry.id) {
            return
        }

        try {
            val connected = connectedSnapshot(entry, snapshot?.servers ?: emptyList())
            if (connected?.isConnected == true) {
                LLog.t(logTag, "server already connected", fields = mapOf("serverId" to entry.id))
                onDismiss()
                return
            }

            val prepared = prepareServerForSelection(entry)
            when {
                prepared.source == "local" -> {
                    appModel.serverBridge.connectLocalServer(
                        prepared.id,
                        prepared.name,
                        prepared.hostname,
                        prepared.port.toUShort(),
                    )
                    appModel.restoreStoredLocalAuthState(prepared.id)
                    SavedServerStore.remember(context, prepared.normalizedForPersistence())
                    reloadSavedServers()
                    appModel.refreshSnapshot()
                    onDismiss()
                }

                prepared.websocketURL != null -> {
                    connectPreparedRemoteUrl(prepared)
                    SavedServerStore.remember(context, prepared.normalizedForPersistence())
                    reloadSavedServers()
                    appModel.refreshSnapshot()
                    onDismiss()
                }

                prepared.requiresConnectionChoice -> {
                    connectionChoiceServer = prepared
                }

                prepared.prefersSshConnection || (!prepared.hasCodexServer && prepared.canConnectViaSsh) -> {
                    sshServer = prepared.withPreferredConnection("ssh")
                }

                prepared.directCodexPort != null -> {
                    appModel.serverBridge.connectRemoteServer(
                        prepared.id,
                        prepared.name,
                        prepared.hostname,
                        prepared.directCodexPort!!.toUShort(),
                    )
                    SavedServerStore.remember(
                        context,
                        prepared.withPreferredConnection("directCodex", prepared.directCodexPort),
                    )
                    reloadSavedServers()
                    appModel.refreshSnapshot()
                    onDismiss()
                }

                else -> {
                    connectError = "唤醒尝试后服务器未响应。请在 Mac 上启用「唤醒以供网络访问」。"
                }
            }
        } catch (e: Exception) {
            LLog.e(
                logTag,
                "server connect failed",
                e,
                fields = mapOf(
                    "serverId" to entry.id,
                    "host" to entry.hostname,
                    "preferredConnectionMode" to entry.preferredConnectionMode,
                ),
            )
            connectError = e.message ?: "无法连接。"
        }
    }

    /** Direct Codex connect picked in [DiscoveryConnectionChoiceDialog]. */
    suspend fun connectDirectCodexPort(server: SavedServer, port: Int) {
        try {
            appModel.serverBridge.connectRemoteServer(
                server.id,
                server.name,
                server.hostname,
                port.toUShort(),
            )
            SavedServerStore.remember(
                context,
                server.withPreferredConnection("directCodex", port),
            )
            reloadSavedServers()
            appModel.refreshSnapshot()
            onDismiss()
        } catch (e: Exception) {
            LLog.e(
                logTag,
                "direct codex connect failed",
                e,
                fields = mapOf(
                    "serverId" to server.id,
                    "host" to server.hostname,
                    "codexPort" to port,
                    "os" to server.os,
                ),
            )
            connectError = e.message ?: "无法连接。"
        }
    }
}

private fun connectedSnapshot(
    entry: SavedServer,
    servers: List<AppServerSnapshot>,
): AppServerSnapshot? = servers.firstOrNull { it.serverId == entry.id }
    ?: servers.firstOrNull { it.host.lowercase().trim().trimStart('[').trimEnd(']') == entry.deduplicationKey }

private fun isSlingshotUrl(rawUrl: String): Boolean =
    runCatching { Uri.parse(rawUrl).scheme?.equals("slingshot", ignoreCase = true) == true }
        .getOrDefault(false)

private sealed interface WakeSignalResult {
    data class Codex(val port: Int) : WakeSignalResult
    data class Ssh(val port: Int) : WakeSignalResult
    data object None : WakeSignalResult
}

private suspend fun waitForWakeSignal(
    host: String,
    preferredCodexPort: Int?,
    preferredSshPort: Int?,
    timeoutMillis: Long,
    wakeMac: String?,
): WakeSignalResult = withContext(Dispatchers.IO) {
    val codexPorts = orderedCodexPorts(preferredCodexPort)
    val sshPorts = orderedSshPorts(preferredSshPort)
    val deadline = System.currentTimeMillis() + maxOf(timeoutMillis, 500L)
    var lastWakePacketAt = 0L

    while (System.currentTimeMillis() < deadline) {
        val now = System.currentTimeMillis()
        if (!wakeMac.isNullOrBlank() && now - lastWakePacketAt >= 2_000L) {
            sendWakeMagicPacket(wakeMac, host)
            lastWakePacketAt = now
        }

        for (port in codexPorts) {
            if (isPortOpen(host, port, 700)) {
                return@withContext WakeSignalResult.Codex(port)
            }
        }

        for (port in sshPorts) {
            if (isPortOpen(host, port, 700)) {
                return@withContext WakeSignalResult.Ssh(port)
            }
        }

        delay(350)
    }

    WakeSignalResult.None
}

private fun orderedCodexPorts(preferred: Int?): List<Int> = buildList {
    preferred?.let(::add)
    addAll(listOf(8390, 9234, 4222))
}.filter { it in 1..65535 }.distinct()

private fun orderedSshPorts(preferred: Int?): List<Int> = buildList {
    preferred?.let(::add)
    add(22)
}.filter { it in 1..65535 }.distinct()

private fun sendWakeMagicPacket(wakeMac: String, hostHint: String) {
    val mac = SavedServer.normalizeWakeMac(wakeMac) ?: return
    val macBytes = mac.split(':').mapNotNull { it.toIntOrNull(16)?.toByte() }
    if (macBytes.size != 6) {
        return
    }

    val packet = ByteArray(6 + 16 * macBytes.size)
    repeat(6) { packet[it] = 0xFF.toByte() }
    for (index in 0 until 16) {
        macBytes.forEachIndexed { byteIndex, value ->
            packet[6 + index * macBytes.size + byteIndex] = value
        }
    }

    wakeBroadcastTargets(hostHint).forEach { target ->
        sendBroadcastUdp(packet, target, 9)
        sendBroadcastUdp(packet, target, 7)
    }
}

private fun wakeBroadcastTargets(host: String): Set<String> {
    val targets = linkedSetOf("255.255.255.255")
    val ipv4Parts = host.split('.')
    if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() != null }) {
        targets += "${ipv4Parts[0]}.${ipv4Parts[1]}.${ipv4Parts[2]}.255"
    }
    return targets
}

private fun sendBroadcastUdp(packet: ByteArray, host: String, port: Int) {
    runCatching {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val address = InetAddress.getByName(host)
            socket.send(DatagramPacket(packet, packet.size, address, port))
        }
    }
}

private fun isPortOpen(host: String, port: Int, timeoutMillis: Int): Boolean =
    runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMillis)
            true
        }
    }.getOrDefault(false)
