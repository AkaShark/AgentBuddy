import SwiftUI

extension TasksHomeView {
    var searchSessions: [HomeDashboardRecentSession] {
        guard let serverId = model.selectedServerId, !serverId.isEmpty else { return model.allSessions }
        return model.allSessions.filter { $0.serverId == serverId }
    }

    var searchRuntimeKinds: [AgentRuntimeKind] {
        let servers = model.selectedServerId.map { id in model.connectedServers.filter { $0.id == id } } ?? model.connectedServers
        let kinds = Set(servers.flatMap { $0.agentRuntimes.filter(\.available).map(\.kind) })
        return AgentRuntimeKind.presentationOrder.filter { kinds.contains($0) }
    }

    var searchField: some View {
        HStack(spacing: BuddySpacing.xs) {
            HStack(spacing: BuddySpacing.xs) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
                TextField("Search tasks", text: $searchQuery)
                    .buddyText(.body)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
            }
            .padding(.horizontal, BuddySpacing.md)
            .frame(minHeight: BuddySize.control)
            .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
                    .strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1)
            }
            Button("Cancel") {
                isSearching = false
                searchQuery = ""
                searchRuntimeKind = nil
            }
            .buddyText(.label, weight: .semibold)
            .foregroundStyle(AgentBuddyTheme.link)
            .frame(minHeight: BuddySize.minHitTarget)
        }
        .padding(.top, BuddySpacing.sm)
    }

    /// Server-side search, debounced like the previous home (250 ms), and
    /// scoped to the selected host.
    var searchResults: some View {
        ThreadSearchResultsView(
            sessions: searchSessions,
            pinnedThreadKeys: Set(model.pinnedKeys),
            query: searchQuery,
            runtimeKinds: searchRuntimeKinds,
            selectedRuntimeKind: $searchRuntimeKind,
            isLoading: isLoadingSearch,
            onRefresh: {
                await actions.searchThreads(searchQuery, searchRuntimeKind, model.selectedServerId, true)
            },
            onAdd: { actions.pinThread($0.key) },
            onRemove: { actions.unpinThread($0.key) }
        )
        .frame(minHeight: 420)
        .task(id: searchLoadID) {
            guard isSearching else { return }
            let query = searchQuery
            if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                try? await Task.sleep(nanoseconds: 250_000_000)
                guard !Task.isCancelled else { return }
            }
            isLoadingSearch = true
            await actions.searchThreads(query, searchRuntimeKind, model.selectedServerId, false)
            guard !Task.isCancelled else { return }
            isLoadingSearch = false
        }
    }

    private var searchLoadID: String {
        [isSearching ? "open" : "closed", model.selectedServerId ?? "all", searchQuery, searchRuntimeKind?.displayLabel ?? "all"]
            .joined(separator: "|")
    }
}

// MARK: - Hydration and stop tracking

/// Keeps behaviour the previous home relied on:
/// - pinned tasks that are not yet resumed get a live listener attached;
/// - a task the user stopped shows "Stopping…" until the snapshot confirms
///   the turn has ended.
extension TasksHomeView {
    var hydrationSignature: [String] {
        visibleSessions.map { HomeTaskPresentation.hydrationId($0.key) }
    }

    var activitySignature: [String] {
        visibleSessions.map { "\(HomeTaskPresentation.hydrationId($0.key)):\($0.hasTurnActive)" }
    }

    func hydratePinnedSessions() {
        let byPinnedKey = Dictionary(
            visibleSessions.map { (SavedThreadsStore.PinnedKey(threadKey: $0.key), $0) },
            uniquingKeysWith: { first, _ in first }
        )
        for session in model.pinnedKeys.compactMap({ byPinnedKey[$0] }) where !session.isResumed {
            let id = HomeTaskPresentation.hydrationId(session.key)
            guard !hydratingKeys.contains(id) else { continue }
            hydratingKeys.insert(id)
            Task {
                await actions.hydrateThread(session.key, true)
                await MainActor.run { _ = hydratingKeys.remove(id) }
            }
        }
    }

    func pruneFinishedStops() {
        let stillActive = Set(visibleSessions.filter(\.hasTurnActive).map { HomeTaskPresentation.hydrationId($0.key) })
        cancellingKeys.formIntersection(stillActive)
    }
}
