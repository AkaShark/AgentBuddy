import SwiftUI

struct SettingsServerConnectionEditor: View {
    let server: HomeDashboardServer
    let onSave: (SettingsServerConnectionConfiguration) -> Void
    let onReconnect: (SettingsServerConnectionConfiguration) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var displayName: String
    @State private var connectionMode: SettingsServerConnectionMode
    @State private var host: String
    @State private var codexPort: String
    @State private var websocketURL: String
    @State private var sshPort: String
    @State private var wakeMAC: String
    @State private var validationError: String?

    private let originalSavedServer: SavedServer?

    @MainActor
    init(
        server: HomeDashboardServer,
        onSave: @escaping (SettingsServerConnectionConfiguration) -> Void,
        onReconnect: @escaping (SettingsServerConnectionConfiguration) -> Void
    ) {
        self.server = server
        self.onSave = onSave
        self.onReconnect = onReconnect

        let saved = SavedServerStore.load().first { $0.id == server.id }
        self.originalSavedServer = saved

        let resolvedMode: SettingsServerConnectionMode
        if server.isLocal {
            resolvedMode = .local
        } else if saved?.websocketURL != nil {
            resolvedMode = .websocket
        } else if saved?.preferredConnectionMode == .ssh || saved?.sshPort != nil && saved?.hasCodexServer == false {
            resolvedMode = .ssh
        } else {
            resolvedMode = .directCodex
        }

        let name = saved?.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedHost = saved?.hostname.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedCodexPort = saved?.preferredCodexPort ?? saved?.port ?? (server.port == 0 ? nil : server.port)
        let resolvedSSHPort = saved?.sshPort ?? (resolvedMode == .ssh ? server.port : nil) ?? 22

        _displayName = State(initialValue: name?.isEmpty == false ? name! : server.displayName)
        _connectionMode = State(initialValue: resolvedMode)
        _host = State(initialValue: resolvedHost?.isEmpty == false ? resolvedHost! : server.host)
        _codexPort = State(initialValue: resolvedCodexPort.map(String.init) ?? "8390")
        _websocketURL = State(initialValue: saved?.websocketURL ?? "")
        _sshPort = State(initialValue: String(resolvedSSHPort))
        _wakeMAC = State(initialValue: saved?.wakeMAC ?? "")
    }

    private var availableModes: [SettingsServerConnectionMode] {
        server.isLocal ? [.local] : [.ssh, .directCodex, .websocket]
    }

