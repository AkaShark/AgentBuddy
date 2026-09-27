import SwiftUI

/// Host card. `.featured` shows partners and a direct "start a task here"
/// action; `.compact` is a single summary block. Both expose the host
/// management menu through a visible "…" button.
struct HostCard<MenuItems: View>: View {
    enum Style { case featured, compact }

    let server: HomeDashboardServer
    let runningCount: Int
    let style: Style
    @ViewBuilder let menu: () -> MenuItems
    let onTap: () -> Void
    let onStartTask: () -> Void

    private var connection: BuddyConnectionState {
        switch server.health {
        case .connected: return .connected
        case .connecting: return .connecting
        case .unresponsive: return .failed
        case .disconnected: return .disconnected
        case .unknown: return .connecting
        }
    }

    private var iconName: String {
        server.isLocal ? "iphone" : "laptopcomputer"
    }

    private var availablePartners: [AgentRuntimeInfo] {
        server.agentRuntimes.filter(\.available)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            switch style {
            case .featured: featuredHeader
            case .compact: compactHeader
            }

            if style == .featured, !availablePartners.isEmpty {
                HStack(spacing: BuddySpacing.xs) {
                    ForEach(availablePartners, id: \.name) { runtime in
                        BuddyChip(runtime.kind.displayLabel)
                    }
                }
            }

            if style == .compact {
                Text(runningSummary)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }

            if style == .featured {
                BuddyButton(
                    "Start a task on this host",
                    trailingSystemImage: "arrow.up.right",
                    kind: .soft,
                    action: onStartTask
                )
                .disabled(!server.canLaunchSessions)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
        .contentShape(RoundedRectangle(cornerRadius: BuddyRadius.card, style: .continuous))
        .onTapGesture(perform: onTap)
        .contextMenu { menu() }
        .accessibilityElement(children: .contain)
        .accessibilityHint(Text(server.canLaunchSessions ? "Shows this host's tasks" : "Reconnects this host"))
    }

    private var featuredHeader: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            HStack(alignment: .top) {
                BuddyIconTile(content: .symbol(iconName), size: 52)
                Spacer()
                BuddyConnectionPill(state: connection)
                menuButton
            }
            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                Text(verbatim: server.displayName)
                    .buddyText(.title)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                Text(subtitle)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
        }
    }

    private var compactHeader: some View {
        HStack(spacing: BuddySpacing.md) {
            BuddyIconTile(content: .symbol(iconName), size: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: server.displayName)
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                HStack(spacing: 6) {
                    Circle().fill(connection.dotColor).frame(width: 8, height: 8)
                        .accessibilityHidden(true)
                    Text(HostSourcePresentation.title(for: server.sourceLabel))
                    Text(verbatim: "·")
                    Text(connection.title)
                }
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .lineLimit(1)
            }
            Spacer(minLength: 0)
            menuButton
        }
    }

    private var menuButton: some View {
        Menu {
            menu()
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(Text("Host actions"))
    }

    private var subtitle: LocalizedStringKey {
        HostSourcePresentation.title(for: server.sourceLabel)
    }

    private var runningSummary: LocalizedStringKey {
        runningCount > 0 ? "\(runningCount) running" : "No tasks running right now"
    }
}

/// Display names for the connection-mode keys in `HomeDashboardServer.sourceLabel`.
/// The keys themselves stay untranslated because other code compares them.
enum HostSourcePresentation {
    static func title(for sourceLabel: String) -> LocalizedStringKey {
        switch sourceLabel {
        case "local": return "This device"
        case "remote": return "Remote connection"
        case "alleycat": return "Peer-to-peer"
        case "ssh": return "SSH"
        case "bonjour": return "Local network"
        case "tailscale": return "Tailscale"
        case "manual": return "Manual address"
        default: return LocalizedStringKey(sourceLabel)
        }
    }
}
