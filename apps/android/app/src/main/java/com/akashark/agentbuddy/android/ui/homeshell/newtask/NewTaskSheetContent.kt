package com.akashark.agentbuddy.android.ui.homeshell.newtask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Laptop
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyPageHeader
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** A host the new task can start on. */
data class NewTaskHostOption(val serverId: String, val name: String)

/**
 * 「开始一个新想法」: heading, host / project / model context chips, the
 * composer and, without a project, a banner pointing at the project picker.
 * Changing a chip keeps the draft.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTaskSheetContent(
    onCancel: () -> Unit,
    chips: @Composable FlowRowScope.() -> Unit,
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onChooseProject: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = BuddySpacing.xl).padding(bottom = BuddySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        BuddyPageHeader(
            title = "开始一个新想法",
            subtitle = "说说你想完成什么。搭子会在你选的主机上开始，并随时告诉你进展。",
            titleStyle = BuddyTextStyle.TITLE,
        ) {
            BuddyChromeTypeLimit {
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = BuddySize.minHitTarget)
                            .clickable(role = Role.Button, onClick = onCancel)
                            .padding(horizontal = BuddySpacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold), color = AgentBuddyTheme.link)
                }
            }
        }
        BuddyChromeTypeLimit {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs), content = chips)
        }
        composer()
        if (onChooseProject != null) {
            BuddyBanner(
                tone = BuddyBannerTone.INFO,
                message = "先选一个项目，搭子才知道在哪个文件夹里工作。",
                icon = Icons.Outlined.Folder,
                actionTitle = "选择项目",
                onAction = onChooseProject,
            )
        }
    }
}

/** Host chip with a menu of the hosts that can start a task. */
@Composable
fun NewTaskHostChip(
    hosts: List<NewTaskHostOption>,
    selectedServerId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = hosts.firstOrNull { it.serverId == selectedServerId }
    val label = selected?.name ?: "选择主机"
    Box {
        BuddyContextChip(
            text = label,
            onClick = { expanded = true },
            icon = Icons.Outlined.Laptop,
            trailingIcon = Icons.Outlined.KeyboardArrowDown,
            enabled = hosts.isNotEmpty(),
            modifier = Modifier.widthIn(max = 220.dp).semantics { contentDescription = "主机：$label" },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = AgentBuddyTheme.surface) {
            hosts.forEach { host ->
                DropdownMenuItem(
                    text = { Text(host.name, style = buddyTextStyle(BuddyTextStyle.BODY)) },
                    leadingIcon = {
                        if (host.serverId == selectedServerId) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = AgentBuddyTheme.textPrimary)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(host.serverId)
                    },
                )
            }
        }
    }
}
