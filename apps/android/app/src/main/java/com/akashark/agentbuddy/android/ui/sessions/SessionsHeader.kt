package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSnapshotRecord

/// Title row: back, title, filtered/total count, fork-active, refresh and info.
@Composable
internal fun SessionsTopBar(
    title: String,
    derived: SessionsDerivedData,
    snapshot: AppSnapshotRecord?,
    isForkingActiveThread: Boolean,
    isLoading: Boolean,
    hasLoadedInitialSessions: Boolean,
    connectedServerIds: List<String>,
    onBack: () -> Unit,
    onForkThread: (AppSessionSummary) -> Unit,
    onRefresh: () -> Unit,
    onInfo: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = AgentBuddyTheme.textPrimary,
            )
        }
        Text(
            text = title,
            color = AgentBuddyTheme.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${derived.filteredCount}/${derived.totalCount}",
            color = AgentBuddyTheme.textMuted,
            fontSize = 12.sp,
        )
        val activeSummary = snapshot?.activeThread?.let { activeKey ->
            snapshot?.sessionSummaries?.firstOrNull { it.key == activeKey }
        }
        if (activeSummary != null) {
            TextButton(
                onClick = { onForkThread(activeSummary) },
                enabled = !isForkingActiveThread && !activeSummary.hasActiveTurn,
            ) {
                if (isForkingActiveThread) {
                    CircularProgressIndicator(
                        color = AgentBuddyTheme.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text("分叉", color = AgentBuddyTheme.accent, fontSize = 12.sp)
                }
            }
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isLoading && connectedServerIds.isNotEmpty(),
            modifier = Modifier.size(32.dp),
        ) {
            if (isLoading && hasLoadedInitialSessions) {
                CircularProgressIndicator(
                    color = AgentBuddyTheme.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新会话",
                    tint = if (connectedServerIds.isEmpty()) {
                        AgentBuddyTheme.textMuted
                    } else {
                        AgentBuddyTheme.accent
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "服务器信息",
                    tint = AgentBuddyTheme.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/// Search field + fork-only chip + sort-mode chip/menu.
@Composable
internal fun SessionsSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sessionsUiState: SessionsUiState,
    onSortModeChanged: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(AgentBuddyTheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (searchQuery.isEmpty()) {
                Text("搜索会话\u2026", color = AgentBuddyTheme.textMuted, fontSize = 13.sp)
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                textStyle = TextStyle(color = AgentBuddyTheme.textPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(AgentBuddyTheme.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FilterChip(
            selected = sessionsUiState.showOnlyForks,
            onClick = { sessionsUiState.showOnlyForks = !sessionsUiState.showOnlyForks },
            label = { Text("分叉", fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AgentBuddyTheme.accent,
                selectedLabelColor = Color.Black,
            ),
        )
        Box {
            FilterChip(
                selected = sessionsUiState.sortMode != WorkspaceSortMode.RECENT,
                onClick = { showSortMenu = true },
                label = { Text(sessionsUiState.sortMode.title, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AgentBuddyTheme.accent,
                    selectedLabelColor = Color.Black,
                ),
            )
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                WorkspaceSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.title) },
                        onClick = {
                            sessionsUiState.sortMode = mode
                            showSortMenu = false
                            onSortModeChanged()
                        },
                    )
                }
            }
        }
    }
}
