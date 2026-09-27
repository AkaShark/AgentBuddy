import SwiftUI

struct SSHBridgeAgentContext: Identifiable {
    let id: String
    let server: DiscoveredServer
    let sessionId: String
    let host: String
    let availability: [RemoteAgentAvailability]
    let credentials: SSHCredentials

    init(
        server: DiscoveredServer,
        sessionId: String,
        host: String,
        availability: [RemoteAgentAvailability],
        credentials: SSHCredentials
    ) {
        self.id = sessionId
        self.server = server
        self.sessionId = sessionId
        self.host = host
        self.availability = availability
        self.credentials = credentials
    }
}

struct SSHBridgeAgentResult {
    let serverId: String
    let displayName: String
    let host: String
    let port: UInt16
    let sessionId: String
    let runtimeKinds: [AgentRuntimeKind]
}

struct SSHAgentPickerSheet: View {
    let context: SSHBridgeAgentContext
    let appModel: AppModel
    let onConnected: (SSHBridgeAgentResult) -> Void
    let onUseCodex: () -> Void
    let onCancel: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedKinds: Set<AgentRuntimeKind>
    @State private var isConnecting = false
    @State private var connectError: String?

    init(
        context: SSHBridgeAgentContext,
        appModel: AppModel,
        onConnected: @escaping (SSHBridgeAgentResult) -> Void,
        onUseCodex: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) {
        self.context = context
        self.appModel = appModel
        self.onConnected = onConnected
        self.onUseCodex = onUseCodex
        self.onCancel = onCancel
        _selectedKinds = State(initialValue: Set(
            Self.availableBridgeKinds(in: context.availability).filter { !$0.isBeta }
        ))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: BuddySpacing.xl) {
                    DiscoveryHostSummary(systemImage: "terminal", name: context.server.name, address: context.host)
                    agentSection
                    if let connectError {
                        BuddyBanner(tone: .danger, message: Text(connectError))
                    }
                }
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.top, BuddySpacing.md)
                .padding(.bottom, BuddySpacing.xl)
            }
            .buddyPageBackground()
            .safeAreaInset(edge: .bottom) {
                connectSection
            }
            .navigationTitle("Remote Agents")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        onCancel()
                        dismiss()
                    }
                    .foregroundStyle(AgentBuddyTheme.link)
                    .disabled(isConnecting)
                }
            }
        }
        .buddySheetStyle()
    }

    private var agentSection: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            DiscoveryAgentListHeader(
                showsToggle: !availableBridgeKinds.isEmpty,
                allSelected: selectedKinds.count == availableBridgeKinds.count,
                isEnabled: !isConnecting
            ) {
                if selectedKinds.count == availableBridgeKinds.count {
                    selectedKinds = []
                } else {
                    selectedKinds = Set(availableBridgeKinds)
                }
            }

            VStack(spacing: 0) {
                ForEach(Array(context.availability.enumerated()), id: \.element.kind) { index, agent in
                    if index > 0 { BuddyDivider().padding(.leading, BuddySpacing.md) }
                    Button {
                        guard isBridgeKind(agent.kind), agent.status == .available else { return }
                        if selectedKinds.contains(agent.kind) {
                            selectedKinds.remove(agent.kind)
                        } else {
                            selectedKinds.insert(agent.kind)
                        }
                    } label: {
                        DiscoveryAgentRow(
                            kind: agent.kind,
                            title: runtimeDisplayName(agent.kind),
                            detail: statusLabel(agent.status, kind: agent.kind),
                            isBeta: agent.kind.isBeta,
                            mark: agentMark(for: agent)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(!isBridgeKind(agent.kind) || agent.status != .available || isConnecting)
                }
            }
            .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
        }
    }

    private func agentMark(for agent: RemoteAgentAvailability) -> DiscoveryAgentRow.Mark {
        if selectedKinds.contains(agent.kind) { return .selected }
        if isBridgeKind(agent.kind), agent.status == .available { return .unselected }
        // The detail line already says why (CLI missing, Windows…).
        return .unavailable(note: nil)
    }

    private var connectSection: some View {
        VStack(spacing: BuddySpacing.xxs) {
            BuddyButton("Connect", isLoading: isConnecting) {
                connect()
            }
            .disabled(isConnecting || selectedKinds.isEmpty)

            BuddyButton("Use Codex SSH", kind: .quiet) {
                onUseCodex()
                dismiss()
            }
            .disabled(isConnecting)
        }
        .padding(.horizontal, BuddySpacing.xl)
        .padding(.vertical, BuddySpacing.sm)
        .background(AgentBuddyTheme.background)
    }

    private var availableBridgeKinds: [AgentRuntimeKind] {
        Self.availableBridgeKinds(in: context.availability)
    }

    private func connect() {
        isConnecting = true
        connectError = nil
        let runtimeKinds = Array(selectedKinds).sorted { runtimeSortRank($0) < runtimeSortRank($1) }
        Task {
            do {
                let result = try await appModel.ssh.sshConnectBridgeSession(
                    sessionId: context.sessionId,
                    serverId: "ssh-bridge:\(context.host)",
                    displayName: context.server.name,
                    host: context.host,
                    stateRoot: try sshBridgeStateRoot(host: context.host),
                    runtimeKinds: runtimeKinds,
                    transport: .ephemeral
                )
                isConnecting = false
                onConnected(SSHBridgeAgentResult(
                    serverId: result.serverId,
                    displayName: context.server.name,
                    host: context.host,
                    port: context.server.resolvedSSHPort,
                    sessionId: context.sessionId,
                    runtimeKinds: runtimeKinds
                ))
                dismiss()
            } catch {
                isConnecting = false
                connectError = error.localizedDescription
            }
        }
    }

    private static func availableBridgeKinds(in availability: [RemoteAgentAvailability]) -> [AgentRuntimeKind] {
        availability
            .filter { isBridgeKind($0.kind) && $0.status == .available }
            .map(\.kind)
            .sorted { runtimeSortRank($0) < runtimeSortRank($1) }
    }
}

