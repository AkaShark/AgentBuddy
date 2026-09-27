import SwiftUI

extension TerminalScreen {
    func initialBackend(
        from options: [TerminalBackendOption],
        cwd: String?
    ) -> TerminalBackendOption? {
        if let preferredNodeId = normalized(preferredAlleycatNodeId),
           let match = options.first(where: { $0.alleycatNodeId == preferredNodeId }) {
            return match
        }
        return options.first
    }

    func selectBackend(_ option: TerminalBackendOption) {
        guard selectedBackendID != option.id else { return }
        selectedBackendID = option.id
        attachOutputSink()
        ghosttyRenderer.clearScreen()
        nativeRendererHasOutput = false
        Task {
            await controller.switchBackend(option.backend)
        }
    }

    private func loadBackendOptions(cwd: String?) -> [TerminalBackendOption] {
        var options: [TerminalBackendOption] = []
        var seenNodeIds = Set<String>()
        var seenSshKeys = Set<String>()
        let savedServers = SavedServerStore.load()
        let savedByNodeId = savedServers.reduce(into: [String: SavedServer]()) { result, saved in
            guard let nodeId = normalized(saved.alleycatNodeId),
                  result[nodeId] == nil else {
                return
            }
            result[nodeId] = saved
        }
        for server in AppModel.shared.snapshot?.servers ?? [] {
            guard let nodeId = alleycatNodeId(fromServerId: server.serverId),
                  seenNodeIds.insert(nodeId).inserted,
                  let token = try? AlleycatCredentialStore.shared.loadToken(nodeId: nodeId),
                  !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                continue
            }
            let saved = savedByNodeId[nodeId]
            options.append(
                TerminalBackendOption.remoteAlleycat(
                    name: normalized(saved?.name) ?? server.displayName,
                    nodeId: nodeId,
                    token: token,
                    relay: normalized(saved?.alleycatRelay)
                )
            )
        }
        // Include non-remembered discovered records too: if they have a
        // terminal-capable credential, the chooser should be able to switch
        // to them while the app still knows about the connection.
        for saved in savedServers {
            if let nodeId = normalized(saved.alleycatNodeId),
               seenNodeIds.insert(nodeId).inserted,
               let token = try? AlleycatCredentialStore.shared.loadToken(nodeId: nodeId),
               !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                options.append(
                    TerminalBackendOption.remoteAlleycat(
                        name: saved.name,
                        nodeId: nodeId,
                        token: token,
                        relay: normalized(saved.alleycatRelay)
                    )
                )
                continue
            }

            let host = saved.hostname
            let sshPort = saved.sshPort ?? 22
            let sshKey = "\(host.lowercased()):\(sshPort)"
            guard !host.isEmpty, seenSshKeys.insert(sshKey).inserted else { continue }
            guard let credential = (try? SSHCredentialStore.shared.load(host: host, port: Int(sshPort))) ?? nil,
                  let sshAuth = Self.terminalSshAuth(from: credential) else {
                continue
            }
            options.append(
                TerminalBackendOption.remoteSsh(
                    name: saved.name,
                    host: host,
                    port: sshPort,
                    username: credential.username,
                    auth: sshAuth
                )
            )
        }
        return options
    }

    @discardableResult
    func refreshBackendOptions() -> [TerminalBackendOption] {
        let options = loadBackendOptions(cwd: cwd)
        backendOptions = options
        return options
    }

    func reconcileBackendOptions() {
        let options = refreshBackendOptions()
        guard let selectedBackendID,
              options.contains(where: { $0.id == selectedBackendID }) else {
            self.selectedBackendID = initialBackend(from: options, cwd: cwd)?.id
            return
        }
    }

    private static func terminalSshAuth(from credential: SavedSSHCredential) -> TerminalSshAuth? {
        switch credential.method {
        case .password:
            guard let password = credential.password, !password.isEmpty else { return nil }
            return .password(password: password)
        case .key:
            guard let key = credential.privateKey, !key.isEmpty else { return nil }
            return .privateKey(keyPem: key, passphrase: credential.passphrase)
        }
    }

    private func normalized(_ value: String?) -> String? {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }

    private func alleycatNodeId(fromServerId serverId: String) -> String? {
        let trimmed = serverId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix(alleycatServerIdPrefix) else { return nil }
        return normalized(String(trimmed.dropFirst(alleycatServerIdPrefix.count)))
    }
}
