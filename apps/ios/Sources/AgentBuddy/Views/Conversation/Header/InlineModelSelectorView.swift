import SwiftUI

/// Partner, model and permission panel. Groups, top to bottom: search,
/// partner (runtime filter), model list, reasoning effort, then the
/// Plan / Fast / Full access toggles with a note on what the access level allows.
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

    private let gutter = BuddySpacing.md

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
            VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                ModelPickerSearchField(query: $modelSearchQuery)
                    .padding(.horizontal, gutter)
                ModelPickerPartnerSection(
                    buckets: runtimeBuckets,
                    totalCount: self.visibleModels.count,
                    selectedRuntime: activeRuntimeFilter,
                    contentInset: gutter,
                    onSelect: { selectedRuntimeFilter = $0 }
                )
                ModelPickerSectionLabel(title: "Model")
                    .padding(.horizontal, gutter)
            }
            .padding(.top, BuddySpacing.xs)

            ScrollView {
                LazyVStack(spacing: 0) {
                    if self.visibleModels.isEmpty {
                        statusText("Loading models...")
                    } else if visibleModels.isEmpty {
                        statusText("No matching models")
                    }

                    let lastModelID = visibleModels.last?.id
                    ForEach(visibleModels) { model in
                        ModelPickerRow(
                            model: model,
                            isSelected: modelMatchesSelection(
                                model,
                                selectedModel,
                                runtime: selectedAgentRuntimeKind
                            )
                        ) {
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
                        }
                        if model.id != lastModelID {
                            BuddyDivider().padding(.leading, BuddySize.icon + BuddySpacing.sm)
                        }
                    }
                }
                .padding(.horizontal, gutter)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            BuddyDivider()

            VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                if isReasoningEffortLocked && selectedModelIsAmp {
                    ModelPickerSectionLabel(title: "Reasoning effort")
                        .padding(.horizontal, gutter)
                    ModelPickerLockedEffortNote()
                        .padding(.horizontal, gutter)
                } else if !effectiveReasoningEfforts.isEmpty {
                    VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                        ModelPickerSectionLabel(title: "Reasoning effort")
                            .padding(.horizontal, gutter)
                        ModelPickerEffortChips(
                            efforts: effectiveReasoningEfforts,
                            selection: reasoningEffort,
                            contentInset: gutter
                        ) { value in
                            reasoningEffort = value
                            onDismiss()
                        }
                    }
                }

                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                    ModelPickerSectionLabel(title: "Mode and access")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: BuddySpacing.xs) {
                            ModelPickerToggleChip(
                                title: "Plan",
                                systemImage: "doc.text",
                                isOn: effectiveCollaborationMode == .plan,
                                hint: Text("Proposes a plan before making changes."),
                                action: togglePlanMode
                            )
                            ModelPickerToggleChip(
                                title: "Fast",
                                systemImage: "bolt.fill",
                                isOn: fastMode,
                                hint: Text("Uses the faster service tier."),
                                action: { fastMode.toggle() }
                            )
                            if selectedRuntimeSupportsPermissionOverrides {
                                ModelPickerToggleChip(
                                    title: "Full access",
                                    systemImage: isFullAccess ? "lock.open.fill" : "lock.fill",
                                    isOn: isFullAccess,
                                    isDanger: true,
                                    action: toggleFullAccess
                                )
                            }
                        }
                        .padding(.horizontal, gutter)
                    }
                    .padding(.horizontal, -gutter)
                    if selectedRuntimeSupportsPermissionOverrides {
                        ModelPickerAccessNote(isFullAccess: isFullAccess)
                    }
                }
                .padding(.horizontal, gutter)
            }
            .padding(.vertical, BuddySpacing.sm)
        }
        .padding(.vertical, BuddySpacing.xxs)
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

    private func statusText(_ text: LocalizedStringKey) -> some View {
        Text(text)
            .buddyText(.label, weight: .regular)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, BuddySpacing.xl)
    }

    /// Flips between plan and default on the thread, or on the pending
    /// pre-thread selection.
    private func togglePlanMode() {
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
    }

    private func toggleFullAccess() {
        if isFullAccess {
            appState.setPermissions(approvalPolicy: "on-request", sandboxMode: "workspace-write", for: threadKey)
        } else {
            appState.setPermissions(approvalPolicy: "never", sandboxMode: "danger-full-access", for: threadKey)
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