private func isBridgeKind(_ kind: AgentRuntimeKind) -> Bool {
    // Prefer the capability flag from alleycat metadata; fall back to
    // the legacy SSH-bridge-supported allowlist when metadata isn't
    // cached yet (cold start).
    if let supports = kind.metadata?.capabilities?.supportsSshBridge {
        return supports
    }
    switch kind {
    case "codex", "claude", "pi", "opencode":
        return true
    default:
        return false
    }
}

private func runtimeDisplayName(_ kind: AgentRuntimeKind) -> String {
    kind.displayLabel
}

private func runtimeSortRank(_ kind: AgentRuntimeKind) -> Int {
    // SSH-bridge picker keeps its own historical ordering distinct
    // from the general presentation order: Claude leads because it's
    // the most common SSH-bootstrap target.
    switch kind {
    case "claude": return 0
    case "pi": return 1
    case "opencode": return 2
    case "codex": return 3
    case "amp": return 4
    case "droid": return 5
    case "hermes": return 6
    default: return Int.max
    }
}

private func statusLabel(_ status: AgentAvailabilityStatus, kind: AgentRuntimeKind) -> String {
    switch status {
    case .available:
        return "Available"
    case .agentCliMissing:
        return "CLI missing"
    case .windowsNotYetSupported:
        return "Windows not supported"
    }
}

private func sshBridgeStateRoot(host: String) throws -> String {
    let fm = FileManager.default
    let base = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        ?? URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
    let safeHost = host
        .addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "host"
    let dir = base
        .appendingPathComponent("alleycat-bridges", isDirectory: true)
        .appendingPathComponent(safeHost, isDirectory: true)
    try fm.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir.path
}
