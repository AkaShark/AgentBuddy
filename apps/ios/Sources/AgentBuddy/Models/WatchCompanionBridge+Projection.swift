import Foundation
import WatchConnectivity

extension WatchCompanionBridge {
    // MARK: - Projection

    func currentPayload() -> WatchSnapshotPayload {
        let snapshot = AppModel.shared.snapshot
        let summaries = snapshot?.sessionSummaries ?? []
        let threads = snapshot?.threads ?? []
        let pendingApprovals = snapshot?.pendingApprovals ?? []

        // Mirror what the iPhone home actually displays — pin/hide rules from
        // SavedThreadsStore. Watch home stays in sync with phone home.
        let pinned = SavedThreadsStore.pinnedKeys()
        let hidden = SavedThreadsStore.hiddenKeys()
        let visibleSummaries = WatchProjection.homeFilteredSummaries(
            summaries: summaries,
            pinned: pinned,
            hidden: hidden
        )

        let projected = WatchProjection.tasks(
            summaries: visibleSummaries,
            threads: threads,
            pendingApprovals: pendingApprovals
        )
        // In pinned mode, the iPhone home shows pins in pin order. The watch
        // overlays its status-priority sort on top (running/needsApproval
        // surface to the top of each pin group).
        let tasks = WatchProjection.applyPinOrder(projected, pinned: pinned)

        // Hidden slice: summaries whose key is in `hidden`, projected with
        // the same shape as visible tasks so the watch hidden screen can
        // render them with the same row.
        let hiddenSet = Set(hidden)
        let hiddenSummaries = summaries.filter {
            hiddenSet.contains(PinnedThreadKey(threadKey: $0.key))
        }
        let hiddenTasks = WatchProjection.tasks(
            summaries: hiddenSummaries,
            threads: threads,
            pendingApprovals: pendingApprovals
        )

        return WatchSnapshotPayload(
            tasks: tasks,
            pendingApproval: pendingApprovals
                .first(where: { $0.kind != .mcpElicitation })
                .map(WatchProjection.approval),
            voice: WatchProjection.voice(
                from: snapshot,
                isMuted: VoiceRuntimeController.shared.isMicrophoneMuted
            ),
            theme: WatchProjection.theme(from: ThemeManager.shared),
            hiddenTasks: hiddenTasks.isEmpty ? nil : hiddenTasks
        )
    }

    func currentComplicationSnapshot() -> Data? {
        let snapshot = AppModel.shared.snapshot
        let summaries = snapshot?.sessionSummaries ?? []
        let threads = snapshot?.threads ?? []
        let pendingApprovals = snapshot?.pendingApprovals ?? []
        let connectedCount = (snapshot?.servers ?? [])
            .filter { $0.transportState == .connected }.count

        // Same home-visibility filter + pin-order overlay as the WC payload —
        // hidden tasks shouldn't bleed into watch face complications either.
        let pinned = SavedThreadsStore.pinnedKeys()
        let hidden = SavedThreadsStore.hiddenKeys()
        let visibleSummaries = WatchProjection.homeFilteredSummaries(
            summaries: summaries,
            pinned: pinned,
            hidden: hidden
        )

        let projected = WatchProjection.tasks(
            summaries: visibleSummaries,
            threads: threads,
            pendingApprovals: pendingApprovals
        )
        let tasks = WatchProjection.applyPinOrder(projected, pinned: pinned)
        let runningTask = tasks.first { $0.status == .running }
            ?? tasks.first { $0.status == .needsApproval }

        // B3: when WatchConnectivity isn't usable, surface offline mode.
        let offline: Bool = transport.activationState != .activated
            || !transport.isPaired
            || !transport.isWatchAppInstalled

        let mode: String
        let title: String
        let toolLine: String
        let progress: Double
        var taskId: String?
        var lastTurnStartMsEpoch: Int64?

        if offline {
            mode = "offline"
            title = "phone unreachable"
            toolLine = "tap to open"
            progress = 0
        } else if let task = runningTask {
            mode = "running"
            title = task.title
            toolLine = task.subtitle ?? "working"
            let total = max(task.steps.count, 1)
            let done = task.steps.filter({ $0.state == .done }).count
            progress = total > 0 ? Double(done) / Double(total) : 0.5
            taskId = task.id
            // Real wall-clock turn start, used by the timeline provider to
            // compute live elapsed seconds. Only running tasks tick, so we
            // only emit it when status == .running.
            if task.status == .running,
               let summary = summaries.first(where: {
                   $0.key.serverId == task.serverId && $0.key.threadId == task.threadId
               }),
               let started = summary.lastTurnStartMs {
                lastTurnStartMsEpoch = started
            }
        } else if tasks.isEmpty {
            mode = "idle"
            title = "\(connectedCount) servers ready"
            toolLine = "tap to open"
            progress = 1
        } else {
            mode = "idle"
            title = "\(tasks.count) task\(tasks.count == 1 ? "" : "s")"
            toolLine = tasks.first?.title ?? ""
            progress = 1
        }

        var dict: [String: Any] = [
            "mode": mode,
            "progress": progress,
            "title": title,
            "toolLine": toolLine,
            "serverCount": connectedCount,
        ]
        if let taskId { dict["taskId"] = taskId }
        if let lastTurnStartMsEpoch { dict["lastTurnStartMsEpoch"] = lastTurnStartMsEpoch }

        return try? JSONSerialization.data(withJSONObject: dict)
    }
}
