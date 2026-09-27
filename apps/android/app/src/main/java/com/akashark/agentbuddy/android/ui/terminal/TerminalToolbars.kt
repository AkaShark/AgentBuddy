package com.akashark.agentbuddy.android.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.TerminalSessionController
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * The terminal is black in both appearances, so its chrome always uses the
 * dark Mint palette (like iOS, which takes the dark theme's brand).
 */
internal object TerminalChrome {
    private val palette get() = AgentBuddyThemeManager.darkTheme
    val background: Color get() = Color.Black
    val textPrimary: Color get() = palette.textPrimary
    val textSecondary: Color get() = palette.textSecondary
    val control: Color get() = palette.surfaceLight
    val border: Color get() = palette.border
    val link: Color get() = palette.accent
    val brand: Color get() = palette.brand
    val onBrand: Color get() = palette.onBrand
    val danger: Color get() = palette.danger
    val disabled: Color get() = palette.onDisabled
}

/**
 * Title bar (back, 「终端」, 「Aa」) above the backend bar (backend picker and
 * the session phase as icon + text).
 */
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
    BuddyChromeTypeLimit {
        Column(Modifier.fillMaxWidth().background(TerminalChrome.background)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = BuddySpacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                BuddyIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    iconSize = BuddySize.iconLarge,
                    tint = TerminalChrome.textPrimary,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    text = "终端",
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = TerminalChrome.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                ConfigButton(onClick = onConfigClick, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = BuddySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BackendPicker(
                    selectedBackend = selectedBackend,
                    backendOptions = backendOptions,
                    onSelectBackend = onSelectBackend,
                    modifier = Modifier.weight(1f, fill = false).padding(end = BuddySpacing.xs),
                )
                PhaseChip(phase = phase, exitCode = exitCode, runningLabel = selectedBackend?.runningLabel ?: "不可用")
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TerminalChrome.border),
            )
        }
    }
}

@Composable
private fun ConfigButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(BuddySize.minHitTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "主题与字体" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = BuddySize.compactPill)
                .background(TerminalChrome.control, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold), color = TerminalChrome.link)
        }
    }
}

@Composable
private fun BackendPicker(
    selectedBackend: TerminalBackendOption?,
    backendOptions: List<TerminalBackendOption>,
    onSelectBackend: (TerminalBackendOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val canChoose = backendOptions.size > 1
    val content = if (selectedBackend != null) TerminalChrome.link else TerminalChrome.textSecondary
    Box(modifier) {
        Box(
            modifier =
                Modifier
                    .heightIn(min = BuddySize.minHitTarget)
                    .clip(CircleShape)
                    .clickable(enabled = canChoose, onClickLabel = "切换终端主机", role = Role.Button) { expanded = true }
                    .semantics { contentDescription = "终端主机：${selectedBackend?.title ?: "未选择主机"}" },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = BuddySize.compactPill)
                        .background(TerminalChrome.control, CircleShape)
                        .padding(horizontal = BuddySpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(selectedBackend?.icon ?: Icons.Outlined.Storage, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                Text(
                    text = selectedBackend?.title ?: "未选择主机",
                    style = buddyTextStyle(BuddyTextStyle.LABEL),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (canChoose) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            backendOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title, style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textPrimary) },
                    leadingIcon = { Icon(option.icon, contentDescription = null, tint = AgentBuddyTheme.textSecondary) },
                    onClick = {
                        expanded = false
                        onSelectBackend(option)
                    },
                    modifier = Modifier.heightIn(min = BuddySize.minHitTarget),
                )
            }
        }
    }
}

