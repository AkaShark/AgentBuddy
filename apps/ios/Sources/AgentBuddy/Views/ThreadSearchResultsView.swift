import SwiftUI

/// Scrollable list of every thread across connected servers, sorted by
/// recency, filtered by the current query. Tapping a row calls `onAdd`.
/// Shows an indicator on rows already on the home list.
struct ThreadSearchResultsView: View {
    let sessions: [HomeDashboardRecentSession]
    let pinnedThreadKeys: Set<SavedThreadsStore.PinnedKey>
    let query: String
    let runtimeKinds: [AgentRuntimeKind]
    @Binding var selectedRuntimeKind: AgentRuntimeKind?
    var isLoading: Bool = false
    let onRefresh: () async -> Void
    let onAdd: (HomeDashboardRecentSession) -> Void
    let onRemove: (HomeDashboardRecentSession) -> Void
    /// Padding applied inside the scroll view so content can scroll under
    /// the floating top/bottom chrome. Caller passes the same values the
    /// tasks list uses so the search view feels like a drop-in replacement.
    var contentInsets: EdgeInsets = EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0)

    /// Tracks which fork lineages the user has expanded inline. Keyed by the
    /// lineage's root `ThreadKey`. Empty by default — clusters render
    /// collapsed.
    @State private var expandedClusters: Set<ThreadKey> = []
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var filtered: [HomeDashboardRecentSession] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return sessions.filter { session in
            if let selectedRuntimeKind, session.agentRuntimeKind != selectedRuntimeKind {
                return false
            }
            guard !trimmed.isEmpty else { return true }
            return session.sessionTitle.lowercased().contains(trimmed)
            || session.cwd.lowercased().contains(trimmed)
            || session.serverDisplayName.lowercased().contains(trimmed)
            || session.preview.lowercased().contains(trimmed)
        }
    }

    /// Group filtered sessions into lineage clusters. Singletons (no
    /// `lineage` or one filtered member) render as today; multi-member
    /// lineages collapse into one expandable cluster row. Cluster order
    /// preserves recency by anchoring on each cluster's first appearance
    /// in the already-sorted `filtered` list.
    private var clusters: [ThreadSearchCluster] {
        var bucket: [ThreadKey: [HomeDashboardRecentSession]] = [:]
        var firstAppearance: [ThreadKey: Int] = [:]
        for (idx, session) in filtered.enumerated() {
            let root = session.lineage?.rootKey ?? session.key
            if firstAppearance[root] == nil { firstAppearance[root] = idx }
            bucket[root, default: []].append(session)
        }
        return bucket
            .map { rootKey, members in
                ThreadSearchCluster(
                    rootKey: rootKey,
                    members: members.sorted { $0.updatedAt > $1.updatedAt }
                )
            }
            .sorted {
                (firstAppearance[$0.rootKey] ?? .max) < (firstAppearance[$1.rootKey] ?? .max)
            }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Color.clear.frame(height: contentInsets.top)
                runtimeFilterRow
                if isLoading {
                    HStack(spacing: BuddySpacing.sm) {
                        ProgressView()
                            .controlSize(.small)
                            .tint(AgentBuddyTheme.textSecondary)
                        Text("Loading tasks…")
                            .buddyText(.label, weight: .regular)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, BuddySpacing.xl)
                    .accessibilityElement(children: .combine)
                } else if filtered.isEmpty {
                    emptyState
                        .padding(.top, BuddySpacing.xs)
                } else {
                    ForEach(Array(clusters.enumerated()), id: \.element.id) { index, cluster in
                        VStack(alignment: .leading, spacing: 0) {
                            if index > 0 { BuddyDivider() }
                            if cluster.members.count == 1, let only = cluster.members.first {
                                ThreadSearchRow(
                                    session: only,
                                    isPinned: pinnedThreadKeys.contains(SavedThreadsStore.PinnedKey(threadKey: only.key)),
                                    onAdd: { onAdd(only) },
                                    onRemove: { onRemove(only) }
                                )
                            } else {
                                ThreadSearchClusterRow(
                                    cluster: cluster,
                                    pinnedThreadKeys: pinnedThreadKeys,
                                    isExpanded: expandedClusters.contains(cluster.rootKey),
                                    onToggleExpanded: {
                                        withAnimation(BuddyMotion.animation(.state, reduceMotion: reduceMotion)) {
                                            if expandedClusters.contains(cluster.rootKey) {
                                                expandedClusters.remove(cluster.rootKey)
                                            } else {
                                                expandedClusters.insert(cluster.rootKey)
                                            }
                                        }
                                    },
                                    onPin: onAdd,
                                    onUnpin: onRemove
                                )
                            }
                        }
                    }
                }
                Color.clear.frame(height: contentInsets.bottom)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .refreshable {
            await onRefresh()
        }
    }

    @ViewBuilder
    private var runtimeFilterRow: some View {
        if runtimeKinds.count > 1 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: BuddySpacing.xs) {
                    runtimeFilterPill(label: Text("All"), kind: nil)
                    ForEach(runtimeKinds, id: \.self) { kind in
                        runtimeFilterPill(label: Text(verbatim: kind.titleDisplayLabel), kind: kind)
                    }
                }
                .padding(.vertical, BuddySpacing.xxs)
            }
        }
    }

    private func runtimeFilterPill(label: Text, kind: AgentRuntimeKind?) -> some View {
        Button {
            selectedRuntimeKind = kind
        } label: {
            SessionsFilterChip(
                title: label,
                systemImage: kind == nil ? "square.grid.2x2" : nil,
                agentKind: kind,
                isSelected: selectedRuntimeKind == kind
            )
        }
        .buttonStyle(.plain)
    }

    /// Empty results explain why and, when a partner filter hides tasks,
    /// offer to show all partners.
    @ViewBuilder
    private var emptyState: some View {
        if sessions.isEmpty {
            BuddyEmptyState(
                systemImage: "tray",
                title: "No tasks yet",
                message: "Tasks from your connected hosts show up here. Pull down to check again."
            )
        } else {
            let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
            let message: LocalizedStringKey = trimmed.isEmpty
                ? "No tasks from this partner yet."
                : "No tasks match “\(trimmed)”. Try another word."
            if selectedRuntimeKind != nil {
                BuddyEmptyState(
                    systemImage: "magnifyingglass",
                    title: "No matching tasks",
                    message: message,
                    actionTitle: "Show all partners",
                    actionSystemImage: "square.grid.2x2",
                    actionKind: .secondary,
                    action: { selectedRuntimeKind = nil }
                )
            } else {
                BuddyEmptyState(
                    systemImage: "magnifyingglass",
                    title: "No matching tasks",
                    message: message
                )
            }
        }
    }
}

private struct ThreadSearchRow: View {
    let session: HomeDashboardRecentSession
    let isPinned: Bool
    let onAdd: () -> Void
    let onRemove: () -> Void

    var body: some View {
        Button(action: { isPinned ? onRemove() : onAdd() }) {
            ThreadSearchRowContent(session: session, updatedAt: session.updatedAt) {
                ThreadSearchPinIcon(isPinned: isPinned)
            }
        }
        .buttonStyle(.plain)
        .accessibilityValue(isPinned ? Text("On Home") : Text(verbatim: ""))
        .accessibilityHint(Text(isPinned ? "Remove from Home" : "Add to Home"))
    }
}

/// One lineage's worth of search rows. `members` is sorted by `updatedAt`
/// desc, so `members.first` is the cluster's "head" (most recently active
/// branch) — that's what the collapsed row surfaces.
struct ThreadSearchCluster: Identifiable {
    let rootKey: ThreadKey
    let members: [HomeDashboardRecentSession]

    var id: ThreadKey { rootKey }
}
