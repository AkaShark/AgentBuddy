import Foundation
import UIKit

private struct TailscaleInterfaceSnapshot: Sendable {
    struct InterfaceRecord: Sendable {
        let name: String
        let family: String
        let address: String
        let flags: [String]
        let isTailscaleAddress: Bool
    }

    let localWiFiAddress: String?
    let localWiFiInterface: String?
    let activeTunnelInterfaces: [String]
    let tailscaleInterfaces: [String]
    let records: [InterfaceRecord]

    var hasLikelyActiveTailscaleTunnel: Bool {
        !activeTunnelInterfaces.isEmpty && !tailscaleInterfaces.isEmpty
    }

    var logDescription: String {
        let wifiSummary: String
        if let localWiFiInterface, let localWiFiAddress {
            wifiSummary = "\(localWiFiInterface)=\(localWiFiAddress)"
        } else {
            wifiSummary = "none"
        }

        let tunnelSummary = activeTunnelInterfaces.isEmpty
            ? "none"
            : activeTunnelInterfaces.joined(separator: ",")
        let tailscaleSummary = tailscaleInterfaces.isEmpty
            ? "none"
            : tailscaleInterfaces.joined(separator: ",")
        let recordsSummary = records.isEmpty
            ? "none"
            : records.map { record in
                let flags = record.flags.joined(separator: "+")
                return "\(record.name):\(record.family):\(record.address):\(flags)\(record.isTailscaleAddress ? ":tailscale" : "")"
            }.joined(separator: " | ")

        return "wifi=\(wifiSummary) likelyActive=\(hasLikelyActiveTailscaleTunnel) utun=\(tunnelSummary) tailscale=\(tailscaleSummary) records=\(recordsSummary)"
    }
}

actor TailscaleDiscoveryDiagnostics {
    private(set) var notice: String?

    func markSuccess() {
        notice = nil
    }

    func record(_ notice: String) {
        if self.notice == nil {
            self.notice = notice
        }
    }
}