@Composable
private fun PhaseChip(
    phase: TerminalSessionController.Phase,
    exitCode: Int?,
    runningLabel: String,
) {
    val color = phaseColor(phase)
    Row(
        modifier =
            Modifier
                .heightIn(min = 28.dp)
                .background(color.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(phaseIcon(phase), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = phaseLabel(phase, exitCode, runningLabel),
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = color,
            maxLines = 1,
        )
    }
}

/** Key bar that reads the controller and the clipboard. */
@Composable
internal fun TerminalAccessoryRow(
    controller: TerminalSessionController,
    canSendToAssistant: Boolean,
    onSendToAssistant: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val pasteText = clipboard.getText()?.text
    TerminalKeyBar(
        canSendInput = controller.canSendInput,
        canPaste = controller.canSendInput && !pasteText.isNullOrEmpty(),
        canClear = controller.output.isNotEmpty(),
        canSendToAssistant = canSendToAssistant,
        onSend = controller::send,
        onPaste = { pasteText?.let(controller::send) },
        onClear = controller::clearOutput,
        onSendToAssistant = onSendToAssistant,
    )
}

/**
 * Scrollable special keys with 48dp targets, and 「发送给 AI」 pinned at the
 * trailing edge so it never scrolls out of reach.
 */
@Composable
internal fun TerminalKeyBar(
    canSendInput: Boolean,
    canPaste: Boolean,
    canClear: Boolean,
    canSendToAssistant: Boolean,
    onSend: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onSendToAssistant: () -> Unit,
) {
    BuddyChromeTypeLimit {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(TerminalChrome.background)
                    .padding(horizontal = BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TerminalKey("Esc", enabled = canSendInput) { onSend("\u001B") }
                TerminalKey("Tab", enabled = canSendInput) { onSend("\t") }
                TerminalKey("Ctrl-C", enabled = canSendInput, description = "Control C") { onSend("\u0003") }
                TerminalKey("Ctrl-D", enabled = canSendInput, description = "Control D") { onSend("\u0004") }
                TerminalKey("Ctrl-Z", enabled = canSendInput, description = "Control Z") { onSend("\u001A") }
                TerminalKey("←", enabled = canSendInput, description = "左方向键") { onSend("\u001B[D") }
                TerminalKey("↑", enabled = canSendInput, description = "上方向键") { onSend("\u001B[A") }
                TerminalKey("↓", enabled = canSendInput, description = "下方向键") { onSend("\u001B[B") }
                TerminalKey("→", enabled = canSendInput, description = "右方向键") { onSend("\u001B[C") }
                TerminalKey("粘贴", enabled = canPaste, onClick = onPaste)
                TerminalKey("清屏", enabled = canClear, onClick = onClear)
            }
            SendToAssistantButton(enabled = canSendToAssistant, onClick = onSendToAssistant)
        }
    }
}

@Composable
private fun TerminalKey(
    label: String,
    enabled: Boolean,
    description: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = BuddySize.minHitTarget)
                .widthIn(min = BuddySize.minHitTarget)
                .clip(BuddyShapes.control)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = 36.dp)
                .background(if (enabled) TerminalChrome.control else Color.Transparent, BuddyShapes.control)
                .padding(horizontal = BuddySpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = if (enabled) TerminalChrome.textPrimary else TerminalChrome.disabled,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SendToAssistantButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .heightIn(min = BuddySize.minHitTarget)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .background(if (enabled) TerminalChrome.brand else TerminalChrome.control, CircleShape)
                    .padding(horizontal = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = if (enabled) TerminalChrome.onBrand else TerminalChrome.disabled
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text("发送给 AI", style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold), color = content, maxLines = 1)
        }
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

private fun phaseIcon(phase: TerminalSessionController.Phase): ImageVector = when (phase) {
    TerminalSessionController.Phase.IDLE, TerminalSessionController.Phase.CONNECTING -> Icons.Outlined.Pending
    TerminalSessionController.Phase.RUNNING -> Icons.Outlined.Terminal
    TerminalSessionController.Phase.EXITED -> Icons.Outlined.CheckCircle
    TerminalSessionController.Phase.FAILED -> Icons.Outlined.ErrorOutline
}

private fun phaseColor(phase: TerminalSessionController.Phase): Color = when (phase) {
    TerminalSessionController.Phase.IDLE,
    TerminalSessionController.Phase.CONNECTING,
    TerminalSessionController.Phase.EXITED,
    -> TerminalChrome.textSecondary
    TerminalSessionController.Phase.RUNNING -> TerminalChrome.link
    TerminalSessionController.Phase.FAILED -> TerminalChrome.danger
}
