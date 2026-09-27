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
            Text("Local Codex isn't running. ChatGPT login and API key entry require the local bridge.")
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.textMuted)
                .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
        } header: {
            Text("Account")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
    }
}
