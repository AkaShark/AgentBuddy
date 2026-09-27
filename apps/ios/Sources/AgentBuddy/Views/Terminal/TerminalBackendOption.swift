import Foundation

struct TerminalBackendOption: Identifiable, Hashable {
    let id: String
    let title: String
    let subtitle: String
    let systemImage: String
    let alleycatNodeId: String?
    let supportsResize: Bool
    let runningLabel: String
    let backend: TerminalBackendKind

    static func remoteAlleycat(
        name: String,
        nodeId: String,
        token: String,
        relay: String?
    ) -> TerminalBackendOption {
        TerminalBackendOption(
            id: "alleycat-\(nodeId)",
            title: name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Remote shell" : name,
            subtitle: shortNodeId(nodeId),
            systemImage: "server.rack",
            alleycatNodeId: nodeId,
            supportsResize: true,
            runningLabel: "remote",
            backend: .remoteAlleycat(
                nodeId: nodeId,
                token: token,
                relay: relay,
                shell: nil
            )
        )
    }

    static func remoteSsh(
        name: String,
        host: String,
        port: UInt16,
        username: String,
        auth: TerminalSshAuth
    ) -> TerminalBackendOption {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let title = trimmedName.isEmpty ? "\(username)@\(host)" : trimmedName
        return TerminalBackendOption(
            id: "ssh-\(host.lowercased()):\(port)",
            title: title,
            subtitle: "ssh \(username)@\(host):\(port)",
            systemImage: "terminal.fill",
            alleycatNodeId: nil,
            supportsResize: true,
            runningLabel: "ssh",
            backend: .remoteSsh(
                host: host,
                port: port,
                username: username,
                auth: auth,
                shell: nil,
                acceptUnknownHost: false,
                cwd: nil
            )
        )
    }

    private static func normalized(_ value: String?) -> String? {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func shortNodeId(_ raw: String) -> String {
        raw.count <= 16 ? raw : "\(raw.prefix(8))...\(raw.suffix(8))"
    }
}