extension NetworkDiscovery {
    nonisolated static func parseTailscalePeerCandidates(
        data: Data,
        response: URLResponse
    ) throws -> [TailscalePeerIdentity] {
        guard let http = response as? HTTPURLResponse,
              (200...299).contains(http.statusCode) else {
            throw TailscalePeerParseError.invalidPayload
        }

        let contentType = http.value(forHTTPHeaderField: "Content-Type")?.lowercased()
        let preview = String(decoding: data.prefix(128), as: UTF8.self)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if contentType?.contains("text/html") == true ||
            preview.hasPrefix("<!doctype html") ||
            preview.hasPrefix("<html") {
            throw TailscalePeerParseError.unsupportedSurface
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let peers = json["Peer"] as? [String: Any] else {
            throw TailscalePeerParseError.invalidPayload
        }

        var out: [TailscalePeerIdentity] = []
        out.reserveCapacity(peers.count)
        for peer in peers.values {
            guard let peerDict = peer as? [String: Any] else { continue }
            if let online = peerDict["Online"] as? Bool, !online {
                continue
            }
            let hostName = cleanedHostName(peerDict["HostName"] as? String)
                ?? cleanedHostName(peerDict["DNSName"] as? String)
            let ips = (peerDict["TailscaleIPs"] as? [String]) ?? []
            guard let ipv4 = ips.first(where: { isIPv4Address($0) }) else { continue }
            out.append(TailscalePeerIdentity(ip: ipv4, name: hostName))
        }

        return out
    }

    nonisolated static func tailscaleDiscoveryNotice(
        for error: Error,
        availability: TailscaleAvailability
    ) -> String? {
        guard availability.shouldSurfaceDiscoveryNotice else {
            return nil
        }

        if let urlError = error as? URLError, urlError.code == .timedOut {
            return "Tailscale peer discovery timed out. Add a server manually with its MagicDNS name or Tailscale IP. Saved servers will still appear here."
        }

        if let parseError = error as? TailscalePeerParseError, parseError == .unsupportedSurface {
            return "Tailscale returned its web UI instead of a peer list, so peer discovery is unavailable here. Add a server manually with its MagicDNS name or Tailscale IP. Saved servers will still appear here."
        }

        return "Tailscale peer discovery is unavailable right now. Add a server manually with its MagicDNS name or Tailscale IP. Saved servers will still appear here."
    }

    nonisolated static func probeTailscaleDiscoveryNotice(
        timeout: TimeInterval,
        appInstalled: Bool,
        diagnostics: TailscaleDiscoveryDiagnostics
    ) async {
        guard let url = URL(string: "http://100.100.100.100/localapi/v0/status") else {
            return
        }

        let interfaceSnapshot = tailscaleInterfaceSnapshot()
        let availability = TailscaleAvailability(
            appInstalled: appInstalled,
            likelyActiveTunnel: interfaceSnapshot.hasLikelyActiveTailscaleTunnel
        )
        NSLog(
            "[tailscale] availability=%@ interface snapshot before request: %@",
            availability.logDescription,
            interfaceSnapshot.logDescription
        )

        let configuration = URLSessionConfiguration.ephemeral
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = timeout + 0.25
        configuration.waitsForConnectivity = false
        configuration.urlCache = nil
        let session = URLSession(configuration: configuration)

        var request = URLRequest(url: url)
        request.timeoutInterval = timeout
        request.cachePolicy = .reloadIgnoringLocalCacheData

        do {
            let (data, response) = try await session.data(for: request)
            if let http = response as? HTTPURLResponse {
                let contentType = http.value(forHTTPHeaderField: "Content-Type") ?? "unknown"
                NSLog("[tailscale] response status=%d contentType=%@", http.statusCode, contentType)
            }
            let peers = try parseTailscalePeerCandidates(data: data, response: response)
            await diagnostics.markSuccess()
            NSLog("[tailscale] got %d peers", peers.count)
        } catch {
            let responsePreview = (error as NSError).localizedDescription
            if let notice = Self.tailscaleDiscoveryNotice(for: error, availability: availability) {
                await diagnostics.record(notice)
            } else {
                NSLog("[tailscale] suppressing notice because Tailscale does not look installed or active")
            }
            NSLog("[tailscale] request error: %@", responsePreview)
            NSLog("[tailscale] interface snapshot after error: %@", tailscaleInterfaceSnapshot().logDescription)
        }
    }

    @MainActor
    static func isTailscaleAppInstalled() -> Bool {
        guard let url = URL(string: "tailscale://") else { return false }
        return UIApplication.shared.canOpenURL(url)
    }

    nonisolated private static func cleanedHostName(_ value: String?) -> String? {
        guard var value, !value.isEmpty else { return nil }
        if value.hasSuffix(".") {
            value.removeLast()
        }
        if value.hasSuffix(".local") {
            value = String(value.dropLast(6))
        }
        return value.isEmpty ? nil : value
    }

    nonisolated private static func isIPv4Address(_ value: String) -> Bool {
        var addr = in_addr()
        return value.withCString { cstr in
            inet_pton(AF_INET, cstr, &addr) == 1
        }
    }

    nonisolated private static func isTailscaleIPv4Address(_ value: String) -> Bool {
        let octets = value.split(separator: ".")
        guard octets.count == 4,
              let first = Int(octets[0]),
              let second = Int(octets[1]) else {
            return false
        }
        return first == 100 && (64...127).contains(second)
    }

    nonisolated private static func isTailscaleIPv6Address(_ value: String) -> Bool {
        value.lowercased().hasPrefix("fd7a:115c:a1e0:")
    }

    nonisolated private static func interfaceFlagDescriptions(_ flags: Int32) -> [String] {
        var out: [String] = []
        if flags & IFF_UP != 0 { out.append("up") }
        if flags & IFF_RUNNING != 0 { out.append("running") }
        if flags & IFF_LOOPBACK != 0 { out.append("loopback") }
        if flags & IFF_POINTOPOINT != 0 { out.append("ptp") }
        if flags & IFF_MULTICAST != 0 { out.append("multicast") }
        return out
    }

    nonisolated private static func ipAddress(fromSockaddr pointer: UnsafePointer<sockaddr>) -> (family: String, address: String)? {
        let family = pointer.pointee.sa_family
        switch family {
        case sa_family_t(AF_INET):
            let sinPtr = UnsafeRawPointer(pointer).assumingMemoryBound(to: sockaddr_in.self)
            var addr = sinPtr.pointee.sin_addr
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            guard inet_ntop(AF_INET, &addr, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else {
                return nil
            }
            return ("ipv4", String(cString: buffer))
        case sa_family_t(AF_INET6):
            let sin6Ptr = UnsafeRawPointer(pointer).assumingMemoryBound(to: sockaddr_in6.self)
            var addr = sin6Ptr.pointee.sin6_addr
            var buffer = [CChar](repeating: 0, count: Int(INET6_ADDRSTRLEN))
            guard inet_ntop(AF_INET6, &addr, &buffer, socklen_t(INET6_ADDRSTRLEN)) != nil else {
                return nil
            }
            return ("ipv6", String(cString: buffer))
        default:
            return nil
        }
    }

    nonisolated private static func tailscaleInterfaceSnapshot() -> TailscaleInterfaceSnapshot {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else {
            return TailscaleInterfaceSnapshot(
                localWiFiAddress: nil,
                localWiFiInterface: nil,
                activeTunnelInterfaces: [],
                tailscaleInterfaces: [],
                records: []
            )
        }
        defer { freeifaddrs(ifaddr) }

        var localWiFiAddress: String?
        var localWiFiInterface: String?
        var activeTunnelInterfaces = Set<String>()
        var tailscaleInterfaces = Set<String>()
        var records: [TailscaleInterfaceSnapshot.InterfaceRecord] = []

        for ptr in sequence(first: first, next: { $0.pointee.ifa_next }) {
            guard let sockaddr = ptr.pointee.ifa_addr else { continue }
            guard let entry = ipAddress(fromSockaddr: sockaddr) else { continue }

            let name = String(cString: ptr.pointee.ifa_name)
            let flags = Int32(ptr.pointee.ifa_flags)
            let flagDescriptions = interfaceFlagDescriptions(flags)
            let isUp = flags & IFF_UP != 0
            let isLoopback = flags & IFF_LOOPBACK != 0
            let isTunnel = name.hasPrefix("utun")
            let hasTailscaleAddress = isTailscaleIPv4Address(entry.address) || isTailscaleIPv6Address(entry.address)
            let isLikelyTailscaleInterface = isTunnel && hasTailscaleAddress

            if isUp && isTunnel {
                activeTunnelInterfaces.insert(name)
            }
            if isLikelyTailscaleInterface {
                tailscaleInterfaces.insert(name)
            }
            if isUp && !isLoopback && localWiFiAddress == nil && entry.family == "ipv4" && name.hasPrefix("en") {
                localWiFiInterface = name
                localWiFiAddress = entry.address
            }

            if isTunnel || isLikelyTailscaleInterface || (isUp && !isLoopback) {
                records.append(
                    TailscaleInterfaceSnapshot.InterfaceRecord(
                        name: name,
                        family: entry.family,
                        address: entry.address,
                        flags: flagDescriptions,
                        isTailscaleAddress: isLikelyTailscaleInterface
                    )
                )
            }
        }

        records.sort { lhs, rhs in
            if lhs.name != rhs.name {
                return lhs.name < rhs.name
            }
            if lhs.family != rhs.family {
                return lhs.family < rhs.family
            }
            return lhs.address < rhs.address
        }

        return TailscaleInterfaceSnapshot(
            localWiFiAddress: localWiFiAddress,
            localWiFiInterface: localWiFiInterface,
            activeTunnelInterfaces: activeTunnelInterfaces.sorted(),
            tailscaleInterfaces: tailscaleInterfaces.sorted(),
            records: records
        )
    }
}
