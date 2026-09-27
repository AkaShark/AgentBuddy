import SwiftUI

extension SettingsView {
    // MARK: - Servers Section

    var serversSection: some View {
        Section {
            if connectedServers.isEmpty {
                Text("No servers connected")
                    .agentBuddyFont(.footnote)
                    .foregroundColor(AgentBuddyTheme.textMuted)
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            } else {
                ForEach(connectedServers, id: \.id) { conn in
                    HStack {
                        Button {
                            activeServerSheet = .edit(conn)
                        } label: {
                            HStack {
                                Image(systemName: conn.isLocal ? "iphone" : "server.rack")
                                    .foregroundColor(AgentBuddyTheme.accent)
                                    .frame(width: 20)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(LocalizedStringKey(conn.displayName))
                                        .agentBuddyFont(.footnote)
                                        .foregroundColor(AgentBuddyTheme.textPrimary)
                                    Text(conn.health.displayLabel)
                                        .agentBuddyFont(.caption)
                                        .foregroundColor(conn.health.accentColor)
                                }
                                Spacer()
                            }
                        }
                        .buttonStyle(.plain)
                        Button("Remove") {
                            removeServer(conn)
                        }
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.danger)
                        .buttonStyle(.borderless)
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                }
            }

            Button {
                activeServerSheet = .add
            } label: {
                HStack {
                    Image(systemName: "plus.circle.fill")
                        .foregroundColor(AgentBuddyTheme.accent)
                        .frame(width: 20)
                    Text("Add Server")
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.accent)
                    Spacer()
                }
            }
            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
        } header: {
            Text("Servers")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
    }

    private func removeServer(_ server: HomeDashboardServer) {
        SavedServerStore.remove(serverId: server.id)
        Task { await SshSessionStore.shared.close(serverId: server.id, ssh: appModel.ssh) }
        appModel.serverBridge.disconnectServer(serverId: server.id)
    }
}
