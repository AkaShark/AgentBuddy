package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing

/** Which part of a saved server the edit form may change. */
internal enum class ServerEditKind {
    /** Paired (alleycat / ssh-bridge): only the name. */
    PAIRED,

    /** This device's local runtime: only the name. */
    LOCAL,

    /** ChatGPT-connected computer: only the name. */
    SLINGSHOT,

    /** Manually added host: connection mode and address fields. */
    EDITABLE,
}

/** Form values of the server edit sheet, as plain data. */
internal data class ServerEditFormState(
    val kind: ServerEditKind,
    val displayName: String,
    val mode: ServerConnectionMode,
    val host: String,
    val sshPort: String,
    val wakeMac: String,
    val codexPort: String,
    val websocketUrl: String,
    val isLocal: Boolean,
    val showReconnect: Boolean,
    val isReconnecting: Boolean,
)

internal class ServerEditFormActions(
    val onDismiss: () -> Unit,
    val onDisplayNameChange: (String) -> Unit,
    val onModeChange: (ServerConnectionMode) -> Unit,
    val onHostChange: (String) -> Unit,
    val onSshPortChange: (String) -> Unit,
    val onWakeMacChange: (String) -> Unit,
    val onCodexPortChange: (String) -> Unit,
    val onWebsocketUrlChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onSaveAndReconnect: () -> Unit,
)

/** Connection modes a manually added host can switch between. */
private val EditableModes =
    listOf(ServerConnectionMode.SSH, ServerConnectionMode.DIRECT_CODEX, ServerConnectionMode.WEBSOCKET)

/** Server edit form (name, connection, save / reconnect); stateless. */
@Composable
internal fun ServerEditContent(
    state: ServerEditFormState,
    actions: ServerEditFormActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsPageHeader(title = "编辑服务器", onDone = actions.onDismiss)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = settingsGutter())
                        .padding(bottom = BuddySpacing.xxl),
            ) {
                SettingsSectionHeader("名称")
                SettingsTextField(
                    value = state.displayName,
                    onValueChange = actions.onDisplayNameChange,
                    label = "服务器名称",
                )

                SettingsSectionHeader(state.mode.formHeader)
                when (state.kind) {
                    ServerEditKind.PAIRED ->
                        ServerEditNote("此已配对服务器使用已保存的配对元数据。可在此处编辑其显示名称，或移除后重新添加以更改配对。")
                    ServerEditKind.LOCAL -> ServerEditNote("本设备的本地运行时由系统自动管理。")
                    ServerEditKind.SLINGSHOT ->
                        ServerEditNote("这台已连接的电脑来自 ChatGPT，使用你登录的账户。可在此处编辑其显示名称，或移除后重新添加以更换电脑。")
                    ServerEditKind.EDITABLE -> ServerEditConnectionFields(state, actions)
                }

                Spacer(Modifier.height(BuddySpacing.xl))
                Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
                    BuddyButton(
                        text = "保存",
                        onClick = actions.onSave,
                        enabled = !state.isReconnecting,
                    )
                    if (state.showReconnect) {
                        BuddyButton(
                            text = if (state.isLocal) "保存并重启" else "保存并重连",
                            onClick = actions.onSaveAndReconnect,
                            kind = BuddyButtonKind.SECONDARY,
                            isLoading = state.isReconnecting,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerEditNote(text: String) {
    SettingsGroup { SettingsNoteRow(text, icon = Icons.Outlined.Info) }
}

@Composable
private fun ServerEditConnectionFields(
    state: ServerEditFormState,
    actions: ServerEditFormActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
        SettingsSegmentedControl(
            options = EditableModes.map { it.label },
            selectedIndex = EditableModes.indexOf(state.mode),
            onSelect = { actions.onModeChange(EditableModes[it]) },
        )
        val uriKeyboard = KeyboardOptions(keyboardType = KeyboardType.Uri)
        val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
        when (state.mode) {
            ServerConnectionMode.SSH -> {
                SettingsTextField(state.host, actions.onHostChange, label = "主机名或 IP", keyboardOptions = uriKeyboard)
                SettingsTextField(state.sshPort, actions.onSshPortChange, label = "SSH 端口", keyboardOptions = numberKeyboard)
                SettingsTextField(
                    state.wakeMac,
                    actions.onWakeMacChange,
                    label = "唤醒 MAC（可选）",
                    placeholder = "aa:bb:cc:dd:ee:ff",
                )
            }
            ServerConnectionMode.DIRECT_CODEX -> {
                SettingsTextField(state.host, actions.onHostChange, label = "主机名或 IP", keyboardOptions = uriKeyboard)
                SettingsTextField(state.codexPort, actions.onCodexPortChange, label = "Codex 端口", keyboardOptions = numberKeyboard)
            }
            ServerConnectionMode.WEBSOCKET -> {
                SettingsTextField(
                    state.websocketUrl,
                    actions.onWebsocketUrlChange,
                    label = "ws://主机:端口 或 wss://...",
                    keyboardOptions = uriKeyboard,
                )
                SettingsFooter(
                    text = "尽量优先使用 SSH。如果你手动运行 codex，请绑定 loopback 并自行建立隧道；除非你清楚自己在做什么，否则不要将其直接暴露到互联网。",
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = AgentBuddyTheme.warning,
                )
            }
            else -> Unit
        }
    }
}
