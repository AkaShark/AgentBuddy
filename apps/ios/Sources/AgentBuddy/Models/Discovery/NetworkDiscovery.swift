import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class NetworkDiscovery {
    var servers: [DiscoveredServer] = []
    var isScanning = false
    var isInitialLoad = false
    var tailscaleDiscoveryNotice: String?
    /// Overall scan progress from 0.0 to 1.0.
    var scanProgress: Float = 0
    /// Human-readable label for the current scan phase.
    var scanProgressLabel: String?

    @ObservationIgnored private var scanTask: Task<Void, Never>?
    @ObservationIgnored private var initialLoadTask: Task<Void, Never>?
    @ObservationIgnored private var activeScanID = UUID()
    @ObservationIgnored var networkServerLastSeen: [String: Date] = [:]
    @ObservationIgnored let discoveryStore = DiscoveryBridge()

    let cacheKey = "agentbuddy.discovery.networkServers.v1"
    let cacheRetention: TimeInterval = 7 * 24 * 60 * 60

    func startScanning() {
        stopScanning()
        let scanID = UUID()
        activeScanID = scanID
        tailscaleDiscoveryNotice = nil

        let cachedNetworkServers = loadCachedNetworkServers()
        let savedNetworkServers = loadSavedNetworkServers()
        let retainedNetworkServers = servers.filter { $0.source != .local }
        servers = reconcileNetworkServers(cachedNetworkServers + savedNetworkServers + retainedNetworkServers)
        isScanning = true
        isInitialLoad = true
        scanProgress = 0
        scanProgressLabel = "Discovering services…"
        if AgentBuddyPlatform.supportsLocalRuntime {
            servers.append(DiscoveredServer(
                id: "local",
                name: AgentBuddyPlatform.localRuntimeDisplayName(),
                hostname: "127.0.0.1",
                port: nil,
                source: .local,
                hasCodexServer: true
            ))
        }

        initialLoadTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1.2))
            await MainActor.run { [weak self] in
                guard let self, self.activeScanID == scanID else { return }
                self.isInitialLoad = false
            }
        }

        scanTask = Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            await self.discoverNetworkServersInBackground(scanID: scanID)
        }
    }

    func stopScanning() {
        scanTask?.cancel()
        initialLoadTask?.cancel()
        scanTask = nil
        initialLoadTask = nil
        isScanning = false
        isInitialLoad = false
    }

    // MARK: - Discovery

    private nonisolated func discoverNetworkServersInBackground(scanID: UUID) async {
        defer {
            Task { @MainActor [weak self] in
                guard let self, self.activeScanID == scanID else { return }
                self.isScanning = false
                self.isInitialLoad = false
            }
        }
        guard !Task.isCancelled else { return }
        let isCurrent = await MainActor.run { [weak self] in
            guard let self else { return false }
            return self.activeScanID == scanID
        }
        guard isCurrent else { return }

        let store = await MainActor.run { [weak self] in self?.discoveryStore }
        guard let store else { return }

        let tailscaleDiagnostics = TailscaleDiscoveryDiagnostics()
        let tailscaleAppInstalled = await MainActor.run { Self.isTailscaleAppInstalled() }
        async let tailscaleNoticeProbe: Void = Self.probeTailscaleDiscoveryNotice(
            timeout: 1.0,
            appInstalled: tailscaleAppInstalled,
            diagnostics: tailscaleDiagnostics
        )

        let seeds = await Self.discoverBonjourSeeds(timeout: 5.0)
        let localIPv4 = Self.localIPv4Address()?.0
        guard !Task.isCancelled else { return }

        await MainActor.run { [weak self] in
            guard let self, self.activeScanID == scanID else { return }
            self.scanProgress = 0.02
            self.scanProgressLabel = "Scanning network…"
        }

        let subscription = store.scanServersWithMdnsContextProgressive(
            seeds: seeds.map {
                AppMdnsSeed(name: $0.name, host: $0.host, port: $0.port, serviceType: $0.serviceType)
            },
            localIpv4: localIPv4
        )

        do {
            while !Task.isCancelled {
                let update = try await subscription.nextEvent()
                guard !Task.isCancelled else { return }
                await MainActor.run { [weak self] in
                    guard let self, self.activeScanID == scanID else { return }
                    self.isInitialLoad = false
                    self.applyRustDiscoveryResults(update.servers)
                    self.scanProgress = update.progress
                    self.scanProgressLabel = update.progressLabel
                }
                if update.kind == .scanComplete {
                    break
                }
            }
        } catch {
            guard !Task.isCancelled else { return }
        }

        _ = await tailscaleNoticeProbe
        let tailscaleNotice = await tailscaleDiagnostics.notice
        await MainActor.run { [weak self] in
            guard let self, self.activeScanID == scanID else { return }
            self.tailscaleDiscoveryNotice = tailscaleNotice
        }
    }

    private static func discoverBonjourSeeds(timeout: TimeInterval) async -> [BonjourDiscoverySeed] {
        async let ssh = discoverBonjourSeeds(
            serviceType: "_ssh._tcp.",
            timeout: timeout
        )
        async let codex = discoverBonjourSeeds(
            serviceType: "_codex._tcp.",
            timeout: timeout
        )
        return Array((await ssh) + (await codex))
    }

    private static func discoverBonjourSeeds(
        serviceType: String,
        timeout: TimeInterval
    ) async -> [BonjourDiscoverySeed] {
        let browser = await BonjourServiceDiscoverer(serviceType: serviceType)
        return await browser.discover(timeout: timeout)
    }

    nonisolated private static func localIPv4Address() -> (String, String)? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(ifaddr) }

        for ptr in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let flags = Int32(ptr.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_LOOPBACK == 0 else { continue }
            guard ptr.pointee.ifa_addr.pointee.sa_family == UInt8(AF_INET) else { continue }
            let name = String(cString: ptr.pointee.ifa_name)
            guard name.hasPrefix("en") else { continue }
            var buf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            _ = ptr.pointee.ifa_addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { sin in
                inet_ntop(AF_INET, &sin.pointee.sin_addr, &buf, socklen_t(INET_ADDRSTRLEN))
            }
            return (String(cString: buf), name)
        }
        return nil
    }
}
