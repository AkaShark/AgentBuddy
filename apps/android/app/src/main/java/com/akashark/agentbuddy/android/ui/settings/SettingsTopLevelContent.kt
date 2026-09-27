package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.akashark.agentbuddy.android.ui.BerkeleyMono

/** Account row of the top-level list (the current or local server). */
internal data class SettingsAccountItem(
    val serverId: String,
    val serverName: String,
    val status: String,
)

/** Everything the top-level settings list shows, as plain data. */
internal data class SettingsTopLevelState(
    val monoFontEnabled: Boolean,
    val collapseTurns: Boolean,
    val showHomeTaskDetails: Boolean,
    val petVisible: Boolean,
    val petSubtitle: String,
    val showApps: Boolean,
    val showDebug: Boolean,
    val account: SettingsAccountItem?,
    val servers: List<SettingsServerItem>,
)

internal class SettingsTopLevelActions(
    val onDone: () -> Unit,
    val onOpenAppearance: () -> Unit,
    val onSelectFont: (mono: Boolean) -> Unit,
    val onCollapseTurnsChange: (Boolean) -> Unit,
    val onShowHomeTaskDetailsChange: (Boolean) -> Unit,
    val onPetVisibleChange: (Boolean) -> Unit,
    val onOpenPets: () -> Unit,
    val onOpenApps: () -> Unit,
    val onOpenExperimental: () -> Unit,
    val onOpenDebug: () -> Unit,
    val onOpenAccount: (serverId: String) -> Unit,
    val onEditServer: (serverId: String) -> Unit,
    val onRenameServer: (serverId: String) -> Unit,
    val onRemoveServer: (serverId: String) -> Unit,
)

private const val FontSample = "敏捷的棕色狐狸 · The quick brown fox"

/** Top-level settings list (iOS SettingsView order): stateless, rendered from [state]. */
@Composable
internal fun SettingsTopLevelContent(
    state: SettingsTopLevelState,
    actions: SettingsTopLevelActions,
    modifier: Modifier = Modifier,
) {
    SettingsPage(title = "设置", onDone = actions.onDone, modifier = modifier) {
        settingsSection("主题", key = "theme") {
            SettingsNavRow("外观", onClick = actions.onOpenAppearance, icon = Icons.Outlined.Palette)
        }

        settingsSection("字体", key = "font") {
            SettingsSelectRow(
                title = "系统默认",
                subtitle = FontSample,
                subtitleFontFamily = FontFamily.Default,
                selected = !state.monoFontEnabled,
                onClick = { actions.onSelectFont(false) },
            )
            SettingsRowDivider(indentForIcon = false)
            SettingsSelectRow(
                title = "Berkeley Mono",
                subtitle = FontSample,
                subtitleFontFamily = BerkeleyMono,
                selected = state.monoFontEnabled,
                onClick = { actions.onSelectFont(true) },
            )
        }

        settingsSection("对话", key = "conversation") {
            SettingsSwitchRow(
                title = "折叠回合",
                subtitle = "将之前的回合折叠为卡片",
                icon = Icons.Outlined.Compress,
                checked = state.collapseTurns,
                onCheckedChange = actions.onCollapseTurnsChange,
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                title = "首页显示任务详情",
                subtitle = "在首页任务卡上显示最新进展、模型和活动摘要",
                icon = Icons.Outlined.ViewAgenda,
                checked = state.showHomeTaskDetails,
                onCheckedChange = actions.onShowHomeTaskDetailsChange,
            )
        }

        settingsSection("宠物", key = "pets") {
            SettingsSwitchRow(
                title = "唤醒宠物",
                subtitle = state.petSubtitle,
                icon = Icons.Outlined.Pets,
                checked = state.petVisible,
                onCheckedChange = actions.onPetVisibleChange,
                onClick = actions.onOpenPets,
                onClickLabel = "打开宠物设置",
            )
        }

        if (state.showApps) {
            settingsSection("应用", key = "apps") {
                SettingsNavRow("已保存的 App", onClick = actions.onOpenApps, icon = Icons.Outlined.Widgets)
            }
        }

        settingsSection("实验性", key = "experimental") {
            SettingsNavRow("实验性功能", onClick = actions.onOpenExperimental, icon = Icons.Outlined.Science)
        }

        if (state.showDebug) {
            settingsSection("调试", key = "debug") {
                SettingsNavRow("调试设置", onClick = actions.onOpenDebug, icon = Icons.Outlined.BugReport)
            }
        }

        settingsSection("账户", key = "account") {
            val account = state.account
            if (account != null) {
                SettingsNavRow(
                    title = account.serverName,
                    subtitle = account.status,
                    icon = Icons.Outlined.AccountCircle,
                    onClick = { actions.onOpenAccount(account.serverId) },
                )
            } else {
                SettingsNoteRow("请先连接到服务器", icon = Icons.Outlined.Info)
            }
        }

        settingsSection("服务器", key = "servers") {
            if (state.servers.isEmpty()) {
                SettingsNoteRow("未连接服务器", icon = Icons.Outlined.Info)
            } else {
                state.servers.forEachIndexed { index, server ->
                    if (index > 0) SettingsRowDivider()
                    SettingsServerRow(
                        item = server,
                        onEdit = { actions.onEditServer(server.id) },
                        onRename = { actions.onRenameServer(server.id) },
                        onRemove = { actions.onRemoveServer(server.id) },
                    )
                }
            }
        }
    }
}
