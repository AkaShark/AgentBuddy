package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.state.SshAuthMethod
import com.akashark.agentbuddy.android.state.SshCredentialStore
import com.akashark.agentbuddy.android.state.promptableSshHostKey
import com.akashark.agentbuddy.android.util.LLog
import java.io.File
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import uniffi.codex_mobile_client.AppSshSessionResult
import uniffi.codex_mobile_client.SshBridgeTransport
import androidx.compose.runtime.MutableState
import com.akashark.agentbuddy.android.state.AppModel

/** The in-flight guided SSH connect, kept so a host-key refusal can offer a retry. */
internal data class GuidedSshAttempt(
    val server: SavedServer,
    val credential: SavedSshCredential,
)

/**
 * SSH login actions of [DiscoveryScreen]: the guided Codex-over-SSH connect and
 * the SSH-bridge agent picker. Built on every composition, like
 * [DiscoveryConnectActions].
 */
internal class SshLoginConnectActions(
    private val appModel: AppModel,
    private val context: Context,
    private val logTag: String,
    private val sshCredentialStore: SshCredentialStore,
    private val reloadSavedServers: suspend () -> Unit,
    sshServerState: MutableState<SavedServer?>,
    sshAgentContextState: MutableState<SshBridgeAgentContext?>,
    pendingAutoNavigateServerIdState: MutableState<String?>,
    sshHostKeyChangeState: MutableState<SshHostKeyChangePrompt?>,
    guidedSshAttemptState: MutableState<GuidedSshAttempt?>,
) {
    private var sshServer by sshServerState
    private var sshAgentContext by sshAgentContextState
    private var pendingAutoNavigateServerId by pendingAutoNavigateServerIdState
    private var sshHostKeyChange by sshHostKeyChangeState
    private var guidedSshAttempt by guidedSshAttemptState

    private suspend fun openSshSession(server: SavedServer, credential: SavedSshCredential): AppSshSessionResult =
        when (credential.method) {
            SshAuthMethod.PASSWORD -> appModel.ssh.sshOpenSession(
                host = server.hostname,
                port = server.resolvedSshPort.toUShort(),
                username = credential.username,
                password = credential.password,
                privateKeyPem = null,
                passphrase = null,
                unlockMacosKeychain = credential.unlockMacosKeychain,
                acceptUnknownHost = true,
            )

            SshAuthMethod.KEY -> appModel.ssh.sshOpenSession(
                host = server.hostname,
                port = server.resolvedSshPort.toUShort(),
                username = credential.username,
                password = null,
                privateKeyPem = credential.privateKey,
                passphrase = credential.passphrase,
                unlockMacosKeychain = false,
                acceptUnknownHost = true,
            )
        }

    private suspend fun startGuidedSshConnect(server: SavedServer, credential: SavedSshCredential) {
        guidedSshAttempt = GuidedSshAttempt(server, credential)
        when (credential.method) {
            SshAuthMethod.PASSWORD -> {
                appModel.serverBridge.startRemoteOverSshConnect(
                    serverId = server.id,
                    displayName = server.name,
                    host = server.hostname,
                    port = server.resolvedSshPort.toUShort(),
                    username = credential.username,
                    password = credential.password,
                    privateKeyPem = null,
                    passphrase = null,
                    unlockMacosKeychain = credential.unlockMacosKeychain,
                    acceptUnknownHost = true,
                    workingDir = null,
                )
            }

            SshAuthMethod.KEY -> {
                appModel.serverBridge.startRemoteOverSshConnect(
                    serverId = server.id,
                    displayName = server.name,
                    host = server.hostname,
                    port = server.resolvedSshPort.toUShort(),
                    username = credential.username,
                    password = null,
                    privateKeyPem = credential.privateKey,
                    passphrase = credential.passphrase,
                    unlockMacosKeychain = false,
                    acceptUnknownHost = true,
                    workingDir = null,
                )
            }
        }
    }

    /**
     * SSH login from [SSHLoginDialog]: opens a session, probes SSH-bridge agents
     * and falls back to the guided Codex connect. Returns an inline error, or
     * null when the dialog is done (connected, agent picker, or host-key prompt).
     */
    suspend fun connectViaSshLogin(
        server: SavedServer,
        credential: SavedSshCredential,
        rememberCredentials: Boolean,
    ): String? =
        try {
            LLog.t(
                logTag,
                "starting SSH connect",
                fields = mapOf(
                    "serverId" to server.id,
                    "host" to server.hostname,
                    "sshPort" to server.resolvedSshPort,
                    "authMethod" to credential.method.name,
                    "os" to server.os,
                ),
            )
            if (rememberCredentials) {
                sshCredentialStore.save(server.hostname, server.resolvedSshPort, credential)
            } else {
                sshCredentialStore.delete(server.hostname, server.resolvedSshPort)
            }

            val session = openSshSession(server, credential)
            val availability = appModel.ssh.sshProbeRemoteAgents(session.sessionId)
            val bridgeAgents = availableSshBridgeKinds(availability)
            if (bridgeAgents.isNotEmpty()) {
                sshAgentContext = SshBridgeAgentContext(
                    server = server,
                    sessionId = session.sessionId,
                    host = session.normalizedHost,
                    availability = availability,
                    credential = credential,
                )
                sshServer = null
                null
            } else {
                appModel.ssh.sshClose(session.sessionId)
                LLog.t(
                    logTag,
                    "no SSH bridge agents available; falling back to Codex SSH",
                    fields = mapOf(
                        "serverId" to server.id,
                        "host" to server.hostname,
                    ),
                )
                startGuidedSshConnect(server, credential)
                SavedServerStore.remember(
                    context,
                    server.withPreferredConnection("ssh"),
                )
                reloadSavedServers()
                appModel.refreshSnapshot()
                pendingAutoNavigateServerId = server.id
                LLog.t(
                    logTag,
                    "guided SSH bootstrap started",
                    fields = mapOf(
                        "serverId" to server.id,
                        "host" to server.hostname,
                        "sshPort" to server.resolvedSshPort,
                    ),
                )
                sshServer = null
                null
            }
        } catch (e: Exception) {
            LLog.e(
                logTag,
                "guided SSH connect failed",
                e,
                fields = mapOf(
                    "serverId" to server.id,
                    "host" to server.hostname,
                    "sshPort" to server.resolvedSshPort,
                    "authMethod" to credential.method.name,
                    "os" to server.os,
                ),
            )
            val mismatch = promptableSshHostKey(e)
            if (mismatch != null) {
                sshServer = null
                sshHostKeyChange = SshHostKeyChangePrompt(
                    server = server,
                    credential = credential,
                    rememberCredentials = rememberCredentials,
                    mismatch = mismatch,
                )
                null
            } else {
                e.message ?: "无法通过 SSH 连接。"
            }
        }

    /** [SSHAgentPickerDialog] dismissed: closes the probed SSH session. */
    suspend fun dismissAgentPicker(agentContext: SshBridgeAgentContext) {
        runCatching { appModel.ssh.sshClose(agentContext.sessionId) }
        sshAgentContext = null
    }

    /** [SSHAgentPickerDialog] "使用 Codex SSH": falls back to the guided Codex connect. */
    suspend fun useCodexFromAgentPicker(agentContext: SshBridgeAgentContext) {
        runCatching { appModel.ssh.sshClose(agentContext.sessionId) }
        startGuidedSshConnect(agentContext.server, agentContext.credential)
        SavedServerStore.remember(
            context,
            agentContext.server.withPreferredConnection("ssh"),
        )
        reloadSavedServers()
        appModel.refreshSnapshot()
        pendingAutoNavigateServerId = agentContext.server.id
        sshAgentContext = null
    }

    /** Connects the agents picked in [SSHAgentPickerDialog]; returns an inline error or null. */
    suspend fun connectAgentPickerSelection(
        agentContext: SshBridgeAgentContext,
        selectedKinds: List<AgentRuntimeKind>,
    ): String? =
        try {
            val result = appModel.ssh.sshConnectBridgeSession(
                sessionId = agentContext.sessionId,
                serverId = "ssh-bridge:${agentContext.host}",
                displayName = agentContext.server.name,
                host = agentContext.host,
                stateRoot = sshBridgeStateRoot(context, agentContext.host),
                runtimeKinds = selectedKinds,
                transport = SshBridgeTransport.EPHEMERAL,
            )
            val server = agentContext.server.copy(
                id = result.serverId,
                hostname = agentContext.host,
                port = 0,
                codexPorts = emptyList(),
                source = "ssh",
                hasCodexServer = true,
                preferredConnectionMode = "ssh",
            )
            appModel.sshSessionStore.record(result.serverId, agentContext.sessionId)
            SavedServerStore.remember(context, server)
            reloadSavedServers()
            appModel.refreshSnapshot()
            pendingAutoNavigateServerId = result.serverId
            sshAgentContext = null
            null
        } catch (e: Exception) {
            LLog.e(
                logTag,
                "SSH bridge connect failed",
                e,
                fields = mapOf(
                    "serverId" to agentContext.server.id,
                    "host" to agentContext.host,
                ),
            )
            e.message ?: "无法连接 SSH 桥接智能体。"
        }
}

private fun sshBridgeStateRoot(context: Context, host: String): String {
    val safeHost = host.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val dir = File(File(context.filesDir, "alleycat-bridges"), safeHost)
    dir.mkdirs()
    return dir.absolutePath
}
