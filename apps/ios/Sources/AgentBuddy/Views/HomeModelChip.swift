import SwiftUI

/// Small tap-to-open model picker for the home composer bar, styled to
/// match `ProjectChip` so the two sit together above the input. Reads +
/// writes the persisted home defaults (`appState.preferredModel` /
/// `appState.preferredReasoningEffort`) so the choice survives thread
/// switches and app relaunches before the next `startThread` call.
struct HomeModelChip: View {
    @Environment(AppModel.self) private var appModel
    @Environment(AppState.self) private var appState
    @AppStorage("fastMode") private var fastMode = false

    /// The server the chip should pull available models from. Typically
    /// the currently-selected project's serverId; when nothing is picked
    /// the chip is disabled.
    let serverId: String?
    let disabled: Bool
    var onSheetStateChange: (Bool) -> Void = { _ in }

    @State private var showSheet = false
    @State private var selectedDetent: PresentationDetent = .large

    /// Whether the user has escalated the pre-thread launch permissions to
    /// the equivalent of the header's "Full Access" preset.
    private var isFullAccess: Bool {
        let approval = appState.launchApprovalPolicy(for: nil)
        let sandbox = appState.turnSandboxPolicy(for: nil)
        return threadPermissionPreset(
            approvalPolicy: approval,
            sandboxPolicy: sandbox
        ) == .fullAccess
    }

    private var isPlanMode: Bool {
        appState.pendingCollaborationMode == .plan
    }

    private var availableModels: [ModelInfo] {
        guard let serverId else { return [] }
        return appModel.availableModels(for: serverId)
    }

    private var selectedModelLabel: String {
        let trimmed = appState.preferredModel.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            if let match = availableModels.first(where: {
                modelMatchesSelection(
                    $0,
                    trimmed,
                    runtime: appState.preferredAgentRuntimeKind
                )
            }) {
                return modelPickerDisplayName(match)
            }
            return trimmed
        }
        if let defaultModel = availableModels.first(where: { $0.isDefault }) {
            return modelPickerDisplayName(defaultModel)
        }
        return "model"
    }

    private var reasoningLabel: String {
        let trimmed = appState.preferredReasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
        return ""
    }

    private var selectedModelBinding: Binding<String> {
        Binding(
            get: { appState.preferredModel },
            set: { appState.preferredModel = $0 }
        )
    }

    private var selectedAgentRuntimeKindBinding: Binding<AgentRuntimeKind?> {
        Binding(
            get: { appState.preferredAgentRuntimeKind },
            set: { appState.preferredAgentRuntimeKind = $0 }
        )
    }

    private var reasoningEffortBinding: Binding<String> {
        Binding(
            get: { appState.preferredReasoningEffort },
            set: { appState.preferredReasoningEffort = $0 }
        )
    }

    var body: some View {
        Button {
            selectedDetent = .large
            showSheet = true
        } label: {
            HStack(spacing: 6) {
                if fastMode {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.warning)
                }
                Image(systemName: "cpu")
                    .font(.system(size: 13, weight: .medium))
                    .accessibilityHidden(true)
                Text(verbatim: selectedModelLabel)
                if !reasoningLabel.isEmpty {
                    Text(verbatim: reasoningLabel)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                if isPlanMode {
                    Text("Plan")
                        .buddyText(.caption, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.onBrand)
                        .padding(.horizontal, 6)
                        .background(AgentBuddyTheme.brand, in: Capsule())
                }
                if isFullAccess {
                    Image(systemName: "lock.open.fill")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.danger)
                        .accessibilityLabel(Text("Full access"))
                }
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
            }
            .buddyContextChip(isEnabled: !disabled)
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .sheet(isPresented: $showSheet) {
            ConversationOptionsSheet(
                models: availableModels,
                selectedModel: selectedModelBinding,
                selectedAgentRuntimeKind: selectedAgentRuntimeKindBinding,
                reasoningEffort: reasoningEffortBinding,
                threadKey: nil
            )
            .environment(appModel)
            .environment(appState)
            .presentationDetents([.medium, .large], selection: $selectedDetent)
            .presentationDragIndicator(.visible)
            .presentationContentInteraction(.scrolls)
            .presentationBackground(AgentBuddyTheme.surface)
        }
        .onChange(of: showSheet) { _, isPresented in
            onSheetStateChange(isPresented)
        }
        .task(id: serverId) {
            guard let serverId else { return }
            await appModel.loadConversationMetadataIfNeeded(serverId: serverId)
        }
    }
}
