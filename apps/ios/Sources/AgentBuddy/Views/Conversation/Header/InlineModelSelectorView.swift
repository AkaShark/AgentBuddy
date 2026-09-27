import SwiftUI

struct InlineModelSelectorView: View {
    let models: [ModelInfo]
    @Binding var selectedModel: String
    @Binding var selectedAgentRuntimeKind: AgentRuntimeKind?
    @Binding var reasoningEffort: String
    /// `nil` indicates the view is being used before a thread exists (home
    /// composer). In that case, plan-mode selection is stored as a pending
    /// app-state preference that the caller applies after `startThread`.
    var threadKey: ThreadKey?
    var collaborationMode: AppModeKind = .default
    var effectiveApprovalPolicy: AppAskForApproval?
    var effectiveSandboxPolicy: AppSandboxPolicy?
    var isReasoningEffortLocked = false
    var showsBackground = true
    @Environment(AppModel.self) private var appModel
    @Environment(AppState.self) private var appState
    @AppStorage("fastMode") private var fastMode = false
    @State private var modelSearchQuery = ""
    @State private var modelSearchIndex = ModelSearchIndex()
    @State private var selectedRuntimeFilter: AgentRuntimeKind?
    @State private var initializedRuntimeFilter = false
    var onDismiss: () -> Void

    private var activeModelSearchIndex: ModelSearchIndex {
        if modelSearchIndex.isEmpty, !runtimeScopedModels.isEmpty {
            return ModelSearchIndex(models: runtimeScopedModels)
        }
        return modelSearchIndex
    }

    private var visibleModels: [ModelInfo] {
        models.filter(isVisibleModelOption)
    }

    private var runtimeBuckets: [RuntimeModelBucket] {
        runtimeModelBuckets(for: visibleModels)
    }

    private var activeRuntimeFilter: AgentRuntimeKind? {
        guard let selectedRuntimeFilter,
              runtimeBuckets.contains(where: { $0.kind == selectedRuntimeFilter }) else {
            return nil
        }
        return selectedRuntimeFilter
    }

    private var runtimeScopedModels: [ModelInfo] {
        guard let activeRuntimeFilter else { return visibleModels }
        return visibleModels.filter { $0.agentRuntimeKind == activeRuntimeFilter }
    }

    private var currentModel: ModelInfo? {
        if let match = visibleModels.first(where: {
            modelMatchesSelection(
                $0,
                selectedModel,
                runtime: selectedAgentRuntimeKind
            )
        }) {
            return match
        }
        // When shown from the home composer, `selectedModel` may be empty
        // because the user hasn't picked yet. Fall back to the default
        // model so the reasoning effort row has something to render.
        return visibleModels.first(where: { $0.isDefault }) ?? visibleModels.first
    }

    /// Effective collaboration mode: live thread value when we have one,
    /// otherwise the pre-thread pending selection tracked on `appState`.
    private var effectiveCollaborationMode: AppModeKind {
        threadKey == nil ? appState.pendingCollaborationMode : collaborationMode
    }

    private var isFullAccess: Bool {
        let approval = appState.launchApprovalPolicy(for: threadKey) ?? effectiveApprovalPolicy
        let sandbox = appState.turnSandboxPolicy(for: threadKey) ?? effectiveSandboxPolicy
        return threadPermissionPreset(approvalPolicy: approval, sandboxPolicy: sandbox) == .fullAccess
    }

    private var selectedRuntimeSupportsPermissionOverrides: Bool {
        let runtime = selectedAgentRuntimeKind ?? currentModel?.agentRuntimeKind
        return runtime?.supportsThreadPermissionOverrides ?? true
    }

