import Foundation

extension NetworkDiscovery {
    func applyRustDiscoveryResults(_ discovered: [AppDiscoveredServer]) {
        let now = Date()
        let metadataSources = loadSavedNetworkServers() + servers.filter { $0.source != .local }
        var existingByKey: [String: DiscoveredServer] = [:]
        for server in metadataSources {
            existingByKey[server.deduplicationKey] = server
        }
        let resolved = discovered.compactMap { rust -> DiscoveredServer? in
            let existing = existingByKey[Self.normalizedServerKey(for: rust.host)]
            guard let server = Self.discoveredServer(from: rust, existing: existing) else {
                return nil
            }
            networkServerLastSeen[server.id] = now
            return server
        }

        let local = servers.filter { $0.source == .local }
        servers = local + reconcileNetworkServers(resolved + metadataSources)
        saveCachedNetworkServers()
    }

    private static func discoveredServer(
        from rust: AppDiscoveredServer,
        existing: DiscoveredServer?
    ) -> DiscoveredServer? {
        let host = rust.host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty, host != "127.0.0.1" else { return nil }

        let id = rust.id.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = rust.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        return DiscoveredServer(
            id: id.isEmpty ? "network-\(host)" : id,
            name: name.isEmpty ? host : name,
            hostname: host,
            port: rust.codexPort,
            codexPorts: rust.codexPorts,
            sshPort: rust.sshPort,
            source: ServerSource(rust.source),
            hasCodexServer: rust.codexPort != nil || !rust.codexPorts.isEmpty,
            wakeMAC: existing?.wakeMAC,
            sshPortForwardingEnabled: false,
            websocketURL: existing?.websocketURL,
            preferredConnectionMode: existing?.preferredConnectionMode,
            preferredCodexPort: existing?.preferredCodexPort,
            os: rust.sshBanner != nil ? rust.os : (rust.os ?? existing?.os),
            sshBanner: rust.sshBanner ?? existing?.sshBanner
        )
    }

    func reconcileNetworkServers(_ candidates: [DiscoveredServer]) -> [DiscoveredServer] {
        var existingByKey: [String: DiscoveredServer] = [:]
        for server in candidates where server.source != .local {
            existingByKey[server.deduplicationKey] = server
        }
        return discoveryStore
            .reconcileServers(
                candidates: candidates
                    .filter { $0.source != .local }
                    .map(Self.ffiDiscoveredServer(from:))
            )
            .compactMap { rust in
                Self.discoveredServer(
                    from: rust,
                    existing: existingByKey[Self.normalizedServerKey(for: rust.host)]
                )
            }
    }

    func loadSavedNetworkServers() -> [DiscoveredServer] {
        SavedServerStore.load()
            .map { $0.toDiscoveredServer() }
            .filter { $0.source != .local }
    }

    private static func normalizedServerKey(for host: String) -> String {
        var normalized = host
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
            .replacingOccurrences(of: "%25", with: "%")

        if !normalized.contains(":"), let scopeIndex = normalized.firstIndex(of: "%") {
            normalized = String(normalized[..<scopeIndex])
        }

        return normalized.lowercased()
    }

    private static func ffiDiscoveredServer(from server: DiscoveredServer) -> AppDiscoveredServer {
        AppDiscoveredServer(
            id: server.id,
            displayName: server.name,
            host: server.hostname,
            port: server.port ?? server.resolvedSSHPort,
            codexPort: server.port,
            codexPorts: server.codexPorts,
            sshPort: server.sshPort,
            source: {
                switch server.source {
                case .local:
                    return .local
                case .bonjour:
                    return .bonjour
                case .ssh:
                    return .manual
                case .tailscale:
                    return .tailscale
                case .manual:
                    return .manual
                }
            }(),
            reachable: server.hasCodexServer || server.sshPort != nil,
            os: server.os,
            sshBanner: server.sshBanner
        )
    }
}
