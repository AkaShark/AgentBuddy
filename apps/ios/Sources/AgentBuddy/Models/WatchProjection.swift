import Foundation

/// Pure functions that project iOS `AppSnapshotRecord` slices into the
/// `Watch*` wire-format types consumed by the watchOS target.
enum WatchProjection {
    /// Build the full task list the watch's home shows. Order matches the
    /// iPhone sessions screen — running/needs-approval first, then most
    /// recently updated.
    static func tasks(
        summaries: [AppSessionSummary],
        threads: [AppThreadSnapshot],
        pendingApprovals: [PendingApproval]
    ) -> [WatchTask] {
        let approvalsByThread = Dictionary(
            grouping: pendingApprovals.filter { $0.kind != .mcpElicitation },
            by: { $0.threadId ?? "" }
        )
        let threadsByKey = Dictionary(uniqueKeysWithValues: threads.map { ($0.key, $0) })

        let mapped = summaries.map { summary -> WatchTask in
            let threadApprovals = approvalsByThread[summary.key.threadId] ?? []
            let thread = threadsByKey[summary.key]

            let status: WatchTask.Status
            if !threadApprovals.isEmpty {
                status = .needsApproval
            } else if summary.hasActiveTurn {
                status = .running
            } else {
                status = .idle
            }

            // Prefer the assistant's last reply over the current tool label —
            // on a small screen the user wants to see what the AI *said*
            // more than which tool is executing. Tool name is still
            // exposed separately as `lastTool` so the UI can render it as
            // a small secondary chip.
            let subtitle: String?
            if status == .needsApproval, let first = threadApprovals.first {
                subtitle = "awaiting approval: \(approvalLabel(first))"
            } else if let lastResp = summary.lastResponsePreview, !lastResp.isEmpty {
                subtitle = compact(lastResp, max: 100)
            } else if let lastTool = summary.lastToolLabel, !lastTool.isEmpty {
                subtitle = compact(lastTool, max: 48)
            } else if let lastUser = summary.lastUserMessage, !lastUser.isEmpty {
                subtitle = compact(lastUser, max: 60)
            } else if !summary.preview.isEmpty {
                subtitle = compact(summary.preview, max: 60)
            } else {
                subtitle = nil
            }

            // Separate field for the active tool. Only set when an assistant
            // reply is the subtitle (otherwise the tool *is* the subtitle and
            // duplicating it would be noisy).
            let lastTool: String? = {
                guard let tool = summary.lastToolLabel, !tool.isEmpty else { return nil }
                guard summary.lastResponsePreview?.isEmpty == false else { return nil }
                return compact(tool, max: 36)
            }()

            let stats = summary.stats
            let pct: Int? = {
                guard let tu = summary.tokenUsage,
                      let window = tu.contextWindow,
                      window > 0
                else { return nil }
                return Int(min(100, max(0, (Double(tu.totalTokens) / Double(window)) * 100)))
            }()

            return WatchTask(
                id: "\(summary.key.serverId):\(summary.key.threadId)",
                threadId: summary.key.threadId,
                serverId: summary.key.serverId,
                serverName: summary.serverDisplayName,
                title: title(for: summary),
                subtitle: subtitle,
                status: status,
                relativeTime: relativeTime(from: summary.updatedAt),
                steps: thread.map { deriveSteps(from: $0.hydratedConversationItems) } ?? [],
                transcript: thread.map { transcript(for: $0) } ?? [],
                pendingApprovalId: threadApprovals.first?.id,
                model: summary.model.isEmpty ? nil : summary.model,
                cwd: summary.cwd.isEmpty ? nil : summary.cwd,
                turnCount: stats.map { Int($0.turnCount) },
                toolCallCount: stats.map { Int($0.toolCallCount) },
                diffAdditions: stats.map { Int($0.diffAdditions) },
                diffDeletions: stats.map { Int($0.diffDeletions) },
                contextPercent: pct,
                hasTurnActive: summary.hasActiveTurn,
                lastTool: lastTool,
                diffs: thread
                    .map { deriveDiffs(from: $0.hydratedConversationItems) }
                    .flatMap { $0.isEmpty ? nil : $0 }
            )
        }

        return mapped.sorted { lhs, rhs in
            // Running / needsApproval surfaces to top; tie-break by updated time.
            let lr = rank(lhs.status)
            let rr = rank(rhs.status)
            if lr != rr { return lr < rr }
            return (indexOfUpdatedAt(lhs, in: summaries) ?? Int.max)
                 < (indexOfUpdatedAt(rhs, in: summaries) ?? Int.max)
        }
    }

