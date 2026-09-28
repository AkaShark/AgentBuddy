package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.discovery.MintContentMaxWidth
import com.akashark.agentbuddy.android.ui.discovery.mintPageGutter

/** One path crumb: display label and the full path it opens. */
internal data class DirectoryPathSegment(val label: String, val path: String)

/** A recent folder, shaped for display. */
internal data class DirectoryRecentRow(
    val path: String,
    val title: String,
    val pathDisplay: String,
    val timeLabel: String,
)

/** Everything the stateless folder picker renders. */
internal data class DirectoryPickerViewState(
    val servers: List<DirectoryPickerServerOption>,
    val selectedServerId: String,
    val serverLabel: String?,
    val showServerMenu: Boolean,
    val showHiddenDirectories: Boolean,
    val searchQuery: String,
    val currentPath: String,
    val currentPathDisplay: String,
    val segments: List<DirectoryPathSegment>,
    val canGoUp: Boolean,
    val canBrowse: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
    val continueEntry: DirectoryRecentRow?,
    val recents: List<DirectoryRecentRow>,
    val folders: List<String>,
)

/** User actions of the folder picker. */
internal class DirectoryPickerCallbacks(
    val onShowServerMenuChange: (Boolean) -> Unit,
    val onSelectServer: (String) -> Unit,
    val onToggleHiddenDirectories: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onNavigateUp: () -> Unit,
    val onOpenGoToPath: () -> Unit,
    val onOpenPath: (String) -> Unit,
    val onOpenFolder: (String) -> Unit,
    val onSelectRecent: (String) -> Unit,
    val onClearRecents: () -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit,
    val onSelectCurrentPath: () -> Unit,
)

/**
 * Stateless folder picker: header controls, the recents / folder list (or
 * loading / error) and the 取消 / 选择此文件夹 footer.
 */
@Composable
internal fun DirectoryPickerLayout(
    state: DirectoryPickerViewState,
    actions: DirectoryPickerCallbacks,
    modifier: Modifier = Modifier,
) {
    val gutter = mintPageGutter()
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = MintContentMaxWidth).fillMaxWidth()) {
            DirectoryPickerHeader(
                state = state,
                actions = actions,
                modifier = Modifier.padding(horizontal = gutter).padding(bottom = BuddySpacing.xs),
            )
            DirectoryPickerList(
                state = state,
                actions = actions,
                gutter = gutter,
                modifier = Modifier.weight(1f),
            )
            DirectoryPickerFooter(
                currentPathDisplay = state.currentPathDisplay,
                canSelect = state.currentPath.isNotBlank(),
                onDismiss = actions.onDismiss,
                onSelectCurrentPath = actions.onSelectCurrentPath,
                modifier = Modifier.padding(horizontal = gutter).padding(bottom = BuddySpacing.sm),
            )
        }
    }
}
