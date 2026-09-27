import SwiftUI

extension DiscoveryView {
    // MARK: - Sections (legacy discovery list, retained for sheet plumbing)

    private var allServers: [DiscoveredServer] {
        localServers + networkServers
    }

    private var serversSection: some View {
        Section {
            if allServers.isEmpty {
                if discovery.isInitialLoad {
                    HStack {
                        ProgressView().tint(AgentBuddyTheme.textMuted).scaleEffect(0.7)
                        Text("Scanning...")
                            .agentBuddyFont(.footnote)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("No servers found")
                            .agentBuddyFont(.footnote)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                        if discovery.isScanning {
                            Text("Still searching network...")
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                        }
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                }
            } else {
                ForEach(allServers) { server in
                    serverRow(server)
                }
            }

            if let notice = discovery.tailscaleDiscoveryNotice {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "network.slash")
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                        .frame(width: 18, alignment: .top)
                    Text(notice)
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
                .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            }
        } header: {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Text("Servers")
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                    Spacer()
                    if discovery.isScanning, let label = discovery.scanProgressLabel {
                        Text(label)
                            .agentBuddyFont(.caption2)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                    }
                }
                if discovery.isScanning {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(AgentBuddyTheme.surface)
                                .frame(height: 3)
                            Capsule()
                                .fill(AgentBuddyTheme.accent)
                                .frame(
                                    width: geo.size.width * CGFloat(discovery.scanProgress),
                                    height: 3
                                )
                                .animation(.easeInOut(duration: 0.25), value: discovery.scanProgress)
                        }
                    }
                    .frame(height: 3)
                }
            }
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    // MARK: - Row

    private func serverRow(_ server: DiscoveredServer) -> some View {
        let rowIdentifier = serverRowAccessibilityIdentifier(for: server)
        let serverSnapshot = appModel.snapshot?.servers.first(where: { $0.serverId == server.id })
        return Button {
            handleTap(server)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: serverIconName(for: server))
                    .foregroundColor(server.hasCodexServer ? AgentBuddyTheme.accent : AgentBuddyTheme.textSecondary)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(server.name)
                        .agentBuddyFont(.subheadline)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                    Text(serverSubtitle(server))
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
                Spacer()
                if let progressTag = progressTag(for: serverSnapshot) {
                    statusTag(label: progressTag.label, color: progressTag.color)
                } else if let health = serverSnapshot?.health,
                          health != .disconnected {
                    statusTag(label: health.displayLabel.lowercased(), color: health.accentColor)
                } else if connectingServer?.id == server.id {
                    ProgressView().controlSize(.small).tint(AgentBuddyTheme.accent)
                } else if wakingServer?.id == server.id {
                    ProgressView().controlSize(.small).tint(AgentBuddyTheme.accent)
                } else {
                    Image(systemName: "chevron.right")
                        .foregroundColor(AgentBuddyTheme.textMuted)
                        .font(.caption)
                }
            }
        }
        .accessibilityIdentifier(rowIdentifier)
        .disabled(connectingServer != nil || wakingServer != nil)
        .contextMenu {
            if server.source != .local {
                Button {
                    renameText = server.name
                    renameTarget = server
                } label: {
                    Label("Rename", systemImage: "pencil")
                }
            }
        }
    }

    private func serverRowAccessibilityIdentifier(for server: DiscoveredServer) -> String {
        let kind = server.hasCodexServer ? "codex" : "ssh"
        let host = server.hostname
            .lowercased()
            .replacingOccurrences(of: ".", with: "_")
            .replacingOccurrences(of: ":", with: "_")
            .replacingOccurrences(of: " ", with: "_")
        return "discovery.server.\(kind).\(host)"
    }

    private func serverSubtitle(_ server: DiscoveredServer) -> String {
        if server.source == .local { return "In-process server" }
        let snapshot = connectedSnapshot(for: server)
        if let progressDetail = snapshot?.connectionProgressDetail,
           !progressDetail.isEmpty {
            return progressDetail
        }
        let displayHost = snapshot?.host.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            ? snapshot!.host
            : server.hostname
        var parts = [displayHost]
        if let os = server.os {
            parts.append(" - \(os)")
        }
        let directPorts = server.availableDirectCodexPorts.map(String.init)
        if !directPorts.isEmpty {
            parts.append(" - codex \(directPorts.joined(separator: ", "))")
        }
        if server.canConnectViaSSH {
            parts.append(" - ssh \(server.resolvedSSHPort)")
        }
        return parts.joined()
    }

    @ViewBuilder
    func statusTag(label: String, color: Color) -> some View {
        Text(label)
            .agentBuddyFont(.caption2)
            .foregroundColor(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.15))
            .cornerRadius(4)
    }

    private func connectedSnapshot(for server: DiscoveredServer) -> AppServerSnapshot? {
        appModel.snapshot?.servers.first(where: { $0.serverId == server.id && !$0.isLocal })
    }

    private func progressTag(
        for serverSnapshot: AppServerSnapshot?
    ) -> (label: String, color: Color)? {
        guard let serverSnapshot,
              let label = serverSnapshot.connectionProgressLabel,
              let step = serverSnapshot.currentConnectionStep else {
            return nil
        }

        let color: Color
        switch step.state {
        case .failed:
            color = .red
        case .completed where step.kind == .connected:
            color = AgentBuddyTheme.accentStrong
        case .awaitingUserInput:
            color = .orange
        default:
            color = AgentBuddyTheme.accent
        }

        return (label, color)
    }
}
