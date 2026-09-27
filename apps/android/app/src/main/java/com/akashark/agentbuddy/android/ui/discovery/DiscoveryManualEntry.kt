package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import java.net.URI

/** 「SSH 或地址」 sheet: manual Codex URL or SSH host entry with validation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualEntrySheet(
    onDismiss: () -> Unit,
    onSubmit: (ManualEntryAction) -> Unit,
) {
    var mode by remember { mutableStateOf(ManualConnectionMode.SSH) }
    var codexUrl by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var wakeMac by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BuddyBottomSheet(onDismissRequest = onDismiss) {
        ManualEntryContent(
            mode = mode,
            onModeChange = { mode = it },
            codexUrl = codexUrl,
            onCodexUrlChange = {
                codexUrl = it
                errorMessage = null
            },
            host = host,
            onHostChange = {
                host = it
                errorMessage = null
            },
            sshPort = sshPort,
            onSshPortChange = {
                sshPort = it
                errorMessage = null
            },
            wakeMac = wakeMac,
            onWakeMacChange = {
                wakeMac = it
                errorMessage = null
            },
            errorMessage = errorMessage,
            onCancel = onDismiss,
            onSubmit = {
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
        )
    }
}

/** Stateless body of [ManualEntrySheet] (also rendered by the gallery). */
@Composable
internal fun ManualEntryContent(
    mode: ManualConnectionMode,
    onModeChange: (ManualConnectionMode) -> Unit,
    codexUrl: String,
    onCodexUrlChange: (String) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    sshPort: String,
    onSshPortChange: (String) -> Unit,
    wakeMac: String,
    onWakeMacChange: (String) -> Unit,
    errorMessage: String?,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DiscoverySheetScaffold(
        modifier = modifier,
        header = {
            DiscoverySheetHeader(title = "SSH 或地址", actionTitle = "取消", onAction = onCancel)
        },
        bottomBar = {
            BuddyButton(text = mode.primaryButtonTitle, onClick = onSubmit)
        },
    ) {
        DiscoveryFormSection(title = "连接方式") {
            DiscoverySegmentedPicker(
                options = ManualConnectionMode.entries.map { it to it.label },
                selection = mode,
                onSelect = onModeChange,
            )
        }

        when (mode) {
            ManualConnectionMode.CODEX -> {
                DiscoveryFormSection(
                    title = "Codex 服务器",
                    footer = "建议使用 SSH 流程 —— 它会在远程绑定 127.0.0.1 并转发端口。" +
                        "若手动运行，请自行绑定环回地址并建立隧道：" +
                        "codex app-server --listen ws://127.0.0.1:8390",
                ) {
                    MintTextField(
                        value = codexUrl,
                        onValueChange = onCodexUrlChange,
                        placeholder = "ws://host:8390 或 host:8390",
                        label = "Codex URL",
                        monospaced = true,
                        isError = errorMessage != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    )
                }
            }

            ManualConnectionMode.SSH -> {
                DiscoveryFormSection(title = "SSH 主机") {
                    MintTextField(
                        value = host,
                        onValueChange = onHostChange,
                        placeholder = "主机名或 IP",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                DiscoveryFormSection(title = "SSH 端口") {
                    MintTextField(
                        value = sshPort,
                        onValueChange = onSshPortChange,
                        placeholder = "22",
                        label = "SSH 端口",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                DiscoveryFormSection(
                    title = "唤醒 MAC（可选）",
                    footer = "填写后，连接前会先向这台电脑发送网络唤醒包。",
                ) {
                    MintTextField(
                        value = wakeMac,
                        onValueChange = onWakeMacChange,
                        placeholder = "aa:bb:cc:dd:ee:ff",
                        label = "唤醒 MAC（可选）",
                        monospaced = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    )
                }
            }
        }

        if (errorMessage != null) {
            BuddyBanner(tone = BuddyBannerTone.DANGER, message = errorMessage)
        }
    }
}

internal sealed interface ManualEntryAction {
    data class Connect(val server: SavedServer) : ManualEntryAction
    data class ContinueWithSsh(val server: SavedServer) : ManualEntryAction
}

private sealed interface ManualEntryBuild {
    data class Action(val action: ManualEntryAction) : ManualEntryBuild
    data class Error(val message: String) : ManualEntryBuild
}

internal enum class ManualConnectionMode(
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
