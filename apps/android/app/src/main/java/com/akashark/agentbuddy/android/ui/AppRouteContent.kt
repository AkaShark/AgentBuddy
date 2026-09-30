package com.akashark.agentbuddy.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import com.akashark.agentbuddy.android.ui.apps.AppsListScreen
import com.akashark.agentbuddy.android.ui.apps.SavedAppScreen
import com.akashark.agentbuddy.android.ui.conversation.ConversationInfoScreen
import com.akashark.agentbuddy.android.ui.conversation.ConversationScreen
import com.akashark.agentbuddy.android.ui.homeshell.HomeShellActions
import com.akashark.agentbuddy.android.ui.homeshell.HomeShellScreen
import com.akashark.agentbuddy.android.ui.sessions.SessionsScreen
import com.akashark.agentbuddy.android.ui.sessions.SessionsUiState
import com.akashark.agentbuddy.android.ui.settings.WallpaperAdjustScreen
import com.akashark.agentbuddy.android.ui.settings.WallpaperSelectionScreen
import com.akashark.agentbuddy.android.ui.terminal.TerminalScreen
import com.akashark.agentbuddy.android.ui.voice.RealtimeVoiceScreen
import uniffi.codex_mobile_client.AppProject

/**
 * Renders the current route. The home shell keeps its saveable state (tab,
 * scroll, search) in [homeStateHolder] so returning from a conversation
 * restores it.
 */
@Composable
fun AppRouteContent(
    route: Route,
    shell: AppShellState,
    navActions: AppNavigationActions,
    homeActions: HomeShellActions,
    projects: List<AppProject>,
    sessionsUiState: SessionsUiState,
    homeStateHolder: SaveableStateHolder,
    terminalEnabled: Boolean,
) {
    when (route) {
        is Route.Home ->
            homeStateHolder.SaveableStateProvider("home") {
                HomeShellScreen(
                    actions = homeActions,
                    memory = shell.homeMemory,
                    selectedServerId = shell.selectedServerId,
                    selectedProject = shell.selectedProject,
                    projects = projects,
                    isStartingVoice = shell.isStartingVoice,
                )
            }

        is Route.Sessions ->
            SessionsScreen(
                serverId = route.serverId,
                title = route.title,
                projectCwd = route.projectCwd,
                sessionsUiState = sessionsUiState,
                onOpenConversation = shell::pushConversation,
                // A project page starts new tasks from the project card instead.
                onNewSession = if (route.projectCwd == null) ({ navActions.openDirectoryPicker(route.serverId) }) else null,
                onBack = shell::navigateBack,
                onInfo = route.serverId?.let { serverId -> { shell.navigate(Route.ServerInfo(serverId)) } },
                stopMarkers = shell.homeMemory.cancelling,
            )

        is Route.Conversation ->
            ConversationScreen(
                threadKey = route.key,
                onBack = shell::navigateBack,
                onInfo = { shell.navigate(Route.ConversationInfo(route.key)) },
                onNavigateToSessions = { shell.navigate(Route.Sessions(serverId = null, title = "全部任务")) },
                onShowDirectoryPicker = { navActions.openDirectoryPicker(route.key.serverId) },
                onOpenSavedApp = { appId -> shell.navigate(Route.SavedApp(appId)) },
            )

        is Route.ConversationInfo ->
            ConversationInfoScreen(
                threadKey = route.key,
                onBack = shell::navigateBack,
                onChangeWallpaper = { shell.navigate(Route.WallpaperSelection(route.key)) },
            )

        is Route.WallpaperSelection ->
            WallpaperSelectionScreen(
                threadKey = route.key,
                onBack = {
                    WallpaperManager.clearPendingWallpaper()
                    shell.navigateBack()
                },
                onApplied = { shell.popRoutes { it is Route.WallpaperSelection || it is Route.WallpaperAdjust } },
            )

        is Route.WallpaperAdjust ->
            WallpaperAdjustScreen(
                threadKey = route.key,
                onBack = shell::navigateBack,
                // Pop back to conversation info (it stays on the stack).
                onApplied = { shell.popRoutes { it is Route.WallpaperSelection || it is Route.WallpaperAdjust } },
            )

        is Route.ServerInfo ->
            ConversationInfoScreen(
                threadKey = null,
                serverId = route.serverId,
                onBack = shell::navigateBack,
                onChangeWallpaper = { shell.navigate(Route.ServerWallpaperSelection(route.serverId)) },
                onOpenShell = navActions.remoteShellLauncher(route.serverId, terminalEnabled),
            )

        is Route.ServerWallpaperSelection ->
            WallpaperSelectionScreen(
                threadKey = null,
                serverId = route.serverId,
                onBack = {
                    WallpaperManager.clearPendingWallpaper()
                    shell.navigateBack()
                },
                onApplied = { shell.popRoutes { it is Route.ServerWallpaperSelection || it is Route.ServerWallpaperAdjust } },
            )

        is Route.ServerWallpaperAdjust ->
            WallpaperAdjustScreen(
                threadKey = null,
                serverId = route.serverId,
                onBack = shell::navigateBack,
                onApplied = { shell.popRoutes { it is Route.ServerWallpaperSelection || it is Route.ServerWallpaperAdjust } },
            )

        is Route.RealtimeVoice -> RealtimeVoiceScreen(threadKey = route.key, onBack = shell::navigateBack)

        is Route.Apps ->
            AppsListScreen(
                onBack = shell::navigateBack,
                onOpenApp = { appId -> shell.navigate(Route.SavedApp(appId)) },
            )

        is Route.SavedApp ->
            SavedAppScreen(
                appId = route.appId,
                onBack = shell::navigateBack,
                onOpenConversation = { key -> shell.navigate(Route.Conversation(key)) },
            )

        is Route.Terminal -> TerminalScreen(preferredAlleycatNodeId = route.preferredAlleycatNodeId, onBack = shell::navigateBack)
    }
}
