package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import java.net.URI

@Composable
internal fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSubmit: (ManualEntryAction) -> Unit,
) {
    var mode by remember { mutableStateOf(ManualConnectionMode.SSH) }
    var codexUrl by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var wakeMac by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加服务器") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ManualConnectionMode.CODEX,
                        onClick = { mode = ManualConnectionMode.CODEX },
                        label = { Text(ManualConnectionMode.CODEX.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AgentBuddyTheme.accent.copy(alpha = 0.18f),
                            selectedLabelColor = AgentBuddyTheme.textPrimary,
                        ),
                    )
                    FilterChip(
                        selected = mode == ManualConnectionMode.SSH,
                        onClick = { mode = ManualConnectionMode.SSH },
                        label = { Text(ManualConnectionMode.SSH.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AgentBuddyTheme.accent.copy(alpha = 0.18f),
                            selectedLabelColor = AgentBuddyTheme.textPrimary,
                        ),
                    )
                }

                when (mode) {
                    ManualConnectionMode.CODEX -> {
                        OutlinedTextField(
                            value = codexUrl,
                            onValueChange = {
                                codexUrl = it
                                errorMessage = null
                            },
                            label = { Text("Codex URL") },
                            placeholder = { Text("ws://host:8390 或 host:8390") },
                            singleLine = true,
                        )
                        Text(
                            text = "建议使用 SSH 流程 —— 它会在远程绑定 127.0.0.1 并转发端口。" +
                                "若手动运行，请自行绑定环回地址并建立隧道：" +
                                "codex app-server --listen ws://127.0.0.1:8390",
                            color = AgentBuddyTheme.textMuted,
                            fontSize = 11.sp,
                        )
                    }

                    ManualConnectionMode.SSH -> {
                        OutlinedTextField(
                            value = host,
                            onValueChange = {
                                host = it
                                errorMessage = null
                            },
                            label = { Text("SSH 主机") },
                            placeholder = { Text("主机名或 IP") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = sshPort,
                            onValueChange = {
                                sshPort = it
                                errorMessage = null
                            },
                            label = { Text("SSH 端口") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = wakeMac,
                            onValueChange = {
                                wakeMac = it
                                errorMessage = null
                            },
                            label = { Text("唤醒 MAC（可选）") },
                            placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                            singleLine = true,
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = AgentBuddyTheme.danger,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    errorMessage = when (val action = buildManualEntryAction(
                        mode,
                        codexUrl,
                        host,
                        sshPort,
                        wakeMac,
                    )) {
                        is ManualEntryBuild.Action -> {
                            onSubmit(action.action)
                            null
                        }

                        is ManualEntryBuild.Error -> action.message
                    }
                },
            ) {
                Text(mode.primaryButtonTitle)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

internal sealed interface ManualEntryAction {
    data class Connect(val server: SavedServer) : ManualEntryAction
    data class ContinueWithSsh(val server: SavedServer) : ManualEntryAction
}

private sealed interface ManualEntryBuild {
    data class Action(val action: ManualEntryAction) : ManualEntryBuild
    data class Error(val message: String) : ManualEntryBuild
}

private enum class ManualConnectionMode(
    val label: String,
    val primaryButtonTitle: String,
) {
    CODEX("Codex", "连接"),
    SSH("SSH", "继续 SSH 登录"),
}

private fun buildManualEntryAction(
    mode: ManualConnectionMode,
    codexUrl: String,
    host: String,
    sshPort: String,
    wakeMac: String,
): ManualEntryBuild = when (mode) {
    ManualConnectionMode.CODEX -> buildManualCodexEntry(codexUrl)
    ManualConnectionMode.SSH -> buildManualSshEntry(host, sshPort, wakeMac)
}

private fun buildManualCodexEntry(rawInput: String): ManualEntryBuild {
    val raw = rawInput.trim()
    if (raw.isEmpty()) {
        return ManualEntryBuild.Error("请输入 ws:// URL 或 host:port。")
    }

    runCatching { URI(raw) }
        .getOrNull()
        ?.let { uri ->
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.takeIf { it.isNotBlank() }
            if ((scheme == "ws" || scheme == "wss") && host != null) {
                val port = uri.port.takeIf { it > 0 }
                return ManualEntryBuild.Action(
                    ManualEntryAction.Connect(
                        SavedServer(
                            id = "manual-url-$raw",
                            name = host,
                            hostname = host,
                            port = port ?: 0,
                            codexPorts = port?.let(::listOf) ?: emptyList(),
                            source = "manual",
                            hasCodexServer = true,
                            preferredConnectionMode = "directCodex",
                            preferredCodexPort = port,
                            websocketURL = raw,
                        ).normalizedForPersistence(),
                    ),
                )
            }
        }

    val (host, port) = parseBareHostAndPort(raw) ?: return ManualEntryBuild.Error("请输入 ws:// URL 或 host:port。")
    if (host.isBlank()) {
        return ManualEntryBuild.Error("请输入主机名或 IP 地址。")
    }

    return ManualEntryBuild.Action(
        ManualEntryAction.Connect(
            SavedServer(
                id = "manual-$host:$port",
                name = host,
                hostname = host,
                port = port,
                codexPorts = listOf(port),
                source = "manual",
                hasCodexServer = true,
                preferredConnectionMode = "directCodex",
                preferredCodexPort = port,
            ).normalizedForPersistence(),
        ),
    )
}

private fun buildManualSshEntry(
    hostInput: String,
    sshPortInput: String,
    wakeMacInput: String,
): ManualEntryBuild {
    val host = hostInput.trim()
    if (host.isEmpty()) {
        return ManualEntryBuild.Error("请输入主机名或 IP 地址。")
    }

    val sshPort = sshPortInput.trim().toIntOrNull()
    if (sshPort == null || sshPort !in 1..65535) {
        return ManualEntryBuild.Error("SSH 端口必须是有效数字。")
    }

    val wakeInput = wakeMacInput.trim()
    val normalizedWakeMac = SavedServer.normalizeWakeMac(wakeInput)
    if (wakeInput.isNotEmpty() && normalizedWakeMac == null) {
        return ManualEntryBuild.Error("唤醒 MAC 地址格式应为 aa:bb:cc:dd:ee:ff。")
    }

    return ManualEntryBuild.Action(
        ManualEntryAction.ContinueWithSsh(
            SavedServer(
                id = "manual-ssh-$host:$sshPort",
                name = host,
                hostname = host,
                port = sshPort,
                sshPort = sshPort,
                source = "manual",
                hasCodexServer = false,
                wakeMAC = normalizedWakeMac,
                preferredConnectionMode = "ssh",
            ).normalizedForPersistence(),
        ),
    )
}

private fun parseBareHostAndPort(raw: String): Pair<String, Int>? {
    if (raw.startsWith("[")) {
        val closing = raw.indexOf(']')
        if (closing > 1) {
            val host = raw.substring(1, closing)
            val portPart = raw.substring(closing + 1)
            val port = when {
                portPart.isEmpty() -> 8390
                portPart.startsWith(":") -> portPart.drop(1).toIntOrNull() ?: return null
                else -> return null
            }
            return host to port
        }
    }

    val colonCount = raw.count { it == ':' }
    if (colonCount == 1) {
        val index = raw.lastIndexOf(':')
        val host = raw.substring(0, index)
        val port = raw.substring(index + 1).toIntOrNull() ?: return null
        return host to port
    }

    return raw to 8390
}
