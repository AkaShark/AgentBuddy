import SwiftUI

struct HeaderView: View {
    @Environment(AppState.self) private var appState
    @Environment(AppModel.self) private var appModel
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let thread: AppThreadSnapshot
    @State private var pulsing = false
    @AppStorage("fastMode") private var fastMode = false

    private var isRegularSurface: Bool {
        AgentBuddyPlatform.isRegularSurface(horizontalSizeClass: horizontalSizeClass)
    }

    private var server: AppServerSnapshot? {
        appModel.snapshot?.serverSnapshot(for: thread.key.serverId)
    }

    private var availableModels: [ModelInfo] {
        appModel.availableModels(for: thread.key.serverId)
    }

    private var headerPermissionPreset: AppThreadPermissionPreset {
        let approval = appState.launchApprovalPolicy(for: thread.key) ?? thread.effectiveApprovalPolicy
        let sandbox = appState.turnSandboxPolicy(for: thread.key) ?? thread.effectiveSandboxPolicy
        return threadPermissionPreset(approvalPolicy: approval, sandboxPolicy: sandbox)
    }

    var body: some View {
        Button {
            appState.showModelSelector.toggle()
        } label: {
            expandedHeaderLabel
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .frame(maxWidth: isRegularSurface ? 420 : 260, alignment: .center)
        }
        .layoutPriority(-1)
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .accessibilityIdentifier("header.modelPickerButton")
        .popover(
            isPresented: Binding(
                get: { appState.showModelSelector },
                set: { appState.showModelSelector = $0 }
            ),
            attachmentAnchor: .rect(.bounds),
            arrowEdge: .top
        ) {
            ConversationModelPickerPanel(thread: thread)
                .environment(appModel)
                .environment(appState)
                .presentationCompactAdaptation(.popover)
        }
        .task(id: thread.key) {
            await loadModelsIfNeeded()
        }
    }

    /// Mint header: task title, then "partner · host" with the connection
    /// dot. Tapping still opens the partner / model / permission panel, which
    /// returns to this conversation when dismissed.
    private var expandedHeaderLabel: some View {
        VStack(spacing: 1) {
            primaryHeaderRow
            secondaryHeaderRow
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint(Text("Shows partner, model and permission options"))
    }

    private var primaryHeaderRow: some View {
        Text(verbatim: thread.displayTitle)
            .buddyText(.heading)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .lineLimit(1)
            .truncationMode(.tail)
    }

    private var secondaryHeaderRow: some View {
        HStack(spacing: 5) {
            statusDot

            if fastMode {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.warning)
                    .accessibilityLabel(Text("Fast mode"))
            }

            Text(verbatim: partnerAndHostLabel)
                .buddyText(.caption, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .lineLimit(1)
                .truncationMode(.middle)

            if thread.collaborationMode == .plan {
                Text("Plan")
                    .buddyText(.caption, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.onBrand)
                    .padding(.horizontal, 6)
                    .background(AgentBuddyTheme.brand, in: Capsule())
            }

            if headerPermissionPreset == .fullAccess {
                Image(systemName: "lock.open.fill")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.danger)
                    .accessibilityLabel(Text("Full access"))
            }

            Image(systemName: "chevron.down")
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .rotationEffect(.degrees(appState.showModelSelector ? 180 : 0))
                .accessibilityHidden(true)
        }
    }

    /// "Codex · MacBook Pro".
    private var partnerAndHostLabel: String {
        let partner = thread.agentRuntimeKind.displayLabel
        guard let host = server?.displayName.trimmingCharacters(in: .whitespacesAndNewlines), !host.isEmpty else {
            return partner
        }
        return "\(partner) · \(host)"
    }

    private var statusDot: some View {
        Circle()
            .fill(statusDotColor)
            .frame(width: 6, height: 6)
            .opacity(shouldPulse ? (pulsing ? 0.3 : 1.0) : 1.0)
            .animation(
                shouldPulse ? .easeInOut(duration: 0.8).repeatForever(autoreverses: true) : .default,
                value: pulsing
            )
            .onChange(of: shouldPulse) { _, pulse in
                pulsing = pulse
            }
    }

    private var shouldPulse: Bool {
        guard let transportState = server?.transportState else { return false }
        return transportState == .connecting || transportState == .unresponsive
    }

    private var statusDotColor: Color {
        guard let server else {
            return AgentBuddyTheme.textMuted
        }
        switch server.transportState {
        case .connecting, .unresponsive:
            return .orange
        case .connected:
            if server.isLocal {
                switch server.account {
                case .chatgpt?, .apiKey?:
                    return AgentBuddyTheme.success
                case nil:
                    return AgentBuddyTheme.danger
                }
            }
            return server.account == nil ? .orange : AgentBuddyTheme.success
        case .disconnected:
            return AgentBuddyTheme.danger
        case .unknown:
            return AgentBuddyTheme.textMuted
        }
    }

    private var selectedModelBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty { return pending }
                return currentThreadModelSelectionId
            },
            set: { appState.selectedModel = $0 }
        )
    }

    private var selectedAgentRuntimeKindBinding: Binding<AgentRuntimeKind?> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty { return appState.selectedAgentRuntimeKind }
                return currentThreadAgentRuntimeKind
            },
            set: { appState.selectedAgentRuntimeKind = $0 }
        )
    }

    private var currentThreadModelSelectionId: String {
        let currentModel = (thread.model ?? thread.info.model ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !currentModel.isEmpty else { return "" }
        return currentModel
    }

    private var currentThreadAgentRuntimeKind: AgentRuntimeKind? {
        thread.agentRuntimeKind
    }

    private var reasoningEffortBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.reasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty { return pending }
                return thread.reasoningEffort?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            },
            set: { appState.reasoningEffort = $0 }
        )
    }

    private func loadModelsIfNeeded() async {
        await appModel.loadConversationMetadataIfNeeded(serverId: thread.key.serverId)
    }
}

#if DEBUG
#Preview("Header") {
    let appModel = AgentBuddyPreviewData.makeConversationAppModel()
    AgentBuddyPreviewScene(appModel: appModel) {
        HeaderView(thread: appModel.snapshot!.threads[0])
    }
}
#endif
