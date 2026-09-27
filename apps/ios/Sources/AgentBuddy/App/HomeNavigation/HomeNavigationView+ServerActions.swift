import SwiftUI

extension HomeNavigationView {
    func handleSelectServer(_ server: HomeDashboardServer) {
        guard server.canLaunchSessions else {
            reconnectServer(server)
            return
        }
        if homeDashboardModel.selectedServerId == server.id {
            homeDashboardModel.clearScope()
        } else {
            homeDashboardModel.selectedServerId = server.id
        }
    }

    func reconnectServer(_ server: HomeDashboardServer) {
        Task {
            await AppRuntimeController.shared.reconnectServer(serverId: server.id)
        }
    }

    func restartAppServer(_ server: HomeDashboardServer) {
        Task {
            do {
                if server.isLocal {
                    try await appModel.restartLocalServer()
                } else {
                    try await appModel.serverBridge.restartAppServer(serverId: server.id)
                    await AppRuntimeController.shared.reconnectServer(serverId: server.id)
                }
                await appModel.refreshSnapshot()
            } catch {
                actionErrorMessage = error.localizedDescription
            }
        }
    }

    func disconnectServer(_ serverId: String) {
        SavedServerStore.remove(serverId: serverId)
        Task { await SshSessionStore.shared.close(serverId: serverId, ssh: appModel.ssh) }
        // Remote transport resources are owned by the Rust `ServerSession` and
        // dropped automatically inside `serverBridge.disconnectServer`.
        appModel.serverBridge.disconnectServer(serverId: serverId)
    }

    func renameServer(_ serverId: String, newName: String) {
        SavedServerStore.rename(serverId: serverId, newName: newName)
        appModel.reconnectController.setMultiClankerAndQuicEnabled(enabled: true)
        appModel.reconnectController.syncSavedServers(
            servers: SavedServerStore.reconnectRecords(
                localDisplayName: appModel.resolvedLocalServerDisplayName()
            )
        )
        appModel.store.renameServer(serverId: serverId, displayName: newName)
    }
}