    /// Re-sort an already-projected task list so that, within each status
    /// group, threads appear in the iPhone home's pin order. Use this on
    /// top of `tasks(...)` when pinned mode is active. Tasks not in
    /// `pinned` sort to the end of their group, ordered by their existing
    /// position (stable).
    static func applyPinOrder(
        _ tasks: [WatchTask],
        pinned: [PinnedThreadKey]
    ) -> [WatchTask] {
        guard !pinned.isEmpty else { return tasks }
        let pinIndex: [PinnedThreadKey: Int] = Dictionary(
            uniqueKeysWithValues: pinned.enumerated().map { ($1, $0) }
        )
        // Pair each task with its (statusRank, pinOrder, originalIndex) so a
        // stable sort preserves intra-group ordering for unpinned trailers.
        let decorated = tasks.enumerated().map { idx, task -> (key: (Int, Int, Int), task: WatchTask) in
            let pin = PinnedThreadKey(serverId: task.serverId, threadId: task.threadId)
            return ((rank(task.status), pinIndex[pin] ?? Int.max, idx), task)
        }
        return decorated
            .sorted { lhs, rhs in
                if lhs.key.0 != rhs.key.0 { return lhs.key.0 < rhs.key.0 }
                if lhs.key.1 != rhs.key.1 { return lhs.key.1 < rhs.key.1 }
                return lhs.key.2 < rhs.key.2
            }
            .map(\.task)
    }

    /// Apply the iPhone home's visibility rules to a summary list so the
    /// watch shows exactly what the phone home shows.
    ///
    /// - Hidden threads are excluded.
    /// - If any threads are pinned, show only those (in pin order). Pinned
    ///   entries not yet in `summaries` are skipped (the iPhone uses a
    ///   "Loading thread" placeholder; the watch just waits for the next push).
    /// - Otherwise show the 10 most-recent summaries.
    ///
    /// Mirrors `HomeDashboardModel.mergedHomeSessions` in
    /// `apps/ios/Sources/AgentBuddy/Views/HomeDashboardModel.swift`.
    static func homeFilteredSummaries(
        summaries: [AppSessionSummary],
        pinned: [PinnedThreadKey],
        hidden: [PinnedThreadKey]
    ) -> [AppSessionSummary] {
        let hiddenSet = Set(hidden)
        let candidates = summaries.filter {
            !hiddenSet.contains(PinnedThreadKey(threadKey: $0.key))
        }
        if !pinned.isEmpty {
            let byKey = Dictionary(uniqueKeysWithValues: candidates.map {
                (PinnedThreadKey(threadKey: $0.key), $0)
            })
            return pinned.compactMap { byKey[$0] }
        }
        return Array(
            candidates
                .sorted { ($0.updatedAt ?? 0) > ($1.updatedAt ?? 0) }
                .prefix(10)
        )
    }

    // MARK: - Helpers

    private static func title(for summary: AppSessionSummary) -> String {
        if !summary.title.isEmpty {
            return compact(summary.title, max: 50)
        }
        if let preview = summary.lastUserMessage, !preview.isEmpty {
            return compact(preview, max: 50)
        }
        if !summary.preview.isEmpty {
            return compact(summary.preview, max: 50)
        }
        return "untitled task"
    }

    private static func rank(_ status: WatchTask.Status) -> Int {
        switch status {
        case .needsApproval: return 0
        case .running:       return 1
        case .error:         return 2
        case .idle:          return 3
        }
    }

    private static func indexOfUpdatedAt(_ task: WatchTask, in summaries: [AppSessionSummary]) -> Int? {
        guard let s = summaries.first(where: { $0.key.threadId == task.threadId && $0.key.serverId == task.serverId })
        else { return nil }
        guard let updated = s.updatedAt else { return Int.max }
        // Invert so larger (more recent) sorts first.
        return -Int(updated)
    }

    private static func relativeTime(from updatedAt: Int64?) -> String {
        guard let updatedAt else { return "" }
        let updatedDate = Date(timeIntervalSince1970: TimeInterval(updatedAt))
        let delta = Date().timeIntervalSince(updatedDate)
        if delta < 60 { return "now" }
        if delta < 3600 { return "\(Int(delta) / 60)m" }
        if delta < 86400 { return "\(Int(delta) / 3600)h" }
        if delta < 7 * 86400 { return "\(Int(delta) / 86400)d" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter.string(from: updatedDate)
    }

    static func compact(_ s: String, max: Int = 60) -> String {
        let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\n", with: " ")
        if trimmed.count <= max { return trimmed }
        return String(trimmed.prefix(max - 1)) + "…"
    }
}
