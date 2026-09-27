import SwiftUI

extension SettingsView {
    // MARK: - Conversation Section

    var conversationSection: some View {
        Section {
            Toggle(isOn: $collapseTurns) {
                SettingsMintRowLabel(
                    "Collapse Turns",
                    subtitle: "Collapse previous turns into cards",
                    systemImage: "rectangle.compress.vertical"
                )
            }
            .tint(AgentBuddyTheme.action)
            .settingsMintRow()

            Toggle(isOn: Binding(
                get: { homeZoomLevel >= 3 },
                set: { homeZoomLevel = $0 ? 4 : 2 }
            )) {
                SettingsMintRowLabel(
                    "Show task details on Home",
                    subtitle: "Show the latest step under each task",
                    systemImage: "list.bullet.rectangle"
                )
            }
            .tint(AgentBuddyTheme.action)
            .settingsMintRow()

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
                .settingsMintHeader()
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
            SettingsMintRowLabel(title, subtitle: subtitle, systemImage: systemImage)
        }
        .pickerStyle(.menu)
        .tint(AgentBuddyTheme.link)
        .settingsMintRow()
    }
}
