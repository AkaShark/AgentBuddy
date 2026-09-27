package com.akashark.agentbuddy.android.ui.homeshell.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.home.ThreadSearchResults
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.PinnedThreadKey

/** Search mode data of the 任务 tab. */
data class TasksSearchUiState(
    val query: String,
    val sessions: List<AppSessionSummary>,
    val pinnedKeys: Set<PinnedThreadKey>,
    val runtimeKinds: List<AgentRuntimeKind>,
    val selectedRuntimeKind: AgentRuntimeKind?,
    val isRefreshing: Boolean,
)

class TasksSearchCallbacks(
    val onQueryChange: (String) -> Unit,
    val onRuntimeSelected: (AgentRuntimeKind?) -> Unit,
    val onRefresh: () -> Unit,
    val onPin: (AppSessionSummary) -> Unit,
    val onUnpin: (AppSessionSummary) -> Unit,
    val onClear: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * 任务 tab in search mode: the header stays, the hero becomes a search field
 * and the sections become server-side search results (tap to pin).
 */
@Composable
fun TasksSearchContent(
    state: TasksSearchUiState,
    callbacks: TasksSearchCallbacks,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gutter = BuddySpacing.pageGutter(maxWidth)
        Column(Modifier.fillMaxSize().padding(horizontal = gutter).imePadding()) {
            Box(Modifier.padding(top = BuddySpacing.xs)) { header() }
            TaskSearchField(
                query = state.query,
                onQueryChange = callbacks.onQueryChange,
                onClear = callbacks.onClear,
                onCancel = callbacks.onCancel,
                autoFocus = autoFocus,
                modifier = Modifier.padding(top = BuddySpacing.sm, bottom = BuddySpacing.xs),
            )
            ThreadSearchResults(
                sessions = state.sessions,
                pinnedKeys = state.pinnedKeys,
                query = state.query,
                runtimeKinds = state.runtimeKinds,
                selectedRuntimeKind = state.selectedRuntimeKind,
                isRefreshing = state.isRefreshing,
                onRuntimeSelected = callbacks.onRuntimeSelected,
                onRefresh = callbacks.onRefresh,
                onPin = callbacks.onPin,
                onUnpin = callbacks.onUnpin,
                onClearSearch = callbacks.onClear,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TaskSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = BuddySize.control)
                    .background(AgentBuddyTheme.surface, BuddyShapes.button)
                    .border(1.dp, AgentBuddyTheme.borderControl, BuddyShapes.button)
                    .padding(start = BuddySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(20.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = buddyTextStyle(BuddyTextStyle.BODY).copy(color = AgentBuddyTheme.textPrimary),
                cursorBrush = SolidColor(AgentBuddyTheme.focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f).padding(vertical = BuddySpacing.sm).focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("搜索任务", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textMuted)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                BuddyIconButton(icon = Icons.Outlined.Cancel, contentDescription = "清除搜索", onClick = onClear, tint = AgentBuddyTheme.textSecondary)
            }
        }
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
}
