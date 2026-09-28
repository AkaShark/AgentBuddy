package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.ui.sessions.SessionsCallbacks
import com.akashark.agentbuddy.android.ui.sessions.SessionsContent
import com.akashark.agentbuddy.android.ui.sessions.SessionsViewState

private val noSessionsActions = SessionsCallbacks(
    onBack = {},
    onRefresh = {},
    onInfo = null,
    onForkCurrent = {},
    onNewTask = {},
    onConnectHost = {},
    onSearchQueryChange = {},
    onSelectServer = {},
    onToggleForksOnly = {},
    onSelectSort = {},
    onClearFilters = {},
    onToggleGroup = {},
    onToggleNode = {},
    onOpen = {},
    onFork = {},
    onRename = {},
    onArchive = {},
)

@Composable
internal fun GalleryTasksAllPage(state: SessionsViewState = GallerySessionsFixtures.state()) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        SessionsContent(state = state, actions = noSessionsActions)
    }
}
