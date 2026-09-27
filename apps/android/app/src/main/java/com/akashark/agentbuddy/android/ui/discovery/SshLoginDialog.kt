package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.state.SshAuthMethod
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSshHostKeyMismatch
import uniffi.codex_mobile_client.AppSshHostKeyMismatchKind

/**
 * An SSH connect refused because the server's host key no longer matches the
 * key pinned on this device, or because that saved key could not be read
 * (typed Rust [AppSshHostKeyMismatch]). Carries what is needed to retry the
 * same login after the user trusts the new key or forgets the unreadable one.
 */
internal data class SshHostKeyChangePrompt(
    val server: SavedServer,
    val credential: SavedSshCredential,
    val rememberCredentials: Boolean,
    val mismatch: AppSshHostKeyMismatch,
)

/**
 * Confirmation for a changed SSH host key. "信任新密钥" pins exactly the
 * displayed fingerprint before calling [onConfirm], so the caller's retry
 * only succeeds if the server still presents that key (otherwise it prompts
 * again). When the saved key could not be read
 * ([AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE]), "忘记已保存的主机密钥"
 * removes it before [onConfirm], so the retry treats the host as new.
 */
@Composable
internal fun SshHostKeyChangedDialog(
    mismatch: AppSshHostKeyMismatch,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val port = mismatch.port.toInt()
    val hostDisplay = if (port == 22) mismatch.host else "${mismatch.host}:$port"
    val savedKeyUnreadable = mismatch.kind == AppSshHostKeyMismatchKind.TRUST_STORE_UNAVAILABLE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (savedKeyUnreadable) "无法读取已保存的 SSH 主机密钥" else "SSH 主机密钥已变更") },
        text = {
            if (savedKeyUnreadable) {
                Text(
                    "本设备为 $hostDisplay 保存的 SSH 主机密钥无法读取，因此连接已被拒绝。\n\n" +
                        "忘记已保存的密钥即可重新连接；服务器当前的密钥将作为新密钥保存。\n\n" +
                        "服务器指纹：\n${mismatch.fingerprint}",
                )
            } else {
                Text(
                    "$hostDisplay 的 SSH 主机密钥与本设备保存的不一致。服务器重装后会出现这种情况，" +
                        "但也可能意味着有人正在拦截连接。\n\n新指纹：\n${mismatch.fingerprint}\n\n" +
                        "仅在你预期到此变更时才信任新密钥。",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (savedKeyUnreadable) {
                        appModel.sshTrustStore.unpin(mismatch.host, mismatch.port)
                    } else {
                        appModel.sshTrustStore.pin(mismatch.host, mismatch.port, mismatch.fingerprint)
                    }
                    onConfirm()
                },
            ) {
                Text(
                    if (savedKeyUnreadable) "忘记已保存的主机密钥" else "信任新密钥",
                    color = AgentBuddyTheme.danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun SSHLoginDialog(
    server: SavedServer,
    initialCredential: SavedSshCredential?,
    onDismiss: () -> Unit,
    onConnect: suspend (SavedSshCredential, Boolean) -> String?,
) {
    val scope = rememberCoroutineScope()
    var username by remember(server.id) { mutableStateOf(initialCredential?.username ?: "") }
    var authMethod by remember(server.id) { mutableStateOf(initialCredential?.method ?: SshAuthMethod.PASSWORD) }
    var password by remember(server.id) { mutableStateOf(initialCredential?.password ?: "") }
    var isPasswordVisible by remember(server.id) { mutableStateOf(false) }
    var privateKey by remember(server.id) { mutableStateOf(initialCredential?.privateKey ?: "") }
    var passphrase by remember(server.id) { mutableStateOf(initialCredential?.passphrase ?: "") }
    var rememberCredentials by remember(server.id) { mutableStateOf(initialCredential != null) }
    var unlockMacosKeychain by remember(server.id) {
        mutableStateOf(initialCredential?.unlockMacosKeychain ?: false)
    }
    var isConnecting by remember(server.id) { mutableStateOf(false) }
    var errorMessage by remember(server.id) { mutableStateOf<String?>(null) }
    val hostDisplay = if (server.resolvedSshPort == 22) {
        server.hostname
    } else {
        "${server.hostname}:${server.resolvedSshPort}"
    }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("SSH 登录") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "${server.name.ifBlank { server.hostname }}\n$hostDisplay",
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { authMethod = SshAuthMethod.PASSWORD },
                        enabled = !isConnecting,
                    ) {
                        Text(if (authMethod == SshAuthMethod.PASSWORD) "密码 *" else "密码")
                    }
                    TextButton(
                        onClick = {
                            authMethod = SshAuthMethod.KEY
                            isPasswordVisible = false
                        },
                        enabled = !isConnecting,
                    ) {
                        Text(if (authMethod == SshAuthMethod.KEY) "SSH 密钥 *" else "SSH 密钥")
                    }
                }
                if (authMethod == SshAuthMethod.PASSWORD) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                enabled = !isConnecting,
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (isPasswordVisible) {
                                        "隐藏密码"
                                    } else {
                                        "显示密码"
                                    },
                                )
                            }
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Switch(
                            checked = unlockMacosKeychain,
                            onCheckedChange = { unlockMacosKeychain = it },
                            enabled = !isConnecting,
                        )
                        Column {
                            Text(
                                text = "解锁钥匙串 (macOS)",
                                color = AgentBuddyTheme.textPrimary,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = "在无头引导启动期间使用你的 SSH/登录密码。gh CLI 认证等工具需要此项。",
                                color = AgentBuddyTheme.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("私钥") },
                        minLines = 5,
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("口令（可选）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Switch(
                        checked = rememberCredentials,
                        onCheckedChange = { rememberCredentials = it },
                        enabled = !isConnecting,
                    )
                    Text(
                        text = "在此设备上记住凭据",
                        color = AgentBuddyTheme.textSecondary,
                        fontSize = 12.sp,
                    )
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
                enabled = !isConnecting && username.isNotBlank() && when (authMethod) {
                    SshAuthMethod.PASSWORD -> password.isNotBlank()
                    SshAuthMethod.KEY -> privateKey.isNotBlank()
                },
                onClick = {
                    val credential = when (authMethod) {
                        SshAuthMethod.PASSWORD -> SavedSshCredential(
                            username = username.trim(),
                            method = SshAuthMethod.PASSWORD,
                            password = password,
                            unlockMacosKeychain = unlockMacosKeychain,
                        )

                        SshAuthMethod.KEY -> SavedSshCredential(
                            username = username.trim(),
                            method = SshAuthMethod.KEY,
                            privateKey = privateKey,
                            passphrase = passphrase.ifBlank { null },
                            unlockMacosKeychain = false,
                        )
                    }
                    scope.launch {
                        isConnecting = true
                        errorMessage = onConnect(credential, rememberCredentials)
                        isConnecting = false
                    }
                },
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AgentBuddyTheme.accent,
                    )
                } else {
                    Text("连接")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConnecting) {
                Text("取消")
            }
        },
    )
}
