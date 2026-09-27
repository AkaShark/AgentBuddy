package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.akashark.agentbuddy.android.state.PetOverlayController
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppPetSummary

// ═══════════════════════════════════════════════════════════════════════════════
// Pets Sub-Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun PetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val connectedServers = remember(snapshot) {
        snapshot?.servers.orEmpty().filter { it.isConnected }
    }
    var selectedServerId by remember(connectedServers) {
        mutableStateOf(
            PetOverlayController.selectedPet?.serverId?.takeIf { id ->
                connectedServers.any { it.serverId == id }
            }
                ?: snapshot?.activeThread?.serverId?.takeIf { id ->
                    connectedServers.any { it.serverId == id }
                }
                ?: connectedServers.firstOrNull()?.serverId
                ?: "",
        )
    }
    var pets by remember(selectedServerId) { mutableStateOf<List<AppPetSummary>>(emptyList()) }
    var loading by remember(selectedServerId) { mutableStateOf(false) }
    var error by remember(selectedServerId) { mutableStateOf<String?>(null) }
    val overlayPermissionGranted = PetOverlayController.canDrawOverlays(context)

    fun refresh() {
        if (selectedServerId.isBlank()) return
        scope.launch {
            loading = true
            error = null
            runCatching { appModel.client.listPets(selectedServerId) }
                .onSuccess { pets = it }
                .onFailure {
                    pets = emptyList()
                    error = it.message ?: "无法加载宠物。"
                }
            loading = false
        }
    }

    LaunchedEffect(selectedServerId) {
        refresh()
    }

    SettingsPage(
        title = "宠物",
        onBack = onBack,
        headerTrailing = {
            BuddyIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = "刷新宠物列表",
                onClick = { refresh() },
                enabled = selectedServerId.isNotBlank() && !loading,
                iconSize = BuddySize.iconLarge,
            )
        },
    ) {
        settingsSection("唤醒", key = "wake") {
            SettingsSwitchRow(
                title = "显示宠物",
                subtitle = PetOverlayController.selectedPet?.displayName ?: "未选择宠物",
                icon = Icons.Outlined.Pets,
                checked = PetOverlayController.visible,
                onCheckedChange = { PetOverlayController.setVisible(context, it) },
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                title = "悬浮在其他应用之上",
                subtitle = if (overlayPermissionGranted) "已授予悬浮窗权限" else "需要「显示在其他应用上层」权限",
                icon = Icons.Outlined.PictureInPictureAlt,
                checked = PetOverlayController.overlayEnabled,
                onCheckedChange = { enabled ->
                    PetOverlayController.setOverlayEnabled(context, enabled)
                    if (enabled && !overlayPermissionGranted) {
                        PetOverlayController.requestOverlayPermission(context)
                    }
                },
                onClick = if (!overlayPermissionGranted) {
                    { PetOverlayController.requestOverlayPermission(context) }
                } else {
                    null
                },
                onClickLabel = "授予悬浮窗权限",
            )
        }

        settingsSection("服务器", key = "servers") {
            if (connectedServers.isEmpty()) {
                SettingsNoteRow("请先连接到服务器", icon = Icons.Outlined.Info)
            } else {
                connectedServers.forEachIndexed { index, server ->
                    if (index > 0) SettingsRowDivider()
                    SettingsSelectRow(
                        title = settingsServerDisplayName(server),
                        subtitle = if (server.isLocal) "本地" else "远程",
                        icon = if (server.isLocal) Icons.Outlined.PhoneAndroid else Icons.Outlined.Dns,
                        selected = server.serverId == selectedServerId,
                        onClick = { selectedServerId = server.serverId },
                    )
                }
            }
        }

        settingsSection("宠物", key = "pets") {
            when {
                selectedServerId.isBlank() -> SettingsNoteRow("未选择服务器", icon = Icons.Outlined.Info)
                loading ->
                    SettingsRow(
                        title = "正在加载宠物",
                        titleColor = AgentBuddyTheme.textSecondary,
                        leading = { SettingsSpinner() },
                    )
                error != null ->
                    SettingsRow(
                        title = "无法加载宠物",
                        subtitle = error,
                        icon = Icons.Outlined.ErrorOutline,
                        iconTint = AgentBuddyTheme.danger,
                    )
                pets.isEmpty() ->
                    SettingsRow(
                        title = "未找到宠物",
                        subtitle = "~/.codex/pets 中没有 hatch-pet 包",
                        icon = Icons.Outlined.Info,
                    )
                else ->
                    pets.forEachIndexed { index, pet ->
                        if (index > 0) SettingsRowDivider(indentForIcon = false)
                        val selected = PetOverlayController.selectedPet?.serverId == selectedServerId &&
                            PetOverlayController.selectedPet?.id == pet.id
                        SettingsRow(
                            title = pet.displayName,
                            subtitle = pet.validationError ?: pet.description ?: pet.sourcePath,
                            subtitleColor = if (pet.validationError != null) AgentBuddyTheme.danger else AgentBuddyTheme.textSecondary,
                            titleWeight = if (selected) FontWeight.SemiBold else null,
                            enabled = pet.hasValidSpritesheet,
                            modifier = Modifier.selectable(
                                selected = selected,
                                enabled = pet.hasValidSpritesheet,
                                role = Role.RadioButton,
                                onClick = {
                                    scope.launch {
                                        PetOverlayController.selectPet(context, appModel, selectedServerId, pet)
                                    }
                                },
                            ),
                            trailing = {
                                if (PetOverlayController.isLoading && selected) {
                                    SettingsSpinner()
                                } else if (selected) {
                                    SettingsCheckmark()
                                }
                            },
                        )
                    }
            }
        }

        PetOverlayController.errorMessage?.let { message ->
            item(key = "petError") {
                BuddyBanner(
                    tone = BuddyBannerTone.DANGER,
                    message = "宠物加载失败：$message",
                    modifier = Modifier.padding(top = BuddySpacing.md),
                )
            }
        }
    }
}
