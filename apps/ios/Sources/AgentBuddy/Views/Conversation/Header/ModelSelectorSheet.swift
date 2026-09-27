import SwiftUI

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
            LazyVStack(spacing: 0) {
                modelSearchField
                runtimeFilterRow

                if self.visibleModels.isEmpty {
                    Text("Loading models...")
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 24)
                } else if visibleModels.isEmpty {
                    Text("No matching models")
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 24)
                }

                ForEach(visibleModels) { model in
                    Button {
                        selectedModel = model.id
                        selectedAgentRuntimeKind = model.agentRuntimeKind
                        let usesModes = visibleModeNames(for: model.agentRuntimeKind) != nil
                        if isReasoningEffortLocked && usesModes {
                            reasoningEffort = ""
                        } else {
                            reasoningEffort = defaultReasoningEffortSelection(for: model)
                        }
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
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                    }
                    Divider().background(AgentBuddyTheme.separator).padding(.leading, 20)
                }

                if isReasoningEffortLocked && selectedModelIsAmp {
                    Text("Reasoning effort is locked after the first message.")
                        .agentBuddyFont(.caption2)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                } else if !effectiveReasoningEfforts.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(effectiveReasoningEfforts) { effort in
                                Button {
                                    reasoningEffort = effort.reasoningEffort.wireValue
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
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                    }
                }

                Divider().background(AgentBuddyTheme.separator).padding(.leading, 20)

                HStack(spacing: 6) {
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
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)

            }
        }
        .padding(.top, 20)
        .background(.ultraThinMaterial)
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
                .agentBuddyFont(.body)
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
        .padding(.horizontal, 20)
        .padding(.bottom, 10)
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
            .padding(.bottom, 10)
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
