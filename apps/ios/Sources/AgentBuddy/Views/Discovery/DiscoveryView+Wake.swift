import Network
import SwiftUI

extension DiscoveryView {
    func prepareServerForSelection(_ server: DiscoveredServer) async -> (server: DiscoveredServer, canAttemptSSH: Bool) {
        guard server.source != .local else {
            return (server, true)
        }

        wakingServer = server
        defer { wakingServer = nil }

        let wakeResult = await waitForWakeSignal(
            host: server.hostname,
            preferredCodexPort: server.hasCodexServer ? server.port : nil,
            preferredSSHPort: server.sshPort,
            timeout: server.hasCodexServer ? 12.0 : 18.0,
            wakeMAC: server.wakeMAC
        )

        switch wakeResult {
        case .codex(let port):
            return (
                DiscoveredServer(
                    id: server.id,
                    name: server.name,
                    hostname: server.hostname,
                    port: port,
                    codexPorts: [port] + server.codexPorts.filter { $0 != port },
                    sshPort: server.sshPort,
                    source: server.source,
                    hasCodexServer: true,
                    wakeMAC: server.wakeMAC,
                    sshPortForwardingEnabled: server.sshPortForwardingEnabled,
                    preferredConnectionMode: server.preferredConnectionMode,
                    preferredCodexPort: port
                ),
                true
            )
        case .ssh(let sshPort):
            return (
                DiscoveredServer(
                    id: server.id,
                    name: server.name,
                    hostname: server.hostname,
                    port: nil,
                    codexPorts: server.codexPorts,
                    sshPort: sshPort,
                    source: server.source,
                    hasCodexServer: false,
                    wakeMAC: server.wakeMAC,
                    sshPortForwardingEnabled: server.sshPortForwardingEnabled,
                    preferredConnectionMode: .ssh
                ),
                true
            )
        case .none:
            // Don't hard-block when wake probing is inconclusive; continue with
            // normal connect/SSH flow so users can still attempt recovery.
            return (server, true)
        }
    }

    private enum WakeSignalResult {
        case codex(UInt16)
        case ssh(UInt16)
        case none
    }

    private func waitForWakeSignal(
        host: String,
        preferredCodexPort: UInt16?,
        preferredSSHPort: UInt16?,
        timeout: TimeInterval,
        wakeMAC: String?
    ) async -> WakeSignalResult {
        let codexPorts = orderedCodexPorts(preferred: preferredCodexPort)
        let sshPorts = orderedSSHPorts(preferred: preferredSSHPort)
        let deadline = Date().addingTimeInterval(max(timeout, 0.5))
        var lastWakePacketAt = Date.distantPast

        while Date() < deadline {
            if let wakeMAC, Date().timeIntervalSince(lastWakePacketAt) >= 2.0 {
                sendWakeMagicPacket(to: wakeMAC, hostHint: host)
                lastWakePacketAt = Date()
            }

            for port in codexPorts {
                if await isPortOpen(host: host, port: port, timeout: 0.7) {
                    return .codex(port)
                }
            }

            for port in sshPorts {
                if await isPortOpen(host: host, port: port, timeout: 0.7) {
                    return .ssh(port)
                }
            }

            try? await Task.sleep(for: .milliseconds(350))
        }

        return .none
    }

    private func orderedCodexPorts(preferred: UInt16?) -> [UInt16] {
        var ports = [UInt16]()
        if let preferred {
            ports.append(preferred)
        }
        ports.append(contentsOf: [8390, 9234, 4222])

        var seen = Set<UInt16>()
        return ports.filter { seen.insert($0).inserted }
    }

    private func orderedSSHPorts(preferred: UInt16?) -> [UInt16] {
        var ports = [UInt16]()
        if let preferred {
            ports.append(preferred)
        }
        ports.append(22)

        var seen = Set<UInt16>()
        return ports.filter { seen.insert($0).inserted }
    }

    private func sendWakeMagicPacket(to wakeMAC: String, hostHint: String) {
        guard let macBytes = macBytes(from: wakeMAC) else { return }
        var packet = Data(repeating: 0xFF, count: 6)
        for _ in 0..<16 {
            packet.append(contentsOf: macBytes)
        }

        let targets = wakeBroadcastTargets(for: hostHint)
        for target in targets {
            sendBroadcastUDP(packet: packet, host: target, port: 9)
            sendBroadcastUDP(packet: packet, host: target, port: 7)
        }
    }

    private func macBytes(from normalizedMAC: String) -> [UInt8]? {
        let compact = normalizedMAC.replacingOccurrences(of: ":", with: "")
        guard compact.count == 12 else { return nil }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(6)
        var index = compact.startIndex
        for _ in 0..<6 {
            let next = compact.index(index, offsetBy: 2)
            let chunk = compact[index..<next]
            guard let byte = UInt8(chunk, radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        return bytes
    }

    private func wakeBroadcastTargets(for host: String) -> [String] {
        var targets = ["255.255.255.255"]
        let parts = host.split(separator: ".")
        if parts.count == 4,
           let _ = Int(parts[0]),
           let _ = Int(parts[1]),
           let _ = Int(parts[2]),
           let _ = Int(parts[3]) {
            targets.append("\(parts[0]).\(parts[1]).\(parts[2]).255")
        }
        return Array(Set(targets))
    }

    private func sendBroadcastUDP(packet: Data, host: String, port: UInt16) {
        let fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        guard fd >= 0 else { return }
        defer { close(fd) }

        var enabled: Int32 = 1
        withUnsafePointer(to: &enabled) { enabledPtr in
            _ = setsockopt(fd, SOL_SOCKET, SO_BROADCAST, enabledPtr, socklen_t(MemoryLayout<Int32>.size))
        }

        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = CFSwapInt16HostToBig(port)
        host.withCString { cString in
            _ = inet_pton(AF_INET, cString, &addr.sin_addr)
        }

        packet.withUnsafeBytes { bytes in
            guard let base = bytes.baseAddress else { return }
            var destination = addr
            withUnsafePointer(to: &destination) { destinationPtr in
                destinationPtr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                    _ = sendto(fd, base, packet.count, 0, sockPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }
        }
    }

    private func isPortOpen(host: String, port: UInt16, timeout: TimeInterval) async -> Bool {
        await withCheckedContinuation { continuation in
            guard let endpointPort = NWEndpoint.Port(rawValue: port) else {
                continuation.resume(returning: false)
                return
            }

            let connection = NWConnection(host: NWEndpoint.Host(host), port: endpointPort, using: .tcp)
            let gate = WakeProbeResumeGate()

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if gate.markResumed() {
                        connection.stateUpdateHandler = nil
                        connection.cancel()
                        continuation.resume(returning: true)
                    }
                case .failed, .cancelled:
                    if gate.markResumed() {
                        connection.stateUpdateHandler = nil
                        connection.cancel()
                        continuation.resume(returning: false)
                    }
                default:
                    break
                }
            }

            connection.start(queue: .global(qos: .utility))

            DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + timeout) {
                if gate.markResumed() {
                    connection.stateUpdateHandler = nil
                    connection.cancel()
                    continuation.resume(returning: false)
                }
            }
        }
    }
}

private final class WakeProbeResumeGate: @unchecked Sendable {
    private let lock = NSLock()
    private var resumed = false

    func markResumed() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if resumed {
            return false
        }
        resumed = true
        return true
    }
}
