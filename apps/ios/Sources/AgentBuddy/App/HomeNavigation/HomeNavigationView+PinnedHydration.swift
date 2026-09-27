import SwiftUI

extension HomeNavigationView {
    var pinnedThreadHydrationSignature: String {
        let pins = homeDashboardModel.pinnedKeys
            .map { "\($0.serverId)/\($0.threadId)" }
            .joined(separator: "|")
        let pinnedSet = Set(homeDashboardModel.pinnedKeys)
        let servers = appModel.snapshot?.servers
            .map { "\($0.serverId)=\(String(describing: $0.transportState)):\($0.port)" }
            .joined(separator: "|") ?? ""
        let sessions = appModel.snapshot?.sessionSummaries
            .compactMap { summary -> String? in
                guard pinnedSet.contains(PinnedThreadKey(threadKey: summary.key)) else { return nil }
                return "\(homeHydrationId(summary.key)):\(summary.isResumed)"
            }
            .joined(separator: "|")
            ?? ""
        return "\(pins)|\(servers)|\(sessions)"
    }

    private func homeHydrationId(_ key: ThreadKey) -> String {
        "\(key.serverId)/\(key.threadId)"
    }

    func hydratePinnedThreadsIfNeeded() {
        let connectedServerIds = Set(
            (appModel.snapshot?.servers ?? [])
                .filter(\.isConnected)
                .map(\.serverId)
        )
        guard !connectedServerIds.isEmpty else { return }

        for pin in homeDashboardModel.pinnedKeys {
            let key = pin.threadKey
            guard connectedServerIds.contains(key.serverId) else { continue }
            let id = homeHydrationId(key)
            if appModel.snapshot?.sessionSummary(for: key)?.isResumed == true { continue }
            guard !hydratingPinnedHomeThreadIds.contains(id) else { continue }
            hydratingPinnedHomeThreadIds.insert(id)

            Task {
                LLog.info(
                    "home",
                    "hydrating pinned thread",
                    fields: ["serverId": key.serverId, "threadId": key.threadId]
                )
                if !(await hydrateThread(key, loadInitialTurns: true)) {
                    let refreshed = await refreshPinnedThreadListing(serverId: key.serverId)
                    guard refreshed else {
                        await MainActor.run {
                            _ = hydratingPinnedHomeThreadIds.remove(id)
                        }
                        return
                    }
                    _ = await hydrateThread(key, loadInitialTurns: true)
                }
                await MainActor.run {
                    _ = hydratingPinnedHomeThreadIds.remove(id)
                }
            }
        }
    }

    @discardableResult
    func hydrateThread(_ key: ThreadKey, loadInitialTurns: Bool) async -> Bool {
        // Resume rather than just read: `external_resume_thread` attaches a
        // server-side conversation listener for this connection, so we get
        // live `TurnStarted` / `ItemStarted` / `MessageDelta` /
        // `TurnCompleted` events. Pinned home rows also load the latest turn
        // window so their previews have recent message content.
        //
        // For pinned home rows, resuming preemptively avoids the "first
        // half-second of a stream is missed while we set up a subscription"
        // latency window that an active-only subscription strategy would
        // have. `externalResume` short-circuits to a no-op when the thread's
        // items are already populated, so warm paths are cheap.
        let resumed = (try? await appModel.store.externalResumeThread(key: key, hostId: nil)) != nil
        if resumed, loadInitialTurns {
            await appModel.loadInitialTurnsIfNeeded(threadId: key)
        }
        await appModel.refreshThreadSnapshot(key: key)
        return resumed
    }

    private func refreshPinnedThreadListing(serverId: String) async -> Bool {
        let task = await MainActor.run {
            if let existing = pinnedThreadListingRepairTasks[serverId] {
                return existing
            }

            let task = Task { () -> Bool in
                LLog.info(
                    "home",
                    "repairing pinned thread listing",
                    fields: ["serverId": serverId, "limit": 80]
                )
                do {
                    try await appModel.client.listThreads(
                        serverId: serverId,
                        params: AppListThreadsRequest(
                            cursor: nil,
                            limit: 80,
                            sortKey: .updatedAt,
                            sortDirection: .desc,
                            modelProviders: nil,
                            sourceKinds: [.cli, .vsCode, .appServer],
                            archived: false,
                            cwd: nil,
                            searchTerm: nil,
                            useStateDbOnly: false,
                            runtimeKinds: nil
                        )
                    )
                    return true
                } catch {
                    LLog.warn(
                        "home",
                        "pinned thread listing repair failed",
                        fields: ["serverId": serverId, "error": String(describing: error)]
                    )
                    return false
                }
            }
            pinnedThreadListingRepairTasks[serverId] = task
            return task
        }

        let refreshed = await task.value
        await MainActor.run {
            pinnedThreadListingRepairTasks[serverId] = nil
        }
        return refreshed
    }
}
