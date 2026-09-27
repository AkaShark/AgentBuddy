import SwiftUI

/// Home-row look: status tile, heading title (two lines), label subtitle
/// with project · status/updated time, then host · partner.
struct ThreadSearchRowContent<Accessory: View>: View {
    let session: HomeDashboardRecentSession
    let updatedAt: Date
    @ViewBuilder var accessory: () -> Accessory

    var body: some View {
        HStack(alignment: .center, spacing: BuddySpacing.md) {
            statusTile
            VStack(alignment: .leading, spacing: 2) {
                FormattedText(text: session.sessionTitle, lineLimit: 2)
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .multilineTextAlignment(.leading)
                Text(verbatim: subtitle)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            accessory()
        }
        .padding(.vertical, BuddySpacing.sm)
        .frame(minHeight: 64)
        .contentShape(Rectangle())
    }

    private var statusTile: some View {
        let state = HomeTaskPresentation.state(for: session, approvalKeys: [], inputKeys: [], cancellingKeys: [])
        let isQuiet = state == .completed || state == .idle
        return BuddyIconTile(
            content: .symbol(state.systemImage),
            fill: isQuiet ? AgentBuddyTheme.surface : state.fill,
            foreground: isQuiet ? AgentBuddyTheme.textSecondary : state.foreground
        )
        .overlay {
            RoundedRectangle(cornerRadius: BuddyRadius.tile, style: .continuous)
                .strokeBorder(AgentBuddyTheme.border, lineWidth: isQuiet ? 1 : 0)
        }
    }

    private var subtitle: String {
        var first: [String] = []
        if let workspace = HomeDashboardSupport.workspaceLabel(for: session.cwd) {
            first.append(workspace)
        }
        first.append(session.hasTurnActive
            ? String(localized: "Running")
            : String(localized: "Updated \(HomeTaskPresentation.relativeTime(updatedAt))"))
        let second = [session.serverDisplayName, session.agentRuntimeKind.displayLabel].filter { !$0.isEmpty }
        return ([first.joined(separator: " · ")] + (second.isEmpty ? [] : [second.joined(separator: " · ")]))
            .joined(separator: "\n")
    }
}

/// "On Home" indicator: a checkmark when the task is already on the home
/// list, a plus when it can be added. Shape changes with the state.
struct ThreadSearchPinIcon: View {
    let isPinned: Bool

    var body: some View {
        Image(systemName: isPinned ? "checkmark.circle.fill" : "plus.circle")
            .font(.system(size: 20, weight: .medium))
            .foregroundStyle(isPinned ? AgentBuddyTheme.link : AgentBuddyTheme.textSecondary)
            .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
            .contentShape(Rectangle())
            .accessibilityHidden(true)
    }
}

/// Cluster row that collapses N sibling threads into a single visual unit.
/// Tapping the branches chip expands the children inline — each child has
/// its own pin button, so the user can still pin a specific branch.
struct ThreadSearchClusterRow: View {
    let cluster: ThreadSearchCluster
    let pinnedThreadKeys: Set<SavedThreadsStore.PinnedKey>
    let isExpanded: Bool
    let onToggleExpanded: () -> Void
    let onPin: (HomeDashboardRecentSession) -> Void
    let onUnpin: (HomeDashboardRecentSession) -> Void

    /// The cluster head represents the lineage's identity. Prefer the root
    /// thread (the original) so the head reads stable: forks come and go,
    /// the root is canonical. Fall back to the most-recent member when the
    /// root isn't loaded into the snapshot.
    private var head: HomeDashboardRecentSession? {
        cluster.members.first(where: { $0.key == cluster.rootKey })
            ?? cluster.members.first
    }

