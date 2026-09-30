import SwiftUI

extension HomeNavigationView {
    /// Ambient hero-composer rendering for the split-view detail root. No
    /// auto-focus (so popping back from a conversation doesn't summon the
    /// keyboard) and no Cancel toolbar (there's nothing to cancel to at the
    /// root). On send it replaces itself with `.conversation(key)` via the
    /// same path as the pushed hero, so the handoff is identical.
    var splitDetailRoot: some View {
        NewThreadHeroView(
            project: homeDashboardModel.launchableSelectedProject,
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

    /// iPad / Mac sidebar: the same shell as phone, without the composer
    /// pill. "New task" pushes the hero composer into the detail pane.
    var sidebarDashboard: some View {
        var actions = homeShellActions
        actions.newTask = { project in
            if let project {
                homeDashboardModel.selectProject(project)
            }
            openNewThread()
        }
        return HomeShellView(
            layout: .sidebar,
            model: homeDashboardModel,
            actions: actions,
            openingKey: openingRecentSessionKey,
            onStartTaskOnServer: { server in
                homeDashboardModel.selectedServerId = server.id
                openNewThread()
            }
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
