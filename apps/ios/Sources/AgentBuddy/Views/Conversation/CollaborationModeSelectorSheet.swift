import SwiftUI

struct CollaborationModeSelectorSheet: View {
    let presets: [AppCollaborationModePreset]
    let selectedMode: AppModeKind
    let isLoading: Bool
    let onSelect: (AppModeKind) -> Void

    var body: some View {
        NavigationStack {
            List {
                if isLoading && presets.isEmpty {
                    HStack(spacing: BuddySpacing.sm) {
                        ProgressView()
                        Text("Loading modes…")
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                    .listRowBackground(AgentBuddyTheme.surface)
                }

                ForEach(presets, id: \.kind) { preset in
                    modeRow(preset)
                        .listRowBackground(AgentBuddyTheme.surface)
                }
            }
            .scrollContentBackground(.hidden)
            .buddyPageBackground()
            .navigationTitle("Collaboration Mode")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    /// Mode name, what it does and its reasoning effort. The current mode
    /// is marked with a checkmark, the word "Selected" and a heavier name.
    private func modeRow(_ preset: AppCollaborationModePreset) -> some View {
        let isSelected = preset.kind == selectedMode
        return Button(action: { onSelect(preset.kind) }) {
            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: preset.name)
                        .buddyText(.body, weight: isSelected ? .semibold : .regular)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                    Text(collaborationModeExplanation(preset.kind))
                        .buddyText(.label, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    if let reasoningEffort = preset.reasoningEffort {
                        Text(verbatim: collaborationModeEffortLabel(reasoningEffort))
                            .buddyText(.caption)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if isSelected {
                    Label("Selected", systemImage: "checkmark")
                        .buddyText(.label, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
            .frame(minHeight: BuddySize.control)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

private func collaborationModeExplanation(_ kind: AppModeKind) -> LocalizedStringKey {
    switch kind {
    case .default:
        return "Works on the task directly."
    case .plan:
        return "Proposes a plan before making changes."
    }
}

private func collaborationModeEffortLabel(_ effort: ReasoningEffort) -> String {
    switch effort {
    case .none:
        return "None"
    case .minimal:
        return "Minimal"
    case .low:
        return "Low"
    case .medium:
        return "Medium"
    case .high:
        return "High"
    case .xHigh:
        return "XHigh"
    case .max:
        return "Max"
    }
}
