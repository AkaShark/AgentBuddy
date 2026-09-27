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
                    HStack(spacing: BuddySpacing.sm) {
                        ProgressView().tint(AgentBuddyTheme.textSecondary)
                        Text("Scanning...")
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                    .listRowBackground(AgentBuddyTheme.surface)
                } else {
                    VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                        Text("No servers found")
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                        if discovery.isScanning {
                            Text("Still searching network...")
                                .buddyText(.caption)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                        }
                    }
                    .listRowBackground(AgentBuddyTheme.surface)
                }
            } else {
                ForEach(allServers) { server in
                    serverRow(server)
                }
            }

            if let notice = discovery.tailscaleDiscoveryNotice {
                HStack(alignment: .top, spacing: BuddySpacing.sm) {
                    Image(systemName: "network.slash")
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: 20, alignment: .top)
                        .accessibilityHidden(true)
                    Text(notice)
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                .listRowBackground(AgentBuddyTheme.surface)
            }
        } header: {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: BuddySpacing.xs) {
                    Text("Servers")
                        .buddyText(.caption, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                    Spacer()
                    if discovery.isScanning, let label = discovery.scanProgressLabel {
                        Text(label)
                            .buddyText(.caption)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                }
                if discovery.isScanning {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(AgentBuddyTheme.surfaceSoft)
                                .frame(height: 3)
                            Capsule()
                                .fill(AgentBuddyTheme.action)
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
        .listRowBackground(AgentBuddyTheme.surface)
    }

    // MARK: - Row

    private func serverRow(_ server: DiscoveredServer) -> some View {
        let rowIdentifier = serverRowAccessibilityIdentifier(for: server)
        let serverSnapshot = appModel.snapshot?.servers.first(where: { $0.serverId == server.id })
        return Button {
            handleTap(server)
        } label: {
            HStack(spacing: BuddySpacing.md) {
                BuddyIconTile(
                    content: .symbol(serverIconName(for: server)),
                    foreground: server.hasCodexServer ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary
                )
                VStack(alignment: .leading, spacing: 2) {
                    Text(server.name)
                        .buddyText(.heading)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                    Text(serverSubtitle(server))
                        .buddyText(.label, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                Spacer()
                if let progressTag = progressTag(for: serverSnapshot) {
                    statusTag(label: progressTag.label, color: progressTag.color)
                } else if let health = serverSnapshot?.health,
                          health != .disconnected {
                    statusTag(label: health.displayLabel.lowercased(), color: healthDotColor(health))
                } else if connectingServer?.id == server.id {
                    ProgressView().controlSize(.small).tint(AgentBuddyTheme.textSecondary)
                } else if wakingServer?.id == server.id {
                    ProgressView().controlSize(.small).tint(AgentBuddyTheme.textSecondary)
                } else {
                    Image(systemName: "chevron.right")
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .font(.system(size: 15, weight: .semibold))
                        .accessibilityHidden(true)
                }
            }
            .frame(minHeight: 64)
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

    /// Dot + text status capsule; the text carries the state, the dot only
    /// reinforces it.
    @ViewBuilder
    func statusTag(label: String, color: Color) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
                .accessibilityHidden(true)
            Text(label)
                .buddyText(.caption, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
        }
        .padding(.horizontal, BuddySpacing.sm)
        .frame(minHeight: 28)
        .background(AgentBuddyTheme.surfaceSoft, in: Capsule())
    }

    private func healthDotColor(_ health: AppServerHealth) -> Color {
        switch health {
        case .connected: return AgentBuddyTheme.success
        case .connecting, .unresponsive: return AgentBuddyTheme.warning
        case .disconnected, .unknown: return AgentBuddyTheme.textSecondary
        }
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
            color = AgentBuddyTheme.danger
        case .completed where step.kind == .connected:
            color = AgentBuddyTheme.success
        case .awaitingUserInput:
            color = AgentBuddyTheme.warning
        default:
            color = AgentBuddyTheme.link
        }

        return (label, color)
    }
}
