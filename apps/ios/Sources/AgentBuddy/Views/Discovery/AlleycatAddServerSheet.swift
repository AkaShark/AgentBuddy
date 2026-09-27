import AVFoundation
import SwiftUI
import UIKit

struct AlleycatConnectedTarget: Equatable {
    let serverId: String
    let nodeId: String
    let displayName: String
    let params: AppAlleycatPairPayload
    let agentName: String
    let agentWire: AppAlleycatAgentWire
}

struct AlleycatAddServerSheet: View {
    let appModel: AppModel
    let startScanningOnAppear: Bool
    let onConnected: (AlleycatConnectedTarget) -> Void

    @Environment(\.dismiss) private var dismiss
    @State var displayName: String = ""
    @State var parsedParams: AppAlleycatPairPayload?
    @State var agents: [AppAlleycatAgentInfo] = []
    @State var selectedAgentNames: Set<String> = []
    @State var isLoadingAgents = false
    @State var parseError: String?
    @State var agentError: String?
    @State private var isConnecting = false
    @State var connectError: String?
    @State var showScanner = false
    @State var didRequestInitialScan = false
    @State var cameraDenied = false
    // pasteJSON / showPaste are used by the Mac paste-JSON UI
    // (Catalyst + iOS-on-Mac) and the iOS QR fallback.
    @State var pasteJSON: String = ""
    @State var showPaste: Bool = false

    let alleycat = RustAlleycatBridge.shared

    init(
        appModel: AppModel,
        startScanningOnAppear: Bool = false,
        onConnected: @escaping (AlleycatConnectedTarget) -> Void
    ) {
        self.appModel = appModel
        self.startScanningOnAppear = startScanningOnAppear
        self.onConnected = onConnected
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    pairingSection
                    if let params = parsedParams {
                        previewSection(params: params)
                        agentSection
                    }
                    if let parseError {
                        errorSection(parseError, color: AgentBuddyTheme.warning)
                    }
                    if let agentError {
                        errorSection(agentError, color: AgentBuddyTheme.warning)
                    }
                    connectSection
                    if let connectError {
                        errorSection(connectError, color: AgentBuddyTheme.danger)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Add Remote Host")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
        }
        .onAppear {
            requestInitialScanIfNeeded()
        }
        // QR scanner cover + camera-denied alert are applied
        // unconditionally; on Mac builds (Catalyst + iOS-on-Mac) the
        // pairing section never triggers `requestCameraAndScan`, so
        // neither presentation ever fires.
        .fullScreenCover(isPresented: $showScanner) {
            AlleycatQRScannerScreen(
                onScan: { scanned in
                    showScanner = false
                    handleScannedPayload(scanned)
                },
                onCancel: {
                    showScanner = false
                    if startScanningOnAppear, parsedParams == nil {
                        dismiss()
                    }
                },
                onPermissionDenied: {
                    showScanner = false
                    cameraDenied = true
                }
            )
        }
        .alert(
            "Camera Access Needed",
            isPresented: $cameraDenied,
            actions: {
                Button("Open Settings") { openAppSettings() }
                Button("Cancel", role: .cancel) {}
            },
            message: {
                Text("Allow camera access in Settings to scan an Alleycat pairing QR code.")
            }
        )
    }

    private func previewSection(params: AppAlleycatPairPayload) -> some View {
        Section {
            previewRow(label: "node", value: shortNodeId(params.nodeId))
            previewRow(label: "protocol", value: "v\(params.v)")
            if let relay = params.relay, !relay.isEmpty {
                previewRow(label: "relay", value: relay)
            }
            if let hostName = params.hostName, !hostName.isEmpty {
                previewRow(label: "host", value: hostName)
            }
            TextField("display name (optional)", text: $displayName)
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
        } header: {
            Text("Scanned Host")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    private func previewRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.textSecondary)
            Spacer()
            Text(value)
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
                .truncationMode(.middle)
        }
    }

    private var connectSection: some View {
        Section {
            Button {
                connect()
            } label: {
                HStack {
                    if isConnecting {
                        ProgressView().tint(AgentBuddyTheme.accent)
                    }
                    Text("Connect")
                        .foregroundColor(AgentBuddyTheme.accent)
                        .agentBuddyFont(.subheadline)
                }
            }
            .disabled(!canConnect)
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    private func errorSection(_ message: String, color: Color) -> some View {
        Section {
            Text(message)
                .agentBuddyFont(.caption)
                .foregroundColor(color)
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    private var canConnect: Bool {
        !isConnecting && !isLoadingAgents && parsedParams != nil && !selectedAgents.isEmpty
    }

    private func connect() {
        guard let params = parsedParams, let fallbackAgent = selectedAgents.first else { return }
        let trimmedDisplay = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName = trimmedDisplay.isEmpty ? suggestedDisplayName(for: params) : trimmedDisplay
        let selectedNames = selectedAgents.map(\.name)
        let serverId = "alleycat:\(params.nodeId)"

        isConnecting = true
        connectError = nil

        Task {
            do {
                let result = try await appModel.serverBridge.connectRemoteOverAlleycat(
                    serverId: serverId,
                    displayName: resolvedName,
                    params: params,
                    agentName: fallbackAgent.name,
                    selectedAgentNames: selectedNames,
                    wire: fallbackAgent.wire
                )
                do {
                    try AlleycatCredentialStore.shared.saveToken(params.token, nodeId: params.nodeId)
                } catch {
                    NSLog("[ALLEYCAT_CREDENTIALS] keychain save failed: %@", error.localizedDescription)
                }
                // First successful alleycat pair triggers the iroh
                // endpoint bind. Persist the freshly-generated device
                // secret key so the next cold launch reuses the same
                // `EndpointId`.
                await MainActor.run {
                    AppRuntimeController.shared.persistAlleycatSecretKeyIfNeeded()
                }

                await MainActor.run {
                    isConnecting = false
                    onConnected(
                        AlleycatConnectedTarget(
                            serverId: result.serverId,
                            nodeId: result.nodeId,
                            displayName: resolvedName,
                            params: params,
                            agentName: result.agentName,
                            agentWire: fallbackAgent.wire
                        )
                    )
                }
            } catch {
                await MainActor.run {
                    isConnecting = false
                    connectError = error.localizedDescription
                }
            }
        }
    }

    func suggestedDisplayName(for params: AppAlleycatPairPayload) -> String {
        let hostName = params.hostName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !hostName.isEmpty {
            return hostName
        }
        return "Alleycat \(shortNodeId(params.nodeId))"
    }

    private func shortNodeId(_ raw: String) -> String {
        if raw.count <= 16 { return raw }
        return "\(raw.prefix(8))...\(raw.suffix(8))"
    }
}
