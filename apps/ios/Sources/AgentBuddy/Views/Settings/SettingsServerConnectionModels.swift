import Foundation

struct SettingsSSHReconnectAttempt {
    let server: DiscoveredServer
    let host: String
    let credentials: SSHCredentials
}

enum SettingsServerSheet: Identifiable {
    case add
    case edit(HomeDashboardServer)
    case sshReconnect(DiscoveredServer)

    var id: String {
        switch self {
        case .add:
            return "add"
        case .edit(let server):
            return "edit-\(server.id)"
        case .sshReconnect(let server):
            return "ssh-\(server.id)"
        }
    }
}

enum SettingsServerConnectionMode: String, CaseIterable, Identifiable {
    case local
    case ssh
    case directCodex
    case websocket

    var id: String { rawValue }

    var label: String {
        switch self {
        case .local:
            return "Local"
        case .ssh:
            return "SSH"
        case .directCodex:
            return "Codex"
        case .websocket:
            return "WebSocket"
        }
    }

    var formHeader: String {
        switch self {
        case .local:
            return "Local Runtime"
        case .ssh:
            return "SSH Host"
        case .directCodex:
            return "Codex Server"
        case .websocket:
            return "Codex URL"
        }
    }
}

enum SettingsServerConnectionError: LocalizedError {
    case emptyName
    case emptyHost
    case invalidCodexPort
    case missingCodexPort
    case invalidSSHPort
    case invalidWakeMAC
    case invalidWebsocketURL

    var errorDescription: String? {
        switch self {
        case .emptyName:
            return "Server name cannot be empty."
        case .emptyHost:
            return "Host cannot be empty."
        case .invalidCodexPort, .missingCodexPort:
            return "Codex port must be a valid number."
        case .invalidSSHPort:
            return "SSH port must be a valid number."
        case .invalidWakeMAC:
            return "Wake MAC must look like aa:bb:cc:dd:ee:ff."
        case .invalidWebsocketURL:
            return "Enter a valid ws:// or wss:// URL."
        }
    }
}

struct SettingsServerConnectionConfiguration {
    let savedServer: SavedServer
    let discoveredServer: DiscoveredServer
    let connectionMode: SettingsServerConnectionMode
}
