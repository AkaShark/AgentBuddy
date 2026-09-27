import SwiftUI

extension HomeNavigationView {
    /// Ambient hero-composer rendering for the split-view detail root. No
    /// auto-focus (so popping back from a conversation doesn't summon the
    /// keyboard) and no Cancel toolbar (there's nothing to cancel to at the
    /// root). On send it replaces itself with `.conversation(key)` via the
    /// same path as the pushed hero, so the handoff is identical.
    var splitDetailRoot: some View {
        NewThreadHeroView(
            project: homeDashboardModel.selectedProject,
            connectedServers: homeDashboardModel.connectedServers,
            selectedServerId: homeDashboardModel.selectedServerId,
            onSelectServer: { serverId in
                homeDashboardModel.selectedServerId = serverId
            },
            onOpenProjectPicker: { showProjectPicker = true },
            onThreadCreated: { key in
                homeDashboardModel.pinThread(key)
                // Root is already the hero; just push the conversation on top.
                openConversation(key)
            },
            onCancel: nil,
            autoFocus: false
        )
    }

    /// Sidebar projection of the home dashboard used inside
    /// `NavigationSplitView`. Same data + callbacks as `homeDashboard`, but
    /// renders with `.sidebar` chrome (no animated logo, no zoom, no bottom
    /// composer) and exposes an `onNewThread` hook that pushes the hero
    /// composer into the detail pane.
    var sidebarDashboard: some View {
        HomeDashboardView(
            chrome: .sidebar,
            recentSessions: homeDashboardModel.recentSessions,
            allSessions: homeDashboardModel.allSessions,
            pinnedThreadKeys: homeDashboardModel.pinnedKeys,
            connectedServers: homeDashboardModel.connectedServers,
            projects: homeDashboardModel.projects,
            selectedServerId: homeDashboardModel.selectedServerId,
            selectedProject: homeDashboardModel.selectedProject,
            openingRecentSessionKey: openingRecentSessionKey,
            onOpenRecentSession: openRecentSession,
            onSelectServer: handleSelectServer,
            onAddServer: { appState.showServerPicker = true },
            onOpenProjectPicker: { showProjectPicker = true },
            onThreadCreated: { key in homeDashboardModel.pinThread(key) },
            onShowSettings: { appState.showSettings = true },
            onShowApps: savedAppsStore.apps.isEmpty ? nil : { navigationPath.append(.appsList) },
            onShowTerminal: terminalLauncher,
            onPinThread: pinThread,
            onUnpinThread: unpinThread,
            onHideThread: hideThread,
            onNewThread: { openNewThread() },
            onHydrateThread: { key, loadInitialTurns in
                await hydrateThread(key, loadInitialTurns: loadInitialTurns)
            },
            onDeleteThread: deleteThread,
            onReconnectServer: reconnectServer,
            onRestartAppServer: restartAppServer,
            onDisconnectServer: disconnectServer,
            onRenameServer: renameServer,
            onOpenRecording: { url in
                navigationPath.append(.replayRecording(url))
            },
            onSendReply: sendQuickReply,
            onCancelThread: cancelThread,
            onForkThread: forkSessionFromHome,
            onInputModeChange: { mode in
                homeInputMode = mode
            },
            onSearchThreads: loadSearchThreads
        )
    }

    var homeDashboard: some View {
        HomeDashboardView(
            recentSessions: homeDashboardModel.recentSessions,
            allSessions: homeDashboardModel.allSessions,
            pinnedThreadKeys: homeDashboardModel.pinnedKeys,
            connectedServers: homeDashboardModel.connectedServers,
            projects: homeDashboardModel.projects,
            selectedServerId: homeDashboardModel.selectedServerId,
            selectedProject: homeDashboardModel.selectedProject,
            openingRecentSessionKey: openingRecentSessionKey,
            onOpenRecentSession: openRecentSession,
            onSelectServer: handleSelectServer,
            onAddServer: { appState.showServerPicker = true },
            onOpenProjectPicker: { showProjectPicker = true },
            onThreadCreated: { key in homeDashboardModel.pinThread(key) },
            onShowSettings: { appState.showSettings = true },
            onShowApps: savedAppsStore.apps.isEmpty ? nil : { navigationPath.append(.appsList) },
            onShowTerminal: terminalLauncher,
            onPinThread: pinThread,
            onUnpinThread: unpinThread,
            onHideThread: hideThread,
            onHydrateThread: { key, loadInitialTurns in
                await hydrateThread(key, loadInitialTurns: loadInitialTurns)
            },
            onDeleteThread: deleteThread,
            onReconnectServer: reconnectServer,
            onRestartAppServer: restartAppServer,
            onDisconnectServer: disconnectServer,
            onRenameServer: renameServer,
            onOpenRecording: { url in
                navigationPath.append(.replayRecording(url))
            },
            onSendReply: sendQuickReply,
            onCancelThread: cancelThread,
            onForkThread: forkSessionFromHome,
            onInputModeChange: { mode in
                homeInputMode = mode
            },
            onSearchThreads: loadSearchThreads
        )
    }

    func updateHomeDashboardActivity() {
        if isHomeRouteActive {
            homeDashboardModel.activate()
        } else {
            homeDashboardModel.deactivate()
        }
    }
}
