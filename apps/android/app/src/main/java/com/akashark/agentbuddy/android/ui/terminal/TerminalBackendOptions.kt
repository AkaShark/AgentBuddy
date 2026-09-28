package com.akashark.agentbuddy.android.ui.terminal

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.akashark.agentbuddy.android.state.AlleycatCredentialStore
import com.akashark.agentbuddy.android.state.AndroidProotBootstrap
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.state.SshAuthMethod
import com.akashark.agentbuddy.android.state.SshCredentialStore
import uniffi.codex_mobile_client.TerminalBackendKind
import uniffi.codex_mobile_client.TerminalSshAuth

internal data class TerminalBackendOption(
    val id: String,
    val title: String,
    val runningLabel: String,
    val icon: ImageVector,
    val alleycatNodeId: String? = null,
    val supportsResize: Boolean,
    val backend: TerminalBackendKind,
)

internal fun initialBackendId(
    options: List<TerminalBackendOption>,
    preferredAlleycatNodeId: String?,
): String? {
    val preferred = normalized(preferredAlleycatNodeId)
    return options.firstOrNull { it.alleycatNodeId == preferred }?.id
        ?: options.firstOrNull()?.id
}

internal fun loadBackendOptions(
    context: Context,
    cwd: String?,
    prootState: AndroidProotBootstrap.BootstrapState,
): List<TerminalBackendOption> {
    val options = mutableListOf<TerminalBackendOption>()
    if (prootState.status == AndroidProotBootstrap.Status.Ready) {
        options.add(
            TerminalBackendOption(
                id = "local-proot",
                title = "本地 Alpine",
                runningLabel = "本地 Alpine",
                icon = Icons.Outlined.PhoneIphone,
                supportsResize = true,
                backend = TerminalBackendKind.LocalProot(normalized(cwd)),
            ),
        )
    }
    val credentialStore = AlleycatCredentialStore(context.applicationContext)
    val sshCredentialStore = SshCredentialStore(context.applicationContext)
    val seenNodeIds = mutableSetOf<String>()
    val seenSshKeys = mutableSetOf<String>()
    SavedServerStore.remembered(context).forEach { saved ->
        val nodeId = normalized(saved.alleycatNodeId)
        if (nodeId != null && seenNodeIds.add(nodeId)) {
            val token = credentialStore.loadToken(nodeId)?.trim()?.takeIf { it.isNotEmpty() }
            if (token != null) {
                options.add(
                    TerminalBackendOption(
                        id = "alleycat-$nodeId",
                        title = saved.name.trim().ifEmpty { "远程 Shell" },
                        runningLabel = "远程 Shell",
                        icon = Icons.Outlined.Storage,
                        alleycatNodeId = nodeId,
                        supportsResize = true,
                        backend = TerminalBackendKind.RemoteAlleycat(
                            nodeId = nodeId,
                            token = token,
                            relay = normalized(saved.alleycatRelay),
                            shell = null,
                        ),
                    ),
                )
                return@forEach
            }
        }

        val host = saved.hostname.takeIf { it.isNotBlank() } ?: return@forEach
        val sshPort = (saved.sshPort ?: 22).toInt()
        val key = "${host.lowercase()}:$sshPort"
        if (!seenSshKeys.add(key)) return@forEach
        val credential = sshCredentialStore.load(host, sshPort) ?: return@forEach
        val auth = credential.toTerminalSshAuth() ?: return@forEach
        options.add(
            TerminalBackendOption(
                id = "ssh-$key",
                title = saved.name.trim().ifEmpty { "${credential.username}@$host" },
                runningLabel = "SSH Shell",
                icon = Icons.Outlined.Storage,
                supportsResize = true,
                backend = TerminalBackendKind.RemoteSsh(
                    host = host,
                    port = sshPort.toUShort(),
                    username = credential.username,
                    auth = auth,
                    shell = null,
                    acceptUnknownHost = false,
                    cwd = null,
                ),
            ),
        )
    }
    return options
}

private fun SavedSshCredential.toTerminalSshAuth(): TerminalSshAuth? = when (method) {
    SshAuthMethod.PASSWORD -> password
        ?.takeIf { it.isNotEmpty() }
        ?.let { TerminalSshAuth.Password(it) }
    SshAuthMethod.KEY -> privateKey
        ?.takeIf { it.isNotEmpty() }
        ?.let { TerminalSshAuth.PrivateKey(it, passphrase) }
}

private fun normalized(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

internal fun terminalEmptyMessage(
    prootState: AndroidProotBootstrap.BootstrapState,
    selectedBackend: TerminalBackendOption?,
): String {
    if (selectedBackend != null) return ""
    return when (prootState.status) {
        AndroidProotBootstrap.Status.Pending,
        AndroidProotBootstrap.Status.Bootstrapping -> "正在准备本地 Alpine...\n"
        AndroidProotBootstrap.Status.PtraceDenied ->
            "本地 Alpine 不可用，因为当前 Android 环境禁用了 ptrace。\n配对 Alleycat 主机后仍可使用远程 Shell。\n"
        AndroidProotBootstrap.Status.MissingArtifact ->
            "本地 Alpine 不可用，因为未内置 proot 或 Alpine rootfs。\n配对 Alleycat 主机后仍可使用远程 Shell。\n"
        AndroidProotBootstrap.Status.Failed ->
            "本地 Alpine 启动失败：${prootState.message ?: "未知错误"}\n配对 Alleycat 主机后仍可使用远程 Shell。\n"
        AndroidProotBootstrap.Status.Ready ->
            "配对一台 Alleycat 主机以打开远程 Shell。\n"
    }
}
