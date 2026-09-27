package com.akashark.agentbuddy.android.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.TerminalSessionController
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme

@Composable
internal fun TerminalHeader(
    phase: TerminalSessionController.Phase,
    exitCode: Int?,
    selectedBackend: TerminalBackendOption?,
    backendOptions: List<TerminalBackendOption>,
    onSelectBackend: (TerminalBackendOption) -> Unit,
    onBack: () -> Unit,
    onConfigClick: () -> Unit = {},
) {
    var backendMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = AgentBuddyTheme.textPrimary,
            )
        }
        Text(
            text = "终端",
            color = AgentBuddyTheme.textPrimary,
            fontFamily = AgentBuddyTheme.monoFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        TextButton(
            onClick = { backendMenuExpanded = true },
            enabled = backendOptions.size > 1,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier
                .padding(start = 8.dp)
                .height(34.dp),
        ) {
            Icon(
                selectedBackend?.icon ?: Icons.Outlined.Storage,
                contentDescription = null,
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = selectedBackend?.title ?: "无后端",
                color = AgentBuddyTheme.accent,
                fontFamily = AgentBuddyTheme.monoFont,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        DropdownMenu(
            expanded = backendMenuExpanded,
            onDismissRequest = { backendMenuExpanded = false },
        ) {
            backendOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.title,
                            color = AgentBuddyTheme.textPrimary,
                            fontFamily = AgentBuddyTheme.monoFont,
                            fontSize = 13.sp,
                        )
                    },
                    leadingIcon = {
                        Icon(option.icon, contentDescription = null, tint = AgentBuddyTheme.accent)
                    },
                    onClick = {
                        backendMenuExpanded = false
                        onSelectBackend(option)
                    },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onConfigClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier
                .padding(end = 6.dp)
                .height(30.dp),
        ) {
            Text(
                text = "Aa",
                color = AgentBuddyTheme.accent,
                fontFamily = AgentBuddyTheme.monoFont,
                fontSize = 13.sp,
            )
        }
        Text(
            text = phaseLabel(phase, exitCode, selectedBackend?.runningLabel ?: "不可用"),
            color = phaseColor(phase),
            fontFamily = AgentBuddyTheme.monoFont,
            fontSize = 12.sp,
            modifier = Modifier
                .border(1.dp, phaseColor(phase).copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun TerminalAccessoryRow(
    controller: TerminalSessionController,
    canSendToAssistant: Boolean,
    onSendToAssistant: () -> Unit,
) {
    val scroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    val pasteText = clipboard.getText()?.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.96f))
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TerminalKey("Esc", enabled = controller.canSendInput) { controller.send("") }
        TerminalKey("Tab", enabled = controller.canSendInput) { controller.send("\t") }
        TerminalKey("Ctrl-C", enabled = controller.canSendInput) { controller.send("") }
        TerminalKey("Ctrl-D", enabled = controller.canSendInput) { controller.send("") }
        TerminalKey("Ctrl-Z", enabled = controller.canSendInput) { controller.send("") }
        TerminalKey("←", enabled = controller.canSendInput) { controller.send("[D") }
        TerminalKey("↑", enabled = controller.canSendInput) { controller.send("[A") }
        TerminalKey("↓", enabled = controller.canSendInput) { controller.send("[B") }
        TerminalKey("→", enabled = controller.canSendInput) { controller.send("[C") }
        TerminalKey("粘贴", enabled = controller.canSendInput && !pasteText.isNullOrEmpty()) {
            pasteText?.let(controller::send)
        }
        TerminalKey("清屏", enabled = controller.output.isNotEmpty()) { controller.clearOutput() }
        TerminalKey("发送给 AI", enabled = canSendToAssistant, onClick = onSendToAssistant)
    }
}

@Composable
private fun TerminalKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) AgentBuddyTheme.textSecondary else AgentBuddyTheme.textMuted,
            fontFamily = AgentBuddyTheme.monoFont,
            fontSize = 12.sp,
        )
    }
}

private fun phaseLabel(
    phase: TerminalSessionController.Phase,
    exitCode: Int?,
    runningLabel: String,
): String = when (phase) {
    TerminalSessionController.Phase.IDLE -> "空闲"
    TerminalSessionController.Phase.CONNECTING -> "连接中"
    TerminalSessionController.Phase.RUNNING -> runningLabel
    TerminalSessionController.Phase.EXITED -> "已退出 ${exitCode ?: 0}"
    TerminalSessionController.Phase.FAILED -> "失败"
}

private fun phaseColor(phase: TerminalSessionController.Phase): Color = when (phase) {
    TerminalSessionController.Phase.IDLE -> AgentBuddyTheme.textMuted
    TerminalSessionController.Phase.CONNECTING -> AgentBuddyTheme.warning
    TerminalSessionController.Phase.RUNNING -> AgentBuddyTheme.accent
    TerminalSessionController.Phase.EXITED -> AgentBuddyTheme.textMuted
    TerminalSessionController.Phase.FAILED -> AgentBuddyTheme.danger
}
