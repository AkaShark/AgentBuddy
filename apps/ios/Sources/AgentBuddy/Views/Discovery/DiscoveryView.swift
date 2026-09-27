import SwiftUI
import Network

struct DiscoveryView: View {
    var onServerSelected: ((DiscoveredServer) -> Void)?
    @Environment(AppModel.self) var appModel
    @State var discovery: NetworkDiscovery
    @State var sshServer: DiscoveredServer?
    @State var connectionChoiceServer: DiscoveredServer?
    @State var pendingSSHServer: DiscoveredServer?
    @State var sshAgentContext: SSHBridgeAgentContext?
    @State var showManualEntry = false
    @State var showAlleycatSheet = false
    @State var showSlingshotHosts = false
    @State var slingshotEnvironments: [AppSlingshotEnvironment] = []
    @State var slingshotIsLoading = false
    @State var slingshotError: String?
    @State var manualConnectionMode: DiscoveryManualConnectionMode = .ssh
    @State var manualCodexURL = ""
    @State var manualHost = ""
    @State var manualSSHPort = "22"
    @State var manualWakeMAC = ""
    @State var autoSSHStarted = false
    @State var connectingServer: DiscoveredServer?
    @State var wakingServer: DiscoveredServer?
    @State var pendingAutoNavigateServerId: String?
    @State var pendingAutoNavigateServer: DiscoveredServer?
    @State var connectError: String?
    @State var sshHostKeyChange: SSHHostKeyChangePrompt?
    @State var guidedSSHAttempt: DiscoveryGuidedSSHAttempt?
    @State var renameTarget: DiscoveredServer?
    @State var renameText = ""
    @State private var didHandleEntryPoint = false
    @Environment(AppState.self) var appState
    @Environment(\.dismiss) private var dismiss
    private let entryPoint: DiscoveryEntryPoint
    private let autoStartDiscovery: Bool
    private let initialServers: [DiscoveredServer]
    let slingshotBaseURL = "https://chatgpt.com/backend-api"

    init(
        onServerSelected: ((DiscoveredServer) -> Void)? = nil,
        entryPoint: DiscoveryEntryPoint = .chooser,
        discovery: NetworkDiscovery? = nil,
        autoStartDiscovery: Bool = true,
        initialServers: [DiscoveredServer] = []
    ) {
        self.onServerSelected = onServerSelected
        self.entryPoint = entryPoint
        _discovery = State(initialValue: discovery ?? NetworkDiscovery())
        self.autoStartDiscovery = autoStartDiscovery
        self.initialServers = initialServers
    }

    var localServers: [DiscoveredServer] {
        discovery.servers.filter { $0.source == .local }
    }

    var networkServers: [DiscoveredServer] {
        discovery.servers.filter { $0.source != .local }
    }

    private func applyInitialServersIfNeeded() {
        guard !initialServers.isEmpty, discovery.servers.isEmpty else { return }
        discovery.servers = initialServers
        discovery.isScanning = false
    }

    private func refreshDiscovery() {
        guard autoStartDiscovery else {
            applyInitialServersIfNeeded()
            return
        }
        discovery.startScanning()
    }

    private func handleAppear() {
        openEntryPointIfNeeded()
        guard autoStartDiscovery else { return }
        maybeStartSimulatorAutoSSH()
    }

    private func handleDisappear() {}

    /// "Scan to connect" on home opens the QR pairing sheet directly, through
    /// the same presentation the chooser's QR card uses. Runs once, so
    /// dismissing the sheet leaves the chooser for the other options.
    private func openEntryPointIfNeeded() {
        guard !didHandleEntryPoint else { return }
        didHandleEntryPoint = true
        guard entryPoint == .pairWithQRCode else { return }
        // Same hand-off the pairing sheet uses for its camera cover: let
        // this sheet finish appearing before presenting on top of it.
        Task { @MainActor in
            await Task.yield()
            showAlleycatSheet = true
        }
    }

