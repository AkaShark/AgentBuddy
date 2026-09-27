import Foundation
import WatchConnectivity

extension WatchCompanionBridge {
    // MARK: - Per-server complication slices

    /// Build a `AgentBuddyComplicationPayload` Data slice per known server. The
    /// widget configuration intent picks the right slice when the user has
    /// pinned a complication to a single server. Servers not present in
    /// the map (or `nil` server selection) fall back to the aggregate
    /// `complication.snapshot.v1` write.
    func currentPerServerComplicationSnapshots() -> [String: Data] {
        let snapshot = AppModel.shared.snapshot
        let summaries = snapshot?.sessionSummaries ?? []
        let threads = snapshot?.threads ?? []
        let pendingApprovals = snapshot?.pendingApprovals ?? []
        let servers = snapshot?.servers ?? []

        // Same offline gate as the aggregate path — when the watch isn't
        // reachable, every server slice surfaces offline so the picker
        // selection still renders something sane.
        let offline: Bool = transport.activationState != .activated
            || !transport.isPaired
            || !transport.isWatchAppInstalled

        let pinned = SavedThreadsStore.pinnedKeys()
        let hidden = SavedThreadsStore.hiddenKeys()
        let visibleSummaries = WatchProjection.homeFilteredSummaries(
            summaries: summaries,
            pinned: pinned,
            hidden: hidden
        )
        let allTasks = WatchProjection.applyPinOrder(
            WatchProjection.tasks(
                summaries: visibleSummaries,
                threads: threads,
                pendingApprovals: pendingApprovals
            ),
            pinned: pinned
        )

        var out: [String: Data] = [:]
        for server in servers {
            let serverId = server.serverId
            let connected = server.transportState == .connected
            let serverTasks = allTasks.filter { $0.serverId == serverId }
            let runningTask = serverTasks.first { $0.status == .running }
                ?? serverTasks.first { $0.status == .needsApproval }

            let mode: AgentBuddyComplicationEntry.Mode
            let title: String
            let toolLine: String
            let progress: Double
            var taskId: String?
            var lastTurnStartMsEpoch: Int64?

            if offline {
                mode = .offline
                title = "phone unreachable"
                toolLine = "tap to open"
                progress = 0
            } else if let task = runningTask {
                mode = .running
                title = task.title
                toolLine = task.subtitle ?? "working"
                let total = max(task.steps.count, 1)
                let done = task.steps.filter({ $0.state == .done }).count
                progress = total > 0 ? Double(done) / Double(total) : 0.5
                taskId = task.id
                if task.status == .running,
                   let summary = summaries.first(where: {
                       $0.key.serverId == task.serverId && $0.key.threadId == task.threadId
                   }),
                   let started = summary.lastTurnStartMs {
                    lastTurnStartMsEpoch = started
                }
            } else if serverTasks.isEmpty {
                mode = .idle
                title = connected
                    ? "\(server.displayName) ready"
                    : "\(server.displayName) offline"
                toolLine = "tap to open"
                progress = 1
            } else {
                mode = .idle
                title = "\(serverTasks.count) task\(serverTasks.count == 1 ? "" : "s")"
                toolLine = serverTasks.first?.title ?? ""
                progress = 1
            }

            let payload = AgentBuddyComplicationPayload(
                mode: mode,
                lastTurnStartMsEpoch: lastTurnStartMsEpoch,
                taskId: taskId,
                progress: progress,
                title: title,
                toolLine: toolLine,
                // Per-server slice always reports 1 — the picker scoped to
                // a single server. The aggregate path still reports the
                // global connected count for the unselected default.
                serverCount: connected ? 1 : 0
            )
            if let data = try? JSONEncoder().encode(payload) {
                out[serverId] = data
            }
        }
        return out
    }

    /// Build the picker payload the watch face configuration intent reads.
    /// Returns every server the iOS app currently knows about, regardless
    /// of transport state — the user might want to pin a complication to a
    /// known-but-disconnected server so the slot is reserved for when it
    /// reconnects.
    func currentServerListPayload() -> AgentBuddyServerListPayload {
        let servers = (AppModel.shared.snapshot?.servers ?? []).map {
            AgentBuddyServerListPayload.Server(
                id: $0.serverId,
                displayName: $0.displayName
            )
        }
        return AgentBuddyServerListPayload(servers: servers)
    }

    func writePerServerComplicationSnapshots() {
        let map = currentPerServerComplicationSnapshots()
        guard let defaults = UserDefaults(suiteName: Self.appGroupSuite) else { return }
        defaults.set(map, forKey: Self.perServerComplicationKey)
    }

    func writeServerListPayload() {
        let payload = currentServerListPayload()
        guard
            let defaults = UserDefaults(suiteName: Self.appGroupSuite),
            let data = try? JSONEncoder().encode(payload)
        else { return }
        defaults.set(data, forKey: Self.serverListKey)
    }
}
