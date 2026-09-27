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
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Loading modes…")
                            .agentBuddyFont(.body)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                    .listRowBackground(AgentBuddyTheme.surface)
                }

                ForEach(presets, id: \.kind) { preset in
                    Button(action: { onSelect(preset.kind) }) {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(preset.name)
                                    .agentBuddyFont(.body, weight: .semibold)
                                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                                if let reasoningEffort = preset.reasoningEffort {
                                    Text(collaborationModeEffortLabel(reasoningEffort))
                                        .agentBuddyFont(.caption)
                                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                                }
                            }
                            Spacer()
                            if preset.kind == selectedMode {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(AgentBuddyTheme.accent)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(AgentBuddyTheme.surface)
                }
            }
            .scrollContentBackground(.hidden)
            .background(AgentBuddyTheme.surface)
            .navigationTitle("Collaboration Mode")
        }
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
