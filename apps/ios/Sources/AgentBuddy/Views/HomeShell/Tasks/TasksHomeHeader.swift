import SwiftUI

/// Home header: wordmark, host status chip (scope + host management), and the
/// always-visible settings entry. Secondary launchers live in the "…" menu.
struct TasksHomeHeader: View {
    let servers: [HomeDashboardServer]
    let selectedServerId: String?
    let actions: HomeShellActions
    let onManageHosts: () -> Void
    var showsComposeButton = false

    private var connectedCount: Int {
        servers.filter { $0.health == .connected }.count
    }

    var body: some View {
        headerRow.buddyChromeTypeLimit()
    }

    private var headerRow: some View {
        HStack(spacing: BuddySpacing.xs) {
            BuddyWordmark(markSize: 36)
                .layoutPriority(0)
            Spacer(minLength: BuddySpacing.xs)
            hostChip
                .layoutPriority(1)
            if actions.showApps != nil || actions.showTerminal != nil {
                moreMenu
            }
            if showsComposeButton {
                BuddyIconButton(
                    systemImage: "square.and.pencil",
                    accessibilityLabel: "Start a new task",
                    tone: .plain,
                    iconSize: 19
                ) {
                    actions.newTask(nil)
                }
            }
            BuddyIconButton(
                systemImage: "gearshape",
                accessibilityLabel: "Settings",
                tone: .plain,
                iconSize: 19,
                action: actions.showSettings
            )
        }
    }

    // MARK: Host chip

    private var hostChip: some View {
        Menu {
            if servers.isEmpty {
                Button {
                    actions.pairWithQRCode()
                } label: {
                    Label("Pair with QR code", systemImage: "qrcode.viewfinder")
                }
            } else {
                Section("Show tasks from") {
                    Button {
                        actions.clearServerScope()
                    } label: {
                        if selectedServerId == nil {
                            Label("All hosts", systemImage: "checkmark")
                        } else {
                            Text("All hosts")
                        }
                    }
                    ForEach(servers) { server in
                        Button {
                            actions.selectServer(server)
                        } label: {
                            if server.id == selectedServerId {
                                Label(server.displayName, systemImage: "checkmark")
                            } else if server.canLaunchSessions {
                                Text(verbatim: server.displayName)
                            } else {
                                Label(String(localized: "Reconnect \(server.displayName)"), systemImage: "arrow.clockwise")
                            }
                        }
                    }
                }
            }
            Divider()
            Button(action: onManageHosts) {
                Label("Manage hosts", systemImage: "laptopcomputer")
            }
        } label: {
            HStack(spacing: 6) {
                Circle()
                    .fill(chipState.dotColor)
                    .frame(width: 8, height: 8)
                    .accessibilityHidden(true)
                chipTitle
                    .buddyText(.label, weight: .medium)
                    .lineLimit(1)
                    .fixedSize()
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
            }
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .padding(.horizontal, BuddySpacing.sm + 2)
            .frame(minHeight: 36)
            .background(AgentBuddyTheme.surfaceSoft, in: Capsule())
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .accessibilityLabel(chipTitle)
        .accessibilityHint(Text("Choose which host's tasks to show"))
    }

    private var chipState: BuddyConnectionState {
        if servers.isEmpty { return .disconnected }
        if connectedCount > 0 { return .connected }
        if servers.contains(where: { $0.health == .connecting }) { return .connecting }
        return .disconnected
    }

    private var chipTitle: Text {
        if servers.isEmpty {
            return Text("No host yet")
        }
        if let selectedServerId, let server = servers.first(where: { $0.id == selectedServerId }) {
            return Text(verbatim: server.displayName)
        }
        return Text("\(connectedCount) hosts online")
    }

    // MARK: More

    @ViewBuilder
    private var moreMenu: some View {
        Menu {
            if let showApps = actions.showApps {
                Button(action: showApps) {
                    Label("Apps", systemImage: "square.grid.2x2")
                }
            }
            if let showTerminal = actions.showTerminal {
                Button(action: showTerminal) {
                    Label("Terminal", systemImage: "terminal")
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.system(size: 19, weight: .regular))
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(Text("More"))
    }
}
