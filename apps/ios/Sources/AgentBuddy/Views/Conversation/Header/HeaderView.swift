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
            .frame(maxWidth: isRegularSurface ? 320 : 240, alignment: .center)
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

    private var expandedHeaderLabel: some View {
        VStack(spacing: 2) {
            primaryHeaderRow
            secondaryHeaderRow
        }
    }

    private var primaryHeaderRow: some View {
        HStack(spacing: 6) {
            statusDot

            if fastMode {
                Image(systemName: "bolt.fill")
                    .font(AgentBuddyFont.styled(size: 10, weight: .semibold))
                    .foregroundColor(AgentBuddyTheme.warning)
            }

            Text(sessionModelLabel)
                .foregroundColor(AgentBuddyTheme.textPrimary)
                .allowsTightening(true)
            Text(sessionReasoningLabel)
                .foregroundColor(AgentBuddyTheme.textSecondary)
                .allowsTightening(true)
            Image(systemName: "chevron.down")
                .font(AgentBuddyFont.styled(size: 10, weight: .semibold))
                .foregroundColor(AgentBuddyTheme.textSecondary)
                .rotationEffect(.degrees(appState.showModelSelector ? 180 : 0))
        }
        .font(AgentBuddyFont.styled(size: 14, weight: .semibold))
        .lineLimit(1)
        .minimumScaleFactor(isRegularSurface ? 1.0 : 0.75)
    }

    private var secondaryHeaderRow: some View {
        HStack(spacing: 6) {
            Text(sessionDirectoryLabel)
                .font(AgentBuddyFont.styled(size: 11, weight: .semibold))
                .foregroundColor(AgentBuddyTheme.textSecondary)
                .lineLimit(1)
                .truncationMode(.middle)

            if thread.collaborationMode == .plan {
                Text("plan")
                    .font(AgentBuddyFont.styled(size: 11, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(AgentBuddyTheme.accent)
                    .clipShape(Capsule())
            }

            if headerPermissionPreset == .fullAccess {
                Image(systemName: "lock.open.fill")
                    .font(AgentBuddyFont.styled(size: 10, weight: .semibold))
                    .foregroundColor(AgentBuddyTheme.danger)
            }

        }
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

    private var sessionModelLabel: String {
        let pendingModel = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
        if !pendingModel.isEmpty {
            if let model = availableModels.first(where: {
                modelMatchesSelection(
                    $0,
                    pendingModel,
                    runtime: appState.selectedAgentRuntimeKind
                )
            }) {
                return modelPickerDisplayName(model)
            }
            return pendingModel
        }

        let threadModel = thread.displayModelLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        if !threadModel.isEmpty { return threadModel }

        return "AgentBuddy"
    }

    private var sessionReasoningLabel: String {
        let pendingReasoning = appState.reasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
        if !pendingReasoning.isEmpty { return pendingReasoning }

        let threadReasoning = thread.reasoningEffort?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !threadReasoning.isEmpty { return threadReasoning }

        // Fall back to the model's default reasoning effort from the loaded model list.
        let currentModel = (thread.model ?? thread.info.model ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if let model = availableModels.first(where: {
            modelMatchesSelection(
                $0,
                currentModel,
                runtime: thread.agentRuntimeKind
            )
        }),
           !model.supportedReasoningEfforts.isEmpty,
           !model.defaultReasoningEffort.wireValue.isEmpty {
            return model.defaultReasoningEffort.wireValue
        }

        return "default"
    }

    private var sessionDirectoryLabel: String {
        let currentDirectory = (thread.info.cwd ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !currentDirectory.isEmpty {
            let isLocal = appModel.isLocalServer(serverId: thread.key.serverId)
            return PathDisplay.display(currentDirectory, isLocal: isLocal)
        }

        return "~"
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