    var body: some View {
        Group {
            chooserContent
        }
        .buddyPageBackground()
        .navigationTitle("Add a host")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Close") { dismiss() }
                    .foregroundStyle(AgentBuddyTheme.link)
            }
        }
        .buddySheetStyle()
        .onAppear { handleAppear() }
        .onDisappear { handleDisappear() }
        .sheet(item: $sshServer) { server in
            SSHLoginSheet(server: server) { target in
                sshServer = nil
                if case .sshThenRemote(let host, let credentials) = target {
                    Task { await startSSHAgentProbe(server: server, host: host, credentials: credentials) }
                } else {
                    Task { await connectToServer(server, targetOverride: target) }
                }
            }
        }
        .sheet(item: $sshAgentContext) { context in
            SSHAgentPickerSheet(
                context: context,
                appModel: appModel,
                onConnected: { result in
                    sshAgentContext = nil
                    Task { await connectSSHBridgeTarget(result, baseServer: context.server) }
                },
                onUseCodex: {
                    sshAgentContext = nil
                    Task {
                        try? await appModel.ssh.sshClose(sessionId: context.sessionId)
                        await connectToServer(
                            context.server,
                            targetOverride: .sshThenRemote(host: context.host, credentials: context.credentials)
                        )
                    }
                },
                onCancel: {
                    sshAgentContext = nil
                    Task { try? await appModel.ssh.sshClose(sessionId: context.sessionId) }
                }
            )
        }
        .confirmationDialog(
            connectionChoiceServer.map { "Connect to \($0.name)" } ?? "Choose Connection",
            isPresented: connectionChoicePresented,
            titleVisibility: .visible
        ) {
            if let server = connectionChoiceServer {
                ForEach(server.availableDirectCodexPorts, id: \.self) { port in
                    Button("Use Codex (\(port))") {
                        let preferredServer = server.withConnectionPreference(.directCodex, codexPort: port)
                        connectionChoiceServer = nil
                        Task { await connectToServer(preferredServer) }
                    }
                }
                if server.canConnectViaSSH {
                    Button("Connect via SSH") {
                        let preferredServer = server.withConnectionPreference(.ssh)
                        connectionChoiceServer = nil
                        sshServer = preferredServer
                    }
                }
            }
            Button("Cancel", role: .cancel) {
                connectionChoiceServer = nil
            }
        } message: {
            if let server = connectionChoiceServer {
                Text(connectionChoiceMessage(for: server))
            }
        }
        .sheet(isPresented: $showManualEntry) {
            manualEntrySheet
        }
        .sheet(isPresented: $showSlingshotHosts) {
            slingshotHostsSheet
        }
        .sheet(isPresented: $showAlleycatSheet) {
            AlleycatAddServerSheet(appModel: appModel, startScanningOnAppear: true) { result in
                showAlleycatSheet = false
                Task { await connectAlleycatTarget(result) }
            }
        }
        .onChange(of: showManualEntry) { _, isPresented in
            guard !isPresented, let pendingSSHServer else { return }
            self.pendingSSHServer = nil
            self.sshServer = pendingSSHServer
        }
        .onChange(of: appModel.snapshot) { _, _ in
            handlePendingAutoNavigate()
        }
        .sshHostKeyChangeAlert($sshHostKeyChange)
        .alert("Connection Failed", isPresented: showConnectError, actions: {
            Button("OK") { connectError = nil }
        }, message: {
            Text(connectError ?? "Unable to connect.")
        })
        .alert("Rename Server", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Name", text: $renameText)
            Button("Cancel", role: .cancel) { renameTarget = nil }
            Button("Save") {
                if let server = renameTarget {
                    let trimmed = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
                    let newName = trimmed.isEmpty ? server.hostname : trimmed
                    SavedServerStore.upsert(DiscoveredServer(
                        id: server.id,
                        name: newName,
                        hostname: server.hostname,
                        port: server.port,
                        codexPorts: server.codexPorts,
                        sshPort: server.sshPort,
                        source: server.source,
                        hasCodexServer: server.hasCodexServer,
                        wakeMAC: server.wakeMAC,
                        preferredConnectionMode: server.preferredConnectionMode,
                        preferredCodexPort: server.preferredCodexPort,
                        os: server.os,
                        sshBanner: server.sshBanner
                    ))
                    if let idx = discovery.servers.firstIndex(where: { $0.id == server.id }) {
                        discovery.servers[idx] = DiscoveredServer(
                            id: server.id,
                            name: newName,
                            hostname: server.hostname,
                            port: server.port,
                            codexPorts: server.codexPorts,
                            sshPort: server.sshPort,
                            source: server.source,
                            hasCodexServer: server.hasCodexServer,
                            wakeMAC: server.wakeMAC,
                            preferredConnectionMode: server.preferredConnectionMode,
                            preferredCodexPort: server.preferredCodexPort,
                            os: server.os,
                            sshBanner: server.sshBanner
                        )
                    }
                }
                renameTarget = nil
            }
        } message: {
            Text("Enter a new name for this server.")
        }
    }

    private var showConnectError: Binding<Bool> {
        Binding(
            get: { connectError != nil },
            set: { newValue in
                if !newValue {
                    connectError = nil
                }
            }
        )
    }

    private var connectionChoicePresented: Binding<Bool> {
        Binding(
            get: { connectionChoiceServer != nil },
            set: { newValue in
                if !newValue {
                    connectionChoiceServer = nil
                }
            }
        )
    }

    private func connectionChoiceMessage(for server: DiscoveredServer) -> String {
        let directPorts = server.availableDirectCodexPorts.map(String.init)
        if directPorts.isEmpty {
            return "Use SSH to bootstrap Codex on \(server.hostname)."
        }
        if server.canConnectViaSSH {
            return "Codex is available on ports \(directPorts.joined(separator: ", ")) and SSH is also available on port \(server.resolvedSSHPort)."
        }
        return "Choose a Codex app-server port on \(server.hostname)."
    }
}

#if DEBUG
#Preview("Discovery") {
    AgentBuddyPreviewScene(
        appModel: AgentBuddyPreviewData.makeDiscoveryAppModel(),
        includeBackground: false
    ) {
        NavigationStack {
            DiscoveryView(
                autoStartDiscovery: false,
                initialServers: AgentBuddyPreviewData.sampleDiscoveryServers
            )
        }
    }
}
#endif
