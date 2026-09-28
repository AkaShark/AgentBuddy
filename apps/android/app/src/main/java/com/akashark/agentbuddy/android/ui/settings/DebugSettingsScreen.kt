package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.DebugSettings
import com.akashark.agentbuddy.android.state.MessageRecorder
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel

// ═══════════════════════════════════════════════════════════════════════════════
// Debug Sub-Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appModel = LocalAppModel.current
    var isRecording by remember { mutableStateOf(MessageRecorder.isRecording(appModel.store)) }
    var recordings by remember { mutableStateOf(MessageRecorder.listRecordings(context)) }

    SettingsPage(title = "调试", onBack = onBack) {
        settingsSection("渲染", key = "rendering") {
            SettingsSwitchRow(
                title = "禁用 Markdown",
                subtitle = "显示原始等宽文本而非渲染后的 markdown",
                icon = Icons.Outlined.TextFields,
                checked = DebugSettings.disableMarkdown,
                onCheckedChange = { DebugSettings.setDisableMarkdown(context, it) },
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                title = "显示回合指标",
                subtitle = "在回合项上显示耗时和 token 数",
                icon = Icons.Outlined.Timer,
                checked = DebugSettings.showTurnMetrics,
                onCheckedChange = { DebugSettings.setShowTurnMetrics(context, it) },
            )
        }

        settingsSection(
            title = "录制",
            key = "recording",
            footer = { SettingsFooter("调试功能仅用于开发和测试。", icon = Icons.Outlined.Info) },
        ) {
            SettingsRow(
                title = if (isRecording) "正在录制…" else "消息录制",
                titleColor = if (isRecording) AgentBuddyTheme.danger else AgentBuddyTheme.textPrimary,
                subtitle = "记录服务器消息以便回放",
                icon = Icons.Outlined.FiberManualRecord,
                iconTint = if (isRecording) AgentBuddyTheme.danger else AgentBuddyTheme.textSecondary,
                trailing = {
                    SettingsTextAction(
                        text = if (isRecording) "停止" else "开始",
                        color = if (isRecording) AgentBuddyTheme.danger else AgentBuddyTheme.link,
                        onClick = {
                            if (isRecording) {
                                MessageRecorder.stopRecording(context, appModel.store)
                                isRecording = false
                                recordings = MessageRecorder.listRecordings(context)
                            } else {
                                MessageRecorder.startRecording(appModel.store)
                                isRecording = true
                            }
                        },
                    )
                },
            )
            if (recordings.isNotEmpty()) {
                SettingsRowDivider(indentForIcon = false)
                SettingsNoteRow("已保存的录制")
                recordings.forEach { file ->
                    SettingsRowDivider(indentForIcon = false)
                    SettingsRow(
                        title = file.name,
                        subtitle = "${file.length() / 1024}KB",
                        trailing = {
                            SettingsTextAction(
                                text = "删除",
                                color = AgentBuddyTheme.danger,
                                onClick = {
                                    MessageRecorder.deleteRecording(file)
                                    recordings = MessageRecorder.listRecordings(context)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
