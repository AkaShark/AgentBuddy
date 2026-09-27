import Foundation

extension NetworkDiscovery {
    nonisolated fileprivate static func ipv4Address(fromSockaddrData data: Data) -> String? {
        data.withUnsafeBytes { bytes in
            guard let base = bytes.baseAddress else { return nil }
            let sockaddrPtr = base.assumingMemoryBound(to: sockaddr.self)
            guard sockaddrPtr.pointee.sa_family == sa_family_t(AF_INET) else { return nil }
            let sinPtr = base.assumingMemoryBound(to: sockaddr_in.self)
            var addr = sinPtr.pointee.sin_addr
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            guard inet_ntop(AF_INET, &addr, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else {
                return nil
            }
            return String(cString: buffer)
        }
    }
}

@MainActor
/// Resolved Bonjour service — name, host (first IPv4), and port. Shared
/// shape for `_codex._tcp`, `_ssh._tcp`, and `_litter-pair._tcp.` clients.
struct BonjourResolvedService: Hashable {
    let name: String
    let host: String
    let port: UInt16
    let serviceType: String
}

@MainActor
final class BonjourServiceDiscoverer: NSObject, @preconcurrency NetServiceBrowserDelegate, @preconcurrency NetServiceDelegate {
    private struct ServiceRecord {
        let name: String
        let port: UInt16?
    }

    private let serviceType: String
    private let browser = NetServiceBrowser()
    private var services: [NetService] = []
    private var results: [String: ServiceRecord] = [:]
    private var pendingServices: Set<ObjectIdentifier> = []
    private var continuation: CheckedContinuation<[BonjourDiscoverySeed], Never>?
    private var timeoutTask: Task<Void, Never>?
    private var resolveDrainTask: Task<Void, Never>?
    private var isFinished = false
    private var requestedStop = false

    init(serviceType: String) {
        self.serviceType = serviceType
    }

    func discover(timeout: TimeInterval) async -> [BonjourDiscoverySeed] {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            browser.delegate = self
            browser.searchForServices(ofType: serviceType, inDomain: "local.")
            timeoutTask = Task { [weak self] in
                guard let self else { return }
                let nanos = UInt64(max(timeout, 0.25) * 1_000_000_000)
                try? await Task.sleep(nanoseconds: nanos)
                self.stopAndDrain()
            }
        }
    }

    private func stopAndDrain() {
        guard !requestedStop else { return }
        requestedStop = true
        browser.stop()
        if pendingServices.isEmpty {
            finish()
            return
        }
        resolveDrainTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(for: .milliseconds(900))
            self.finish()
        }
    }

    private func finish() {
        // Consume the continuation atomically: capture + nil-out before any
        // delegate callbacks (browser.stop() can re-fire didStopSearch and
        // re-enter finish before we'd otherwise clear the field). Any later
        // re-entry sees `continuation == nil` and bails harmlessly, which
        // dodges the `tried to resume more than once` crash.
        guard let continuation = self.continuation else { return }
        self.continuation = nil
        isFinished = true
        timeoutTask?.cancel()
        resolveDrainTask?.cancel()
        timeoutTask = nil
        resolveDrainTask = nil
        if !requestedStop {
            browser.stop()
        }
        for service in services {
            service.stop()
            service.delegate = nil
        }
        let discovered = results.map {
            BonjourDiscoverySeed(
                name: $0.value.name,
                host: $0.key,
                port: $0.value.port,
                serviceType: serviceType
            )
        }
        continuation.resume(returning: discovered)
    }

    func netServiceBrowserWillSearch(_ browser: NetServiceBrowser) {}

    func netServiceBrowser(_ browser: NetServiceBrowser, didNotSearch errorDict: [String: NSNumber]) {
        finish()
    }

    func netServiceBrowserDidStopSearch(_ browser: NetServiceBrowser) {
        if requestedStop, pendingServices.isEmpty {
            finish()
        } else if !requestedStop {
            finish()
        }
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        guard !isFinished else { return }
        services.append(service)
        pendingServices.insert(ObjectIdentifier(service))
        service.delegate = self
        service.resolve(withTimeout: 2.5)
    }

    func netServiceDidResolveAddress(_ sender: NetService) {
        pendingServices.remove(ObjectIdentifier(sender))
        guard let addresses = sender.addresses else { return }
        let resolvedPort: UInt16? = {
            guard sender.port > 0, sender.port <= Int(UInt16.max) else { return nil }
            return UInt16(sender.port)
        }()
        for address in addresses {
            guard let ip = NetworkDiscovery.ipv4Address(fromSockaddrData: address) else { continue }
            results[ip] = ServiceRecord(name: sender.name, port: resolvedPort)
            break
        }
        if requestedStop, pendingServices.isEmpty {
            finish()
        }
    }

    func netService(_ sender: NetService, didNotResolve errorDict: [String: NSNumber]) {
        pendingServices.remove(ObjectIdentifier(sender))
        if requestedStop, pendingServices.isEmpty {
            finish()
        }
    }
}
