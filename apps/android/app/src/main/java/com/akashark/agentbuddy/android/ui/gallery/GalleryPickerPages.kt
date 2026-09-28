package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.runtime.Composable
import com.akashark.agentbuddy.android.ui.home.ProjectPickerContent
import com.akashark.agentbuddy.android.ui.home.ProjectPickerItem
import com.akashark.agentbuddy.android.ui.sessions.DirectoryPathSegment
import com.akashark.agentbuddy.android.ui.sessions.DirectoryPickerCallbacks
import com.akashark.agentbuddy.android.ui.sessions.DirectoryPickerLayout
import com.akashark.agentbuddy.android.ui.sessions.DirectoryPickerServerOption
import com.akashark.agentbuddy.android.ui.sessions.DirectoryPickerViewState
import com.akashark.agentbuddy.android.ui.sessions.DirectoryRecentRow

private val pickerServers = listOf(
    DirectoryPickerServerOption(id = "mac", name = "MacBook Pro", sourceLabel = "远程"),
    DirectoryPickerServerOption(id = "local", name = "本设备", sourceLabel = "本地"),
)

private val directoryState = DirectoryPickerViewState(
    servers = pickerServers,
    selectedServerId = "mac",
    serverLabel = "MacBook Pro • 远程",
    showServerMenu = false,
    showHiddenDirectories = false,
    searchQuery = "",
    currentPath = "/Users/me/Projects",
    currentPathDisplay = "~/Projects",
    segments = listOf(
        DirectoryPathSegment("/", "/"),
        DirectoryPathSegment("Users", "/Users"),
        DirectoryPathSegment("me", "/Users/me"),
        DirectoryPathSegment("Projects", "/Users/me/Projects"),
    ),
    canGoUp = true,
    canBrowse = true,
    isLoading = false,
    errorMessage = null,
    continueEntry = DirectoryRecentRow("/Users/me/Projects/AgentBuddy", "AgentBuddy", "~/Projects/AgentBuddy", "18 分钟前"),
    recents = listOf(
        DirectoryRecentRow("/Users/me/Projects/AgentBuddy", "AgentBuddy", "~/Projects/AgentBuddy", "18 分钟前"),
        DirectoryRecentRow("/Users/me/Personal", "Personal", "~/Personal", "昨天"),
    ),
    folders = listOf("AgentBuddy", "alleycat", "design-system", "notes", "website"),
)

private val noDirectoryActions = DirectoryPickerCallbacks(
    onShowServerMenuChange = {},
    onSelectServer = {},
    onToggleHiddenDirectories = {},
    onSearchQueryChange = {},
    onNavigateUp = {},
    onOpenGoToPath = {},
    onOpenPath = {},
    onOpenFolder = {},
    onSelectRecent = {},
    onClearRecents = {},
    onRetry = {},
    onDismiss = {},
    onSelectCurrentPath = {},
)

@Composable
internal fun GalleryDirectoryPickerPage(error: Boolean = false) {
    GallerySheetFrame {
        DirectoryPickerLayout(
            state = if (error) {
                directoryState.copy(errorMessage = "所选服务器未连接。", folders = emptyList())
            } else {
                directoryState
            },
            actions = noDirectoryActions,
        )
    }
}

private val projectItems = listOf(
    ProjectPickerItem("mac::/Users/me/Projects/AgentBuddy", "AgentBuddy", "MacBook Pro", "~/Projects/AgentBuddy", "/Users/me/Projects/AgentBuddy"),
    ProjectPickerItem("mini::/Users/studio/Personal", "Personal", "Mac mini", "~/Personal", "/Users/studio/Personal"),
    ProjectPickerItem("mac::/Users/me/Projects/website", "website", "MacBook Pro", "~/Projects/website", "/Users/me/Projects/website"),
    ProjectPickerItem("local::/workspace", "workspace", "本设备", "~", "/workspace"),
)

@Composable
internal fun GalleryProjectPickerPage(empty: Boolean = false) {
    GallerySheetFrame {
        ProjectPickerContent(
            items = if (empty) emptyList() else projectItems,
            query = "",
            onQueryChange = {},
            selectedProjectId = projectItems.first().id,
            onSelect = {},
            onCreateNew = {},
            onDismiss = {},
        )
    }
}
