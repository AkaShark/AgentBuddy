import SwiftUI

/// "开始一个新想法": the new-task composer. Used as the phone new-task sheet
/// and as the iPad / Mac detail-pane root. Host, project and partner are
/// explicit chips above the composer; changing any of them keeps the draft.
///
/// On send the heading fades and the composer settles before the parent
/// swaps in the conversation (or dismisses the sheet), so the handoff reads
/// as one motion.
struct NewThreadHeroView: View {
    let project: AppProject?
    let connectedServers: [HomeDashboardServer]
    let selectedServerId: String?
    let onSelectServer: (String) -> Void
    let onOpenProjectPicker: () -> Void
    let onThreadCreated: (ThreadKey) -> Void
    /// When nil, no Cancel button is shown (used for the split-view detail
    /// pane root where there's nothing to cancel back to).
    var onCancel: (() -> Void)? = nil
    /// When false, the composer doesn't steal focus on appear. Used when
    /// the hero is the ambient detail-pane root so popping back from a
    /// conversation doesn't rudely summon the keyboard.
    var autoFocus: Bool = true

    @State private var isSending = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Delay between the composer firing `onThreadCreated` and the parent
    /// replacing the route, long enough for the settle animation.
    private static let morphSettleSeconds: UInt64 = 360_000_000

    private var launchableServers: [HomeDashboardServer] {
        connectedServers.filter(\.canLaunchSessions)
    }

    private var activeServerId: String? {
        project?.serverId ?? selectedServerId
    }

    private var selectedLaunchableServer: HomeDashboardServer? {
        guard let activeServerId else { return nil }
        return launchableServers.first { $0.id == activeServerId }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.lg) {
            if !isSending {
                BuddyPageHeader(
                    title: "Start a new idea",
                    subtitle: Text("Describe what you want done. AgentBuddy starts on the host you pick and keeps you posted."),
                    titleStyle: .title
                )
                .transition(.opacity.combined(with: .move(edge: .top)))

                contextChips
                    .transition(.opacity)
            }

            HomeComposerView(
                project: project,
                transcriptionServerId: activeServerId,
                onThreadCreated: { key in
                    withAnimation(reduceMotion ? nil : .spring(response: 0.5, dampingFraction: 0.85)) {
                        isSending = true
                    }
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: Self.morphSettleSeconds)
                        onThreadCreated(key)
                    }
                },
                autoFocus: autoFocus
            )
            .padding(.horizontal, -BuddySpacing.md)

            if project == nil, !launchableServers.isEmpty, !isSending {
                BuddyBanner(
                    tone: .info,
                    message: Text("Pick a project so AgentBuddy knows which folder to work in."),
                    systemImage: "folder",
                    actionTitle: "Choose project",
                    action: onOpenProjectPicker
                )
            }

            Spacer(minLength: 0)
        }
        .frame(maxWidth: 760, alignment: .leading)
        .padding(.horizontal, BuddySpacing.xl)
        .padding(.top, BuddySpacing.md)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .buddyPageBackground()
        .animation(reduceMotion ? nil : .spring(response: 0.5, dampingFraction: 0.85), value: isSending)
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let onCancel {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { onCancel() }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
    }

    // MARK: - Context chips

    private var contextChips: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: BuddySpacing.xs) { chips }
            VStack(alignment: .leading, spacing: 0) { chips }
        }
    }

    @ViewBuilder
    private var chips: some View {
        serverChip
        ProjectChip(
            project: project,
            disabled: launchableServers.isEmpty,
            onTap: onOpenProjectPicker
        )
        HomeModelChip(
            serverId: activeServerId,
            disabled: selectedLaunchableServer == nil
        )
    }

    private var serverChip: some View {
        Menu {
            if launchableServers.isEmpty {
                Text("No hosts connected")
            } else {
                ForEach(launchableServers, id: \.id) { server in
                    Button {
                        onSelectServer(server.id)
                    } label: {
                        if server.id == activeServerId {
                            Label(server.displayName, systemImage: "checkmark")
                        } else {
                            Text(verbatim: server.displayName)
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "laptopcomputer")
                    .font(.system(size: 13, weight: .medium))
                    .accessibilityHidden(true)
                Text(verbatim: selectedLaunchableServer?.displayName ?? String(localized: "Choose host"))
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
            }
            .buddyContextChip(isEnabled: !launchableServers.isEmpty)
        }
        .disabled(launchableServers.isEmpty)
        .accessibilityLabel(Text("Host: \(selectedLaunchableServer?.displayName ?? String(localized: "Choose host"))"))
    }
}
