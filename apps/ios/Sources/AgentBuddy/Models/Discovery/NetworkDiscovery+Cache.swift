import Foundation

extension NetworkDiscovery {
    private struct CachedNetworkServer: Codable {
        let id: String
        let name: String
        let hostname: String
        let port: UInt16?
        let codexPorts: [UInt16]?
        let sshPort: UInt16?
        let source: ServerSource
        let hasCodexServer: Bool
        let wakeMAC: String?
        let preferredConnectionMode: PreferredConnectionMode?
        let preferredCodexPort: UInt16?
        let lastSeenAt: TimeInterval
        let os: String?
        let sshBanner: String?
    }

    func loadCachedNetworkServers() -> [DiscoveredServer] {
        guard let data = UserDefaults.standard.data(forKey: cacheKey) else { return [] }
        let decoder = JSONDecoder()
        guard let cached = try? decoder.decode([CachedNetworkServer].self, from: data) else {
            UserDefaults.standard.removeObject(forKey: cacheKey)
            return []
        }

        let now = Date()
        let maxAge = cacheRetention
        var pruned: [CachedNetworkServer] = []
        var loaded: [DiscoveredServer] = []
        networkServerLastSeen.removeAll(keepingCapacity: true)

        for entry in cached {
            guard now.timeIntervalSince1970 - entry.lastSeenAt <= maxAge else { continue }
            guard entry.source != .local else { continue }
            let server = DiscoveredServer(
                id: entry.id,
                name: entry.name,
                hostname: entry.hostname,
                port: entry.port,
                codexPorts: entry.codexPorts ?? (entry.port.map { [ $0 ] } ?? []),
                sshPort: entry.sshPort,
                source: entry.source,
                hasCodexServer: entry.hasCodexServer,
                wakeMAC: entry.wakeMAC,
                preferredConnectionMode: entry.preferredConnectionMode,
                preferredCodexPort: entry.preferredCodexPort,
                os: entry.os,
                sshBanner: entry.sshBanner
            )
            loaded.append(server)
            pruned.append(entry)
            networkServerLastSeen[entry.id] = Date(timeIntervalSince1970: entry.lastSeenAt)
        }

        if pruned.count != cached.count {
            persistCachedNetworkServers(pruned)
        }

        return loaded
    }

    func saveCachedNetworkServers() {
        let now = Date()
        let cached = servers
            .filter { $0.source != .local }
            .map { server in
                let lastSeen = networkServerLastSeen[server.id] ?? now
                return CachedNetworkServer(
                    id: server.id,
                    name: server.name,
                    hostname: server.hostname,
                    port: server.port,
                    codexPorts: server.codexPorts,
                    sshPort: server.sshPort,
                    source: server.source,
                    hasCodexServer: server.hasCodexServer,
                    wakeMAC: server.wakeMAC,
                    preferredConnectionMode: server.preferredConnectionMode,
                    preferredCodexPort: server.preferredCodexPort,
                    lastSeenAt: lastSeen.timeIntervalSince1970,
                    os: server.os,
                    sshBanner: server.sshBanner
                )
            }
        persistCachedNetworkServers(cached)
    }

    private func persistCachedNetworkServers(_ cached: [CachedNetworkServer]) {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(cached) else { return }
        UserDefaults.standard.set(data, forKey: cacheKey)
    }
}