    var body: some View {
        let visibleModels = activeModelSearchIndex.results(matching: modelSearchQuery)
        let selectedModelIsAmp: Bool = {
            guard let model = currentModel else { return false }
            return visibleModeNames(for: model.agentRuntimeKind) != nil
        }()
        let effectiveReasoningEfforts = isReasoningEffortLocked ? [] : (currentModel?.supportedReasoningEfforts ?? [])

        VStack(spacing: 0) {
            modelSearchField
            runtimeFilterRow

            ScrollView {
                LazyVStack(spacing: 0) {
                    if self.visibleModels.isEmpty {
                        Text("Loading models...")
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 24)
                    } else if visibleModels.isEmpty {
                        Text("No matching models")
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 24)
                    }

                    let lastModelID = visibleModels.last?.id
                    ForEach(visibleModels) { model in
                        Button {
                            selectedModel = model.id
                            selectedAgentRuntimeKind = model.agentRuntimeKind
                            if isReasoningEffortLocked && visibleModeNames(for: model.agentRuntimeKind) != nil {
                                reasoningEffort = ""
                            } else {
                                reasoningEffort = defaultReasoningEffortSelection(for: model)
                            }
                            // Auto-dismiss only in the thread-scoped popover
                            // context. In the home sheet (no thread yet) we
                            // let the user pick a model AND change plan or
                            // permissions before hitting Done.
                            if threadKey != nil { onDismiss() }
                        } label: {
                            HStack {
                                ModelRuntimeIcon(kind: model.agentRuntimeKind)

                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 6) {
                                        Text(modelPickerDisplayName(model))
                                            .agentBuddyFont(.footnote)
                                            .foregroundColor(AgentBuddyTheme.textPrimary)
                                        if model.isDefault {
                                            Text("default")
                                                .agentBuddyFont(.caption2, weight: .medium)
                                                .foregroundColor(AgentBuddyTheme.accent)
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 1)
                                                .background(AgentBuddyTheme.accent.opacity(0.15))
                                                .clipShape(Capsule())
                                        }
                                    }
                                    Text(model.description)
                                        .agentBuddyFont(.caption2)
                                        .foregroundColor(AgentBuddyTheme.textSecondary)
                                }
                                Spacer()
                                if modelMatchesSelection(
                                    model,
                                    selectedModel,
                                    runtime: selectedAgentRuntimeKind
                                ) {
                                    Image(systemName: "checkmark")
                                        .agentBuddyFont(size: 12, weight: .medium)
                                        .foregroundColor(AgentBuddyTheme.accent)
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                        }
                        if model.id != lastModelID {
                            Divider().background(AgentBuddyTheme.separator).padding(.leading, 16)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            if isReasoningEffortLocked && selectedModelIsAmp {
                Divider().background(AgentBuddyTheme.separator).padding(.horizontal, 12)

                Text("Reasoning effort is locked after the first message.")
                    .agentBuddyFont(.caption2)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
            } else if !effectiveReasoningEfforts.isEmpty {
                Divider().background(AgentBuddyTheme.separator).padding(.horizontal, 12)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(effectiveReasoningEfforts) { effort in
                            Button {
                                reasoningEffort = effort.reasoningEffort.wireValue
                                onDismiss()
                            } label: {
                                Text(effort.reasoningEffort.wireValue)
                                    .agentBuddyFont(.caption2, weight: .medium)
                                    .foregroundColor(effort.reasoningEffort.wireValue == reasoningEffort ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textPrimary)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(effort.reasoningEffort.wireValue == reasoningEffort ? AgentBuddyTheme.accent : AgentBuddyTheme.surfaceLight)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
            }

            Divider().background(AgentBuddyTheme.separator).padding(.horizontal, 12)

            HStack(spacing: 6) {
                Button {
                    let current = effectiveCollaborationMode
                    let next: AppModeKind = current == .plan ? .default : .plan
                    if let threadKey {
                        Task {
                            try? await appModel.store.setThreadCollaborationMode(
                                key: threadKey, mode: next
                            )
                        }
                    } else {
                        appState.pendingCollaborationMode = next
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "doc.text")
                            .agentBuddyFont(size: 9, weight: .semibold)
                        Text("Plan")
                            .agentBuddyFont(.caption2, weight: .medium)
                    }
                    .foregroundColor(effectiveCollaborationMode == .plan ? .black : AgentBuddyTheme.textPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(effectiveCollaborationMode == .plan ? AgentBuddyTheme.accent : AgentBuddyTheme.surfaceLight)
                    .clipShape(Capsule())
                }

                Button {
                    fastMode.toggle()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "bolt.fill")
                            .agentBuddyFont(size: 9, weight: .semibold)
                        Text("Fast")
                            .agentBuddyFont(.caption2, weight: .medium)
                    }
                    .foregroundColor(fastMode ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(fastMode ? AgentBuddyTheme.warning : AgentBuddyTheme.surfaceLight)
                    .clipShape(Capsule())
                }

                if selectedRuntimeSupportsPermissionOverrides {
                    Button {
                        if isFullAccess {
                            appState.setPermissions(approvalPolicy: "on-request", sandboxMode: "workspace-write", for: threadKey)
                        } else {
                            appState.setPermissions(approvalPolicy: "never", sandboxMode: "danger-full-access", for: threadKey)
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: isFullAccess ? "lock.open.fill" : "lock.fill")
                                .agentBuddyFont(size: 9, weight: .semibold)
                            Text(isFullAccess ? "Full Access" : "Supervised")
                                .agentBuddyFont(.caption2, weight: .medium)
                        }
                        .foregroundColor(isFullAccess ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textPrimary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(isFullAccess ? AgentBuddyTheme.danger : AgentBuddyTheme.surfaceLight)
                        .clipShape(Capsule())
                    }
                }

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(showsBackground ? AgentBuddyTheme.surface : Color.clear)
        .onAppear {
            synchronizeRuntimeFilter()
            resetModelSearchIndex()
        }
        .onChange(of: models) { _, newModels in
            synchronizeRuntimeFilter()
            modelSearchIndex = ModelSearchIndex(models: newModels.filter(isVisibleModelOption).filtered(by: activeRuntimeFilter))
        }
        .onChange(of: selectedRuntimeFilter) { _, _ in
            resetModelSearchIndex()
        }
        .onChange(of: selectedAgentRuntimeKind) { _, _ in
            synchronizeRuntimeFilter()
        }
    }

    private var modelSearchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AgentBuddyTheme.textMuted)
            TextField("Search models", text: $modelSearchQuery)
                .agentBuddyFont(.caption)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .tint(AgentBuddyTheme.accent)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !modelSearchQuery.isEmpty {
                Button { modelSearchQuery = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private var runtimeFilterRow: some View {
        if runtimeBuckets.count > 1 {
            RuntimeFilterRow(
                buckets: runtimeBuckets,
                totalCount: visibleModels.count,
                selectedRuntime: activeRuntimeFilter,
                onSelect: { selectedRuntimeFilter = $0 }
            )
            .padding(.bottom, 6)
        }
    }

    private func resetModelSearchIndex() {
        modelSearchIndex = ModelSearchIndex(models: runtimeScopedModels)
    }

    private func synchronizeRuntimeFilter() {
        if !initializedRuntimeFilter {
            let initial = selectedAgentRuntimeKind ?? currentModel?.agentRuntimeKind
            if let initial, runtimeBuckets.contains(where: { $0.kind == initial }) {
                selectedRuntimeFilter = initial
            }
            initializedRuntimeFilter = true
            return
        }
        if let selectedRuntimeFilter,
           !runtimeBuckets.contains(where: { $0.kind == selectedRuntimeFilter }) {
            self.selectedRuntimeFilter = nil
        }
    }
}
