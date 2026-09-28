package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

/** Slash command definitions matching iOS. */
internal data class SlashCommand(val name: String, val description: String)
internal data class SlashInvocation(val command: SlashCommand, val args: String?) {
    /**
     * Commands that call the host (rather than opening a local sheet). Offline
     * they fail, so the composer keeps their text for another try.
     */
    val needsHost: Boolean
        get() =
            when (command.name) {
                "goal", "fork", "review" -> true
                "rename" -> !args.isNullOrBlank()
                else -> false
            }
}

private val SLASH_COMMANDS = listOf(
    SlashCommand("plan", "切换协作模式"),
    SlashCommand("model", "更改模型或推理强度"),
    SlashCommand("new", "开始新会话"),
    SlashCommand("fork", "分叉此对话"),
    SlashCommand("rename", "重命名此会话"),
    SlashCommand("review", "开始代码评审"),
    SlashCommand("goal", "设置或管理线程目标"),
    SlashCommand("resume", "浏览会话"),
    SlashCommand("skills", "列出可用技能"),
    SlashCommand("permissions", "更改权限"),
    SlashCommand("experimental", "切换实验性功能"),
)

internal fun filterSlashCommands(query: String): List<SlashCommand> =
    SLASH_COMMANDS.filter { it.name.startsWith(query) || query.isEmpty() }

internal fun parseSlashCommandInvocation(text: String): SlashInvocation? {
    val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
    if (!firstLine.startsWith("/")) return null
    val commandText = firstLine.drop(1).trim()
    if (commandText.isEmpty()) return null
    val parts = commandText.split(Regex("\\s+"), limit = 2)
    val command = SLASH_COMMANDS.firstOrNull { it.name == parts.first().lowercase() } ?: return null
    val args = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    return SlashInvocation(command = command, args = args)
}

@Composable
internal fun ComposerSlashCommandMenu(
    showSlashMenu: Boolean,
    filteredCommands: List<SlashCommand>,
    onDismissSlashMenu: () -> Unit,
    onSlashCommandSelected: (SlashCommand) -> Unit,
) {
    // Slash command popup
    DropdownMenu(
        expanded = showSlashMenu,
        onDismissRequest = { onDismissSlashMenu() },
    ) {
        for (cmd in filteredCommands) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("/${cmd.name}", color = AgentBuddyTheme.accent, fontSize = AgentBuddyTextStyle.footnote.scaled, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Text(cmd.description, color = AgentBuddyTheme.textMuted, fontSize = AgentBuddyTextStyle.caption.scaled)
                    }
                },
                onClick = {
                    onDismissSlashMenu()
                    onSlashCommandSelected(cmd)
                },
            )
        }
    }
}

@Composable
internal fun ComposerFileSearchMenu(
    showFileMenu: Boolean,
    fileSearchResults: List<String>,
    text: String,
    onDismissFileMenu: () -> Unit,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
) {
    // @file search popup
    DropdownMenu(
        expanded = showFileMenu,
        onDismissRequest = { onDismissFileMenu() },
    ) {
        for (path in fileSearchResults) {
            DropdownMenuItem(
                text = { Text(path, color = AgentBuddyTheme.textPrimary, fontSize = AgentBuddyTextStyle.caption.scaled, fontFamily = AgentBuddyTheme.monoFont) },
                onClick = {
                    onDismissFileMenu()
                    val atIdx = text.lastIndexOf('@')
                    if (atIdx >= 0) {
                        val updated = text.substring(0, atIdx) + "@$path "
                        onTextFieldValueChange(
                            TextFieldValue(
                                text = updated,
                                selection = TextRange(updated.length),
                            ),
                        )
                    }
                },
            )
        }
    }
}