    private var isSpecialPairedServer: Bool {
        originalSavedServer?.alleycatNodeId != nil || originalSavedServer?.alleycatAgentWire == "ssh-bridge"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    nameSection
                    connectionSection
                    actionSection
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Edit Server")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
            .alert("Invalid Server", isPresented: Binding(
                get: { validationError != nil },
                set: { if !$0 { validationError = nil } }
            )) {
                Button("OK") { validationError = nil }
            } message: {
                Text(validationError ?? "Check the server details.")
            }
        }
    }

    private var nameSection: some View {
        Section {
            TextField("Server name", text: $displayName)
                .agentBuddyFont(.footnote)
                .foregroundColor(AgentBuddyTheme.textPrimary)
        } header: {
            Text("Name")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    private var connectionSection: some View {
        Section {
            if isSpecialPairedServer {
                Text("This paired server uses saved pairing metadata. Edit its display name here, or remove and add it again to change the pairing.")
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            } else if connectionMode == .local {
                Text("This device's local runtime is managed automatically.")
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            } else {
                Picker("Connection Type", selection: $connectionMode) {
                    ForEach(availableModes) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                .pickerStyle(.segmented)

                switch connectionMode {
                case .local:
                    EmptyView()
                case .ssh:
                    hostField
                    TextField("ssh port", text: $sshPort)
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                        .keyboardType(.numberPad)
                    TextField("wake MAC (optional)", text: $wakeMAC)
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                case .directCodex:
                    hostField
                    TextField("codex port", text: $codexPort)
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                        .keyboardType(.numberPad)
                case .websocket:
                    TextField("ws://host:port or wss://...", text: $websocketURL)
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .keyboardType(.URL)
                }
            }
        } header: {
            Text(connectionMode.formHeader)
                .foregroundColor(AgentBuddyTheme.textSecondary)
        } footer: {
            if !isSpecialPairedServer, connectionMode == .websocket {
                Text("Prefer SSH when possible. If you run codex manually, bind loopback and tunnel it yourself; do not expose it directly to the internet unless you know what you are doing.")
                    .agentBuddyFont(.caption2)
                    .foregroundColor(AgentBuddyTheme.textMuted)
            }
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    private var hostField: some View {
        TextField("hostname or IP", text: $host)
            .agentBuddyFont(.footnote)
            .foregroundColor(AgentBuddyTheme.textPrimary)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
    }

    private var actionSection: some View {
        Section {
            Button("Save") {
                submit(reconnect: false)
            }
            .foregroundColor(AgentBuddyTheme.accent)
            .agentBuddyFont(.subheadline)

            if !isSpecialPairedServer {
                Button(connectionMode == .local ? "Save & Restart" : "Save & Reconnect") {
                    submit(reconnect: true)
                }
                .foregroundColor(AgentBuddyTheme.accent)
                .agentBuddyFont(.subheadline)
            }
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    private func submit(reconnect: Bool) {
        do {
            let configuration = try buildConfiguration()
            if reconnect {
                onReconnect(configuration)
            } else {
                onSave(configuration)
            }
        } catch {
            validationError = error.localizedDescription
        }
    }

    private func buildConfiguration() throws -> SettingsServerConnectionConfiguration {
        let name = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { throw SettingsServerConnectionError.emptyName }

        if isSpecialPairedServer, let originalSavedServer {
            let updated = originalSavedServer.withName(name)
            return SettingsServerConnectionConfiguration(
                savedServer: updated,
                discoveredServer: updated.toDiscoveredServer(),
                connectionMode: connectionMode
            )
        }

        switch connectionMode {
        case .local:
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: "127.0.0.1",
                port: 0,
                codexPorts: [],
                sshPort: nil,
                source: .local,
                hasCodexServer: true,
                wakeMAC: nil,
                preferredConnectionMode: nil,
                preferredCodexPort: nil,
                sshPortForwardingEnabled: nil,
                websocketURL: nil,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .local
            )
        case .ssh:
            let resolvedHost = try validatedHost()
            let resolvedWakeMAC = try validatedWakeMAC()
            guard let resolvedSSHPort = UInt16(sshPort.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                throw SettingsServerConnectionError.invalidSSHPort
            }
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: resolvedHost,
                port: nil,
                codexPorts: [],
                sshPort: resolvedSSHPort,
                source: .manual,
                hasCodexServer: false,
                wakeMAC: resolvedWakeMAC,
                preferredConnectionMode: .ssh,
                preferredCodexPort: nil,
                sshPortForwardingEnabled: nil,
                websocketURL: nil,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .ssh
            )
        case .directCodex:
            let resolvedHost = try validatedHost()
            guard let resolvedCodexPort = UInt16(codexPort.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                throw SettingsServerConnectionError.invalidCodexPort
            }
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: resolvedHost,
                port: resolvedCodexPort,
                codexPorts: [resolvedCodexPort],
                sshPort: nil,
                source: .manual,
                hasCodexServer: true,
                wakeMAC: nil,
                preferredConnectionMode: .directCodex,
                preferredCodexPort: resolvedCodexPort,
                sshPortForwardingEnabled: nil,
                websocketURL: nil,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .directCodex
            )
        case .websocket:
            let rawURL = websocketURL.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let url = URL(string: rawURL),
                  let scheme = url.scheme?.lowercased(),
                  (scheme == "ws" || scheme == "wss"),
                  let resolvedHost = url.host,
                  !resolvedHost.isEmpty else {
                throw SettingsServerConnectionError.invalidWebsocketURL
            }
            let resolvedPort = url.port.flatMap { UInt16(exactly: $0) }
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: resolvedHost,
                port: resolvedPort,
                codexPorts: resolvedPort.map { [$0] } ?? [],
                sshPort: nil,
                source: .manual,
                hasCodexServer: true,
                wakeMAC: nil,
                preferredConnectionMode: .directCodex,
                preferredCodexPort: resolvedPort,
                sshPortForwardingEnabled: nil,
                websocketURL: rawURL,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .websocket
            )
        }
    }

    private func validatedHost() throws -> String {
        let resolvedHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !resolvedHost.isEmpty else { throw SettingsServerConnectionError.emptyHost }
        return resolvedHost
    }

    private func validatedWakeMAC() throws -> String? {
        let wakeInput = wakeMAC.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !wakeInput.isEmpty else { return nil }
        guard let normalized = DiscoveredServer.normalizeWakeMAC(wakeInput) else {
            throw SettingsServerConnectionError.invalidWakeMAC
        }
        return normalized
    }
}
