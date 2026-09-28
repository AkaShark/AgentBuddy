package com.akashark.agentbuddy.android.ui.homeshell

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.home.QuickReplySheet
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostSummary
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import uniffi.codex_mobile_client.AppSessionSummary

/** An informational failure message ("回复失败", "分叉失败"…). */
data class HomeShellNotice(val title: String, val message: String)

/** Sheets and dialogs the home shell can show; one of each at a time. */
class HomeShellDialogState {
    var replyTarget by mutableStateOf<AppSessionSummary?>(null)
    var deleteTarget by mutableStateOf<AppSessionSummary?>(null)
    var renameTarget by mutableStateOf<HostSummary?>(null)
    var removeTarget by mutableStateOf<HostSummary?>(null)
    var notice by mutableStateOf<HomeShellNotice?>(null)
}

@Composable
fun HomeShellDialogs(
    state: HomeShellDialogState,
    taskActions: HomeTaskActions,
    hostActions: HomeHostActions,
) {
    state.replyTarget?.let { target ->
        QuickReplySheet(
            thread = target,
            onDismiss = { state.replyTarget = null },
            onSend = { key, text -> taskActions.sendQuickReply(key, text) },
        )
    }

    state.deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除任务？",
            message = "这将永久删除“${HomeTaskPresentation.title(target.displayTitle)}”。",
            confirmTitle = "删除",
            onConfirm = { taskActions.delete(target.key) },
            onDismiss = { state.deleteTarget = null },
        )
    }

    state.removeTarget?.let { host ->
        ConfirmDialog(
            title = "移除 ${host.name}？",
            message = "这台手机会忘记该主机的连接信息。电脑上正在运行的任务不会停止。",
            confirmTitle = "断开并移除",
            onConfirm = { hostActions.remove(host.serverId) },
            onDismiss = { state.removeTarget = null },
        )
    }

    state.renameTarget?.let { host ->
        RenameHostDialog(
            host = host,
            onSave = { name -> hostActions.rename(host.serverId, name) },
            onDismiss = { state.renameTarget = null },
        )
    }

    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = { state.notice = null },
            title = { Text(notice.title, style = buddyTextStyle(BuddyTextStyle.HEADING)) },
            text = { Text(notice.message, style = buddyTextStyle(BuddyTextStyle.BODY)) },
            confirmButton = {
                TextButton(onClick = { state.notice = null }) {
                    Text("好", style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold), color = AgentBuddyTheme.link)
                }
            },
            containerColor = AgentBuddyTheme.surface,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = buddyTextStyle(BuddyTextStyle.HEADING)) },
        text = { Text(message, style = buddyTextStyle(BuddyTextStyle.BODY)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(confirmTitle, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold), color = AgentBuddyTheme.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = buddyTextStyle(BuddyTextStyle.LABEL), color = AgentBuddyTheme.link)
            }
        },
        containerColor = AgentBuddyTheme.surface,
    )
}

@Composable
private fun RenameHostDialog(
    host: HostSummary,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(host.serverId) { mutableStateOf(host.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名主机", style = buddyTextStyle(BuddyTextStyle.HEADING)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("主机名称") },
                singleLine = true,
                textStyle = buddyTextStyle(BuddyTextStyle.BODY),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onSave(text)
                    onDismiss()
                },
            ) {
                Text("保存", style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = buddyTextStyle(BuddyTextStyle.LABEL), color = AgentBuddyTheme.link)
            }
        },
        containerColor = AgentBuddyTheme.surface,
    )
}
