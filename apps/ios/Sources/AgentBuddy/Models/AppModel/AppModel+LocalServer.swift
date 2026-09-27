import Foundation

extension AppModel {
    /// True if the given `serverId` resolves to a local-server snapshot
    /// entry. Used at every `startThread` call site to gate the generative-UI
    /// dynamic tools (show_widget / visualize_read_me) so remote servers
    /// never see them.
    func isLocalServer(serverId: String) -> Bool {
        snapshot?.servers.first(where: { $0.serverId == serverId })?.isLocal == true
    }

    /// `generativeUiDynamicToolSpecs()` when `serverId` is a local server,
    /// otherwise `nil`. Use this to construct the `dynamicTools` field on
    /// any thread-start request.
    func localGenerativeUiToolSpecs(for serverId: String) -> [AppDynamicToolSpec]? {
        isLocalServer(serverId: serverId) ? generativeUiDynamicToolSpecs() : nil
    }

    func resolvedLocalServerDisplayName() -> String {
        let connectedLocalName = snapshot?.servers
            .first(where: \.isLocal)
            .flatMap { $0.displayName.trimmingCharacters(in: .whitespacesAndNewlines) }

        if let connectedLocalName, !connectedLocalName.isEmpty, connectedLocalName != "This Device" {
            return connectedLocalName
        }

        let savedLocalName = SavedServerStore.load()
            .first(where: { $0.id == "local" || $0.source == .local })
            .flatMap { $0.name.trimmingCharacters(in: .whitespacesAndNewlines) }

        if let savedLocalName, !savedLocalName.isEmpty, savedLocalName != "This Device" {
            return savedLocalName
        }

        return AgentBuddyPlatform.localRuntimeDisplayName()
    }

    func restartLocalServer() async throws {
        let currentLocal = snapshot?.servers.first(where: \.isLocal)
        let serverId = currentLocal?.serverId ?? "local"
        let displayName = resolvedLocalServerDisplayName()
        serverBridge.disconnectServer(serverId: serverId)
        _ = try await serverBridge.connectLocalServer(
            serverId: serverId,
            displayName: displayName,
            host: "127.0.0.1",
            port: 0
        )
        await restoreStoredLocalAuthState(serverId: serverId)
        await refreshSnapshot()
    }
}
