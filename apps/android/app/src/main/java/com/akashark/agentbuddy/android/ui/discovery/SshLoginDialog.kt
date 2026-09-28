package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedSshCredential
import com.akashark.agentbuddy.android.state.SshAuthMethod
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import kotlinx.coroutines.launch

/**
 * 「SSH 登录」 (presented as a Mint bottom sheet). [onConnect] returns an inline error, or null once the
 * login moved on (connected, agent picker or host-key prompt). The sheet
 * cannot be dismissed while a connect is in flight.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val connecting by rememberUpdatedState(isConnecting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !connecting },
    )
    val canConnect = !isConnecting && username.isNotBlank() && when (authMethod) {
        SshAuthMethod.PASSWORD -> password.isNotBlank()
        SshAuthMethod.KEY -> privateKey.isNotBlank()
    }

    fun connect() {
        if (!canConnect) return
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
    }

    BuddyBottomSheet(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        sheetState = sheetState,
    ) {
        SshLoginContent(
            serverName = server.name.ifBlank { server.hostname },
            hostDisplay = hostDisplay,
            username = username,
            onUsernameChange = { username = it },
            authMethod = authMethod,
            onAuthMethodChange = { method ->
                authMethod = method
                if (method == SshAuthMethod.KEY) isPasswordVisible = false
            },
            password = password,
            onPasswordChange = { password = it },
            isPasswordVisible = isPasswordVisible,
            onTogglePasswordVisible = { isPasswordVisible = !isPasswordVisible },
            privateKey = privateKey,
            onPrivateKeyChange = { privateKey = it },
            passphrase = passphrase,
            onPassphraseChange = { passphrase = it },
            unlockMacosKeychain = unlockMacosKeychain,
            onUnlockMacosKeychainChange = { unlockMacosKeychain = it },
            rememberCredentials = rememberCredentials,
            onRememberCredentialsChange = { rememberCredentials = it },
            isConnecting = isConnecting,
            canConnect = canConnect,
            errorMessage = errorMessage,
            onCancel = onDismiss,
            onConnect = ::connect,
        )
    }
}

/** Stateless body of [SSHLoginDialog] (also rendered by the gallery). */
@Composable
internal fun SshLoginContent(
    serverName: String,
    hostDisplay: String,
    username: String,
    onUsernameChange: (String) -> Unit,
    authMethod: SshAuthMethod,
    onAuthMethodChange: (SshAuthMethod) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    privateKey: String,
    onPrivateKeyChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    unlockMacosKeychain: Boolean,
    onUnlockMacosKeychainChange: (Boolean) -> Unit,
    rememberCredentials: Boolean,
    onRememberCredentialsChange: (Boolean) -> Unit,
    isConnecting: Boolean,
    canConnect: Boolean,
    errorMessage: String?,
    onCancel: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DiscoverySheetScaffold(
        modifier = modifier,
        header = {
            DiscoverySheetHeader(
                title = "SSH 登录",
                actionTitle = "取消",
                onAction = onCancel,
                actionEnabled = !isConnecting,
            )
        },
        bottomBar = {
            BuddyButton(
                text = "连接",
                onClick = onConnect,
                enabled = canConnect,
                isLoading = isConnecting,
            )
        },
    ) {
        DiscoveryHostSummary(icon = Icons.Outlined.Terminal, name = serverName, address = hostDisplay)

        DiscoveryFormSection(title = "用户名") {
            MintTextField(
                value = username,
                onValueChange = onUsernameChange,
                placeholder = "用户名",
                enabled = !isConnecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            )
        }

        DiscoveryFormSection(title = "身份验证") {
            DiscoverySegmentedPicker(
                options = listOf(SshAuthMethod.PASSWORD to "密码", SshAuthMethod.KEY to "SSH 密钥"),
                selection = authMethod,
                onSelect = onAuthMethodChange,
                enabled = !isConnecting,
            )
            if (authMethod == SshAuthMethod.PASSWORD) {
                MintTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = "密码",
                    enabled = !isConnecting,
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailing = {
                        BuddyIconButton(
                            icon = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                            onClick = onTogglePasswordVisible,
                            enabled = !isConnecting,
                        )
                    },
                )
                DiscoveryToggleRow(
                    title = "解锁钥匙串 (macOS)",
                    detail = "在无头引导启动期间使用你的 SSH/登录密码。gh CLI 认证等工具需要此项。",
                    checked = unlockMacosKeychain,
                    onCheckedChange = onUnlockMacosKeychainChange,
                    enabled = !isConnecting,
                )
            } else {
                MintTextField(
                    value = privateKey,
                    onValueChange = onPrivateKeyChange,
                    placeholder = "在此粘贴私钥...",
                    label = "私钥",
                    singleLine = false,
                    minLines = 5,
                    monospaced = true,
                    enabled = !isConnecting,
                )
                MintTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    placeholder = "密钥口令（可选）",
                    enabled = !isConnecting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )
            }
        }

        DiscoveryFormSection(title = "已保存凭据") {
            DiscoveryToggleRow(
                title = "在此设备上记住凭据",
                checked = rememberCredentials,
                onCheckedChange = onRememberCredentialsChange,
                enabled = !isConnecting,
            )
        }

        if (errorMessage != null) {
            BuddyBanner(
                tone = BuddyBannerTone.DANGER,
                message = "$errorMessage\n请检查主机地址、用户名和凭据，然后再点「连接」。",
            )
        }
    }
}
