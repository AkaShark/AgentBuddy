import SwiftUI

/// Composer model sheet: partner filter, model list, reasoning effort and
/// the Fast switch, in one scrolling column.
struct ModelSelectorSheet: View {
    let models: [ModelInfo]
    @Binding var selectedModel: String
    @Binding var selectedAgentRuntimeKind: AgentRuntimeKind?
    @Binding var reasoningEffort: String
    var isReasoningEffortLocked = false
    @AppStorage("fastMode") private var fastMode = false
    @State private var modelSearchQuery = ""
    @State private var modelSearchIndex = ModelSearchIndex()
    @State private var selectedRuntimeFilter: AgentRuntimeKind?
    @State private var initializedRuntimeFilter = false

    private let gutter = BuddySpacing.xl

    private var currentModel: ModelInfo? {
        visibleModels.first {
            modelMatchesSelection(
                $0,
                selectedModel,
                runtime: selectedAgentRuntimeKind
            )
        }
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

    private var activeModelSearchIndex: ModelSearchIndex {
        if modelSearchIndex.isEmpty, !runtimeScopedModels.isEmpty {
            return ModelSearchIndex(models: runtimeScopedModels)
        }
        return modelSearchIndex
    }

    var body: some View {
        let visibleModels = activeModelSearchIndex.results(matching: modelSearchQuery)
        let selectedModelIsAmp: Bool = {
            guard let model = currentModel else { return false }
            return visibleModeNames(for: model.agentRuntimeKind) != nil
        }()
        let effectiveReasoningEfforts = isReasoningEffortLocked ? [] : (currentModel?.supportedReasoningEfforts ?? [])

        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text("Partner and model")
                    .buddyText(.title)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.horizontal, gutter)
                    .padding(.bottom, BuddySpacing.md)

                ModelPickerSearchField(query: $modelSearchQuery)
                    .padding(.horizontal, gutter)
                    .padding(.bottom, BuddySpacing.sm)

                ModelPickerPartnerSection(
                    buckets: runtimeBuckets,
                    totalCount: self.visibleModels.count,
                    selectedRuntime: activeRuntimeFilter,
                    contentInset: gutter,
                    onSelect: { selectedRuntimeFilter = $0 }
                )
                .padding(.bottom, BuddySpacing.sm)

                ModelPickerSectionLabel(title: "Model")
                    .padding(.horizontal, gutter)

                if self.visibleModels.isEmpty {
                    statusText("Loading models...")
                } else if visibleModels.isEmpty {
                    statusText("No matching models")
                }

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
                        let usesModes = visibleModeNames(for: model.agentRuntimeKind) != nil
                        if isReasoningEffortLocked && usesModes {
                            reasoningEffort = ""
                        } else {
                            reasoningEffort = defaultReasoningEffortSelection(for: model)
                        }
                    }
                    .padding(.horizontal, gutter)
                    BuddyDivider()
                        .padding(.leading, gutter + BuddySize.icon + BuddySpacing.sm)
                        .padding(.trailing, gutter)
                }

                if isReasoningEffortLocked && selectedModelIsAmp {
                    VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                        ModelPickerSectionLabel(title: "Reasoning effort")
                        ModelPickerLockedEffortNote()
                    }
                    .padding(.horizontal, gutter)
                    .padding(.top, BuddySpacing.lg)
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
                        }
                    }
                    .padding(.top, BuddySpacing.lg)
                }

                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                    ModelPickerSectionLabel(title: "Mode and access")
                    ModelPickerToggleChip(
                        title: "Fast",
                        systemImage: "bolt.fill",
                        isOn: fastMode,
                        hint: Text("Uses the faster service tier."),
                        action: { fastMode.toggle() }
                    )
                }
                .padding(.horizontal, gutter)
                .padding(.top, BuddySpacing.lg)
                .padding(.bottom, BuddySpacing.xl)
            }
        }
        .padding(.top, BuddySpacing.lg)
        .background(AgentBuddyTheme.surface)
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
            .padding(.horizontal, gutter)
            .padding(.vertical, BuddySpacing.xl)
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
