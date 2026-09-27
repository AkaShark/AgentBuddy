package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import uniffi.codex_mobile_client.ThreadKey
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import com.akashark.agentbuddy.android.state.AppModel
import uniffi.codex_mobile_client.AppSessionSummary

/// Empty/loading state, or the grouped, collapsible session tree.
@Composable
internal fun ColumnScope.SessionsListContent(
    derived: SessionsDerivedData,
    isLoading: Boolean,
    listState: LazyListState,
    sessionsUiState: SessionsUiState,
    appModel: AppModel,
    onOpenConversation: (ThreadKey) -> Unit,
    onSessionNodeToggled: () -> Unit,
    onForkThread: (AppSessionSummary) -> Unit,
) {
    if (derived.totalCount == 0) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = AgentBuddyTheme.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "暂无会话(对话)",
                    color = AgentBuddyTheme.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
    } else {
        if (isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                CircularProgressIndicator(
                    color = AgentBuddyTheme.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "正在加载更多会话(对话)...",
                    color = AgentBuddyTheme.textMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // Session list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            for (group in derived.groups) {
                val groupKey = SessionsDerivation.workspaceGroupKey(group.serverId, group.cwd)
                val isCollapsed = groupKey in sessionsUiState.collapsedWorkspaceGroupKeys

                // Group header
                item(key = "header-$groupKey") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sessionsUiState.toggleWorkspaceGroup(groupKey)
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(
                            if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AgentBuddyTheme.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = group.workspaceLabel,
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${group.nodes.size}",
                            color = AgentBuddyTheme.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Session nodes (if expanded)
                if (!isCollapsed) {
                    items(
                        items = visibleSessionRows(group.nodes, sessionsUiState.collapsedSessionNodeKeys),
                        key = { "${it.summary.key.serverId}/${it.summary.key.threadId}" },
                    ) { node ->
                        SessionNodeRow(
                            node = node,
                            hasChildren = node.children.isNotEmpty(),
                            isCollapsed = node.summary.key in sessionsUiState.collapsedSessionNodeKeys,
                            onToggleCollapse = {
                                if (node.children.isNotEmpty()) {
                                    sessionsUiState.toggleSessionNode(node.summary.key)
                                    onSessionNodeToggled()
                                }
                            },
                            onClick = {
                                appModel.launchState.updateCurrentCwd(node.summary.cwd)
                                onOpenConversation(node.summary.key)
                            },
                            onFork = {
                                onForkThread(node.summary)
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

internal fun visibleSessionRows(
    nodes: List<SessionTreeNode>,
    collapsedSessionNodeKeys: Set<ThreadKey>,
): List<SessionTreeNode> {
    val result = mutableListOf<SessionTreeNode>()
    fun walk(node: SessionTreeNode) {
        result.add(node)
        if (node.summary.key !in collapsedSessionNodeKeys) {
            node.children.forEach { walk(it) }
        }
    }
    nodes.forEach { walk(it) }
    return result
}
