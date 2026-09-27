import SwiftUI

extension HomeNavigationView {
    var terminalLauncher: (() -> Void)? {
        #if targetEnvironment(macCatalyst)
        return nil
        #else
        guard experimentalFeatures.isEnabled(.terminal) else { return nil }
        return { navigationPath.append(.terminal(preferredAlleycatNodeId: nil)) }
        #endif
    }

    func preferredTerminalWorkingDirectory() -> String? {
        let current = appState.currentCwd.trimmingCharacters(in: .whitespacesAndNewlines)
        if !current.isEmpty { return current }

        let stored = workDir.trimmingCharacters(in: .whitespacesAndNewlines)
        if !stored.isEmpty { return stored }

        return nil
    }

    func remoteShellLauncher(for serverId: String) -> (() -> Void)? {
        guard experimentalFeatures.isEnabled(.terminal),
              let nodeId = savedAlleycatNodeId(for: serverId) else {
            return nil
        }
        return {
            navigationPath.append(.terminal(preferredAlleycatNodeId: nodeId))
        }
    }

    private func savedAlleycatNodeId(for serverId: String) -> String? {
        guard let saved = SavedServerStore.rememberedServers().first(where: { $0.id == serverId }),
              let nodeId = normalizedNonEmpty(saved.alleycatNodeId),
              let token = try? AlleycatCredentialStore.shared.loadToken(nodeId: nodeId),
              !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return nodeId
    }

    private func normalizedNonEmpty(_ value: String?) -> String? {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }
}
