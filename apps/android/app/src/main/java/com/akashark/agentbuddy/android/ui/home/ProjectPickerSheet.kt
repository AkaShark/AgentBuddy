package com.akashark.agentbuddy.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.PathDisplay
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.discovery.DiscoverySheetHeader
import com.akashark.agentbuddy.android.ui.discovery.DiscoverySheetScaffold
import com.akashark.agentbuddy.android.ui.sessions.SessionsSearchField
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.projectDefaultLabel

/** One project (host + folder) row of the picker, already shaped for display. */
internal data class ProjectPickerItem(
    val id: String,
    val name: String,
    val hostName: String?,
    val pathDisplay: String,
    /** Raw cwd, kept for search. */
    val cwd: String,
)

/**
 * Project picker: search by name, host or path, pick a project (the current
 * one carries a check), or 「新建项目」 to choose a folder in the directory
 * picker. Every row shows its host, so the same folder name on two computers
 * stays distinguishable.
 */
@Composable
fun ProjectPickerSheet(
    projects: List<AppProject>,
    serverNamesById: Map<String, String>,
    isLocalById: Map<String, Boolean>,
    onSelect: (AppProject) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    selectedProjectId: String? = null,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val items = remember(projects, serverNamesById, isLocalById) {
        projects.map { project ->
            ProjectPickerItem(
                id = project.id,
                name = projectDefaultLabel(project.cwd),
                hostName = serverNamesById[project.serverId]?.let { if (it == "This Device") "本设备" else it },
                pathDisplay = PathDisplay.display(project.cwd, isLocalById[project.serverId] == true, context),
                cwd = project.cwd,
            )
        }
    }
    ProjectPickerContent(
        items = items,
        query = query,
        onQueryChange = { query = it },
        selectedProjectId = selectedProjectId,
        onSelect = { item ->
            projects.firstOrNull { it.id == item.id }?.let { project ->
                onSelect(project)
                onDismiss()
            }
        },
        onCreateNew = onCreateNew,
        onDismiss = onDismiss,
    )
}

/** Stateless body of [ProjectPickerSheet] (also rendered by the gallery). */
@Composable
internal fun ProjectPickerContent(
    items: List<ProjectPickerItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedProjectId: String?,
    onSelect: (ProjectPickerItem) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmed = query.trim().lowercase()
    val filtered = if (trimmed.isEmpty()) {
        items
    } else {
        items.filter { item ->
            item.name.lowercase().contains(trimmed) ||
                item.cwd.lowercase().contains(trimmed) ||
                (item.hostName ?: "").lowercase().contains(trimmed)
        }
    }

    DiscoverySheetScaffold(
        modifier = modifier,
        contentSpacing = BuddySpacing.md,
        header = {
            DiscoverySheetHeader(
                title = "项目",
                actionTitle = "关闭",
                onAction = onDismiss,
                trailing = {
                    BuddyContextChip(text = "新建项目", icon = Icons.Outlined.Add, onClick = onCreateNew)
                },
            )
        },
    ) {
        SessionsSearchField(query = query, onQueryChange = onQueryChange, placeholder = "搜索项目")
        if (filtered.isEmpty()) {
            BuddyEmptyState(
                icon = if (trimmed.isEmpty()) Icons.Outlined.CreateNewFolder else Icons.Outlined.Search,
                title = if (trimmed.isEmpty()) "暂无项目" else "没有匹配的项目",
                message = if (trimmed.isEmpty()) {
                    "在主机上选择一个文件夹，创建你的第一个项目。"
                } else {
                    "换个名称、主机或路径试试，或打开新的文件夹。"
                },
                actionTitle = "新建项目",
                actionIcon = Icons.Outlined.Add,
                actionKind = if (trimmed.isEmpty()) BuddyButtonKind.PRIMARY else BuddyButtonKind.SECONDARY,
                onAction = onCreateNew,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .buddyCard(BuddySurfaceTone.SURFACE, padding = null),
            ) {
                filtered.forEachIndexed { index, item ->
                    if (index > 0) BuddyDivider(startIndent = BuddySpacing.md + BuddySize.rowTile + BuddySpacing.md)
                    ProjectRow(
                        item = item,
                        selected = item.id == selectedProjectId,
                        onClick = { onSelect(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    item: ProjectPickerItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuddyShapes.card)
            .clickable(role = Role.Button, onClickLabel = "选择此项目", onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .heightIn(min = BuddySize.listRow)
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuddyIconTile(
            content = BuddyTileContent.Initial(item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"),
            fill = if (selected) AgentBuddyTheme.brand else AgentBuddyTheme.surfaceSoft,
            foreground = if (selected) AgentBuddyTheme.onBrand else AgentBuddyTheme.textPrimary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.name,
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(item.hostName, item.pathDisplay).joinToString(" · "),
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "当前项目",
                tint = AgentBuddyTheme.action,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
