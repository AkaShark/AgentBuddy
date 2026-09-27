import SwiftUI

extension SettingsView {
    // MARK: - Conversation Section

    var conversationSection: some View {
        Section {
            Toggle(isOn: $collapseTurns) {
                HStack(spacing: 10) {
                    Image(systemName: "rectangle.compress.vertical")
                        .foregroundColor(AgentBuddyTheme.accent)
                        .frame(width: 20)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Collapse Turns")
                            .agentBuddyFont(.subheadline)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                        Text("Collapse previous turns into cards")
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                }
            }
            .tint(AgentBuddyTheme.accent)
            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

            transcriptDisplayPicker(
                title: "Internal Thinking",
                subtitle: "Reasoning and analysis blocks",
                systemImage: "brain.head.profile",
                selection: $reasoningDisplayMode
            )

            transcriptDisplayPicker(
                title: "Commands",
                subtitle: "Shell commands and command output",
                systemImage: "terminal",
                selection: $commandDisplayMode
            )

            transcriptDisplayPicker(
                title: "Tools",
                subtitle: "MCP, web, image, and file-change cards",
                systemImage: "wrench.and.screwdriver",
                selection: $toolDisplayMode
            )
        } header: {
            Text("Conversation")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
    }

    private func transcriptDisplayPicker(
        title: LocalizedStringKey,
        subtitle: LocalizedStringKey,
        systemImage: String,
        selection: Binding<String>
    ) -> some View {
        Picker(selection: selection) {
            ForEach(ConversationDetailDisplayMode.allCases) { mode in
                Text(LocalizedStringKey(mode.displayName)).tag(mode.rawValue)
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .foregroundColor(AgentBuddyTheme.accent)
                    .frame(width: 20)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .agentBuddyFont(.subheadline)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                    Text(subtitle)
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
            }
        }
        .pickerStyle(.menu)
        .tint(AgentBuddyTheme.accent)
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }
}
