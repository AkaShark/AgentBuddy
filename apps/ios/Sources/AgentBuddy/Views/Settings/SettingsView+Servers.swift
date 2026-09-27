import SwiftUI

extension SettingsView {
    // MARK: - Servers Section

    var serversSection: some View {
        Section {
            if connectedServers.isEmpty {
                Text("No servers connected")
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .padding(.vertical, BuddySpacing.xxs)
                    .settingsMintRow()
            } else {
                ForEach(connectedServers, id: \.id) { conn in
                    HStack(spacing: BuddySpacing.sm) {
                        Button {
                            activeServerSheet = .edit(conn)
                        } label: {
                            HStack(spacing: BuddySpacing.sm) {
                                Image(systemName: conn.isLocal ? "iphone" : "server.rack")
                                    .font(.system(size: 17, weight: .medium))
                                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                                    .frame(width: 24)
                                    .accessibilityHidden(true)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(LocalizedStringKey(conn.displayName))
                                        .buddyText(.body)
                                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                                        .lineLimit(2)
                                    BuddyConnectionPill(
                                        state: settingsConnectionState(conn.health),
                                        title: Text(LocalizedStringKey(conn.health.displayLabel)),
                                        filled: false
                                    )
                                }
                                Spacer(minLength: BuddySpacing.xs)
                            }
                            .padding(.vertical, BuddySpacing.xxs)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(Text("Edit Server"))

                        Button {
                            removeServer(conn)
                        } label: {
                            Text("Remove")
                                .buddyText(.label, weight: .medium)
                                .foregroundStyle(AgentBuddyTheme.danger)
                                .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.borderless)
                    }
                    .settingsMintRow()
                }
            }

            Button {
                activeServerSheet = .add
            } label: {
                HStack(spacing: BuddySpacing.sm) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 17, weight: .medium))
                        .frame(width: 24)
                        .accessibilityHidden(true)
                    Text("Add Server")
                        .buddyText(.body, weight: .medium)
                    Spacer()
                }
                .foregroundStyle(AgentBuddyTheme.link)
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .settingsMintRow()
        } header: {
            Text("Servers")
                .settingsMintHeader()
        }
    }

    private func settingsConnectionState(_ health: AppServerHealth) -> BuddyConnectionState {
        switch health {
        case .connected: return .connected
        case .connecting: return .connecting
        case .unresponsive: return .failed
        case .disconnected, .unknown: return .disconnected
        }
    }

    private func removeServer(_ server: HomeDashboardServer) {
        SavedServerStore.remove(serverId: server.id)
        Task { await SshSessionStore.shared.close(serverId: server.id, ssh: appModel.ssh) }
        appModel.serverBridge.disconnectServer(serverId: server.id)
    }
}