    /// Latest activity across the whole lineage — root or any fork. Used
    /// for the head row's "Nh ago" so the head reflects whether the
    /// lineage is fresh, even though its title is the (possibly older) root.
    private var headLatestUpdatedAt: Date {
        cluster.members.map(\.updatedAt).max() ?? Date(timeIntervalSince1970: 0)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let head { headRow(for: head) }
            if isExpanded {
                childrenList
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private func headRow(for session: HomeDashboardRecentSession) -> some View {
        let isPinned = pinnedThreadKeys.contains(SavedThreadsStore.PinnedKey(threadKey: session.key))
        return ThreadSearchRowContent(session: session, updatedAt: headLatestUpdatedAt) {
            HStack(spacing: 0) {
                branchesChip
                pinButton(isPinned: isPinned) {
                    isPinned ? onUnpin(session) : onPin(session)
                }
            }
        }
    }

    private var branchesChip: some View {
        Button(action: onToggleExpanded) {
            HStack(spacing: 4) {
                Image(systemName: "arrow.triangle.branch")
                    .imageScale(.small)
                    .accessibilityHidden(true)
                Text(verbatim: "\(cluster.members.count)")
                    .monospacedDigit()
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .accessibilityHidden(true)
            }
            .buddyContextChip()
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(cluster.members.count) branches")
        .accessibilityValue(Text(isExpanded ? "Expanded" : "Collapsed"))
    }

    private var childrenList: some View {
        // Skip the cluster's head — it's already shown by `headRow`. Showing
        // it again in the branches list reads as a duplicate. The root and
        // every other sibling stay visible so any branch is still pinnable.
        let headKey = head?.key
        let otherMembers = cluster.members.filter { $0.key != headKey }
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(otherMembers) { member in
                let isPinned = pinnedThreadKeys.contains(SavedThreadsStore.PinnedKey(threadKey: member.key))
                let isRoot = member.key == cluster.rootKey
                Button(action: { isPinned ? onUnpin(member) : onPin(member) }) {
                    HStack(alignment: .center, spacing: BuddySpacing.sm) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(alignment: .firstTextBaseline, spacing: 6) {
                                FormattedText(text: branchLabel(for: member, isRoot: isRoot), lineLimit: 2)
                                    .buddyText(.body, weight: isPinned ? .semibold : .regular)
                                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                                    .multilineTextAlignment(.leading)
                                if isRoot {
                                    SessionsRowTag(title: Text("root"))
                                }
                            }
                            Text(verbatim: String(localized: "Updated \(HomeTaskPresentation.relativeTime(member.updatedAt))"))
                                .buddyText(.caption)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        pinButton(isPinned: isPinned) {
                            isPinned ? onUnpin(member) : onPin(member)
                        }
                    }
                    .padding(.leading, BuddySize.rowTile + BuddySpacing.md)
                    .padding(.vertical, BuddySpacing.xxs)
                    .frame(minHeight: BuddySize.control)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(isPinned ? Text("On Home") : Text(verbatim: ""))
            }
        }
        .padding(.bottom, BuddySpacing.xs)
        .overlay(alignment: .leading) {
            // Lineage guide under the head tile.
            Rectangle()
                .fill(AgentBuddyTheme.border)
                .frame(width: 1)
                .padding(.leading, BuddySize.rowTile / 2)
                .padding(.bottom, BuddySpacing.xs)
                .accessibilityHidden(true)
        }
    }

    private func pinButton(isPinned: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ThreadSearchPinIcon(isPinned: isPinned)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(isPinned ? "Remove from Home" : "Add to Home"))
    }

    /// Codex auto-titles threads from the *first* user message, which is
    /// shared up to the fork point — so two siblings often have identical
    /// titles. Inside a cluster the user needs a distinguisher: the
    /// most-recent user message (the divergent prompt) when it actually
    /// differs from the title. Fall back to the title for the root and
    /// for forks whose latest prompt hasn't diverged yet.
    private func branchLabel(for member: HomeDashboardRecentSession, isRoot: Bool) -> String {
        if isRoot { return member.sessionTitle }
        let lastUser = (member.lastUserMessage ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if lastUser.isEmpty { return member.sessionTitle }
        let normalize: (String) -> String = { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        if normalize(lastUser) == normalize(member.sessionTitle) {
            return member.sessionTitle
        }
        return lastUser
    }
}
