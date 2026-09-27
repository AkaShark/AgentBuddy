import SwiftUI

extension SettingsView {
    // MARK: - Account Section (inline, no nested sheet)

    var accountSection: some View {
        Group {
            if let localServer {
                SettingsConnectionAccountSection(server: localServer)
            } else {
                SettingsDisconnectedAccountSection()
            }
        }
    }
}

private struct SettingsDisconnectedAccountSection: View {
    var body: some View {
        Section {
            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.sm) {
                Image(systemName: "info.circle")
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
                Text("Local Codex isn't running. ChatGPT login and API key entry require the local bridge.")
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, BuddySpacing.xxs)
            .settingsMintRow()
        } header: {
            Text("Account")
                .settingsMintHeader()
        }
    }
}
