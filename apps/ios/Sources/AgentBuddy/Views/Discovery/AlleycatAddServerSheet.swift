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
    // (iOS-on-Mac) and the iOS QR fallback.
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
            ScrollView {
                VStack(alignment: .leading, spacing: BuddySpacing.xl) {
                    pairingSection
                    if let params = parsedParams {
                        previewSection(params: params)
                        agentSection
                    }
                    if let parseError {
                        errorBanner(parseError, tone: .warning)
                    }
                    if let agentError {
                        errorBanner(agentError, tone: .warning)
                    }
                    if let connectError {
                        errorBanner(connectError, tone: .danger)
                    }
                }
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.top, BuddySpacing.md)
                .padding(.bottom, BuddySpacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)
            .buddyPageBackground()
            .safeAreaInset(edge: .bottom) {
                connectSection
            }
            .navigationTitle("Pair with QR code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
        .buddySheetStyle()
        .onAppear {
            requestInitialScanIfNeeded()
        }
        // QR scanner cover + camera-denied alert are applied
        // unconditionally; on Mac builds (iOS-on-Mac) the
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
        DiscoveryFormSection("Scanned Host") {
            VStack(spacing: 0) {
                previewRow(label: "node", value: shortNodeId(params.nodeId))
                BuddyDivider()
                previewRow(label: "protocol", value: "v\(params.v)")
                if let relay = params.relay, !relay.isEmpty {
                    BuddyDivider()
                    previewRow(label: "relay", value: relay)
                }
                if let hostName = params.hostName, !hostName.isEmpty {
                    BuddyDivider()
                    previewRow(label: "host", value: hostName)
                }
            }
            .padding(.horizontal, BuddySpacing.md)
            .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)

            TextField("display name (optional)", text: $displayName)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .discoveryFieldStyle()
                .padding(.top, BuddySpacing.xxs)
        }
    }

    private func previewRow(label: String, value: String) -> some View {
        HStack(spacing: BuddySpacing.md) {
            Text(label)
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            Spacer()
            Text(value)
                .buddyText(.code)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
                .truncationMode(.middle)
        }
        .frame(minHeight: BuddySize.minHitTarget)
        .accessibilityElement(children: .combine)
    }

    private var connectSection: some View {
        BuddyButton("Connect", isLoading: isConnecting) {
            connect()
        }
        .disabled(!canConnect)
        .padding(.horizontal, BuddySpacing.xl)
        .padding(.vertical, BuddySpacing.sm)
        .background(AgentBuddyTheme.background)
    }

    private func errorBanner(_ message: String, tone: BuddyBanner.Tone) -> some View {
        BuddyBanner(tone: tone, message: Text(message))
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
