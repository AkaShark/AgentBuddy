import Foundation
import SwiftUI

/// Render-only projection of a home task. Built from Rust-owned snapshot data
/// (`AppSessionSummary` via `HomeDashboardRecentSession`, plus pending
/// approvals / user inputs joined by thread key); it never decides task state
/// on its own.
struct HomeTaskItem: Identifiable, Equatable {
    let session: HomeDashboardRecentSession
    let state: BuddyTaskState
    let pendingApprovalCount: Int
    let isPinned: Bool

    var id: ThreadKey { session.key }
    var key: ThreadKey { session.key }

    var title: String {
        let trimmed = session.sessionTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? String(localized: "Untitled task") : trimmed
    }

    /// Last path component of the working directory ("AgentBuddy").
    var projectName: String? {
        HomeTaskPresentation.projectName(forCwd: session.cwd)
    }

    /// Partner / runtime label ("Codex", "Claude Code").
    var partnerName: String {
        session.agentRuntimeKind.displayLabel
    }

    /// The most recent step the partner reported, for the card's second line.
    var latestStep: String? {
        let candidates = [session.lastToolLabel, session.lastResponsePreview, session.preview]
        return candidates
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty }
    }

    /// "已修改 3 个文件 · 运行 2 条命令" style summary from Rust stats.
    var activitySummary: String? {
        guard let stats = session.stats else { return nil }
        var parts: [String] = []
        if stats.filesChanged > 0 {
            parts.append(String(localized: "Changed \(Int(stats.filesChanged)) files"))
        }
        if stats.commandsExecuted > 0 {
            parts.append(String(localized: "Ran \(Int(stats.commandsExecuted)) commands"))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

enum HomeTaskPresentation {
    /// Joins a session with snapshot-level request queues. Order matters:
    /// a stop in flight wins over "running", a pending approval wins over
    /// plain activity, and connection problems are never mapped to failure.
    static func state(
        for session: HomeDashboardRecentSession,
        approvalKeys: Set<String>,
        inputKeys: Set<String>,
        cancellingKeys: Set<String>
    ) -> BuddyTaskState {
        let id = hydrationId(session.key)
        if session.hasTurnActive, cancellingKeys.contains(id) { return .stopping }
        if approvalKeys.contains(id) { return .awaitingApproval }
        if inputKeys.contains(id) { return .awaitingInput }
        if session.hasTurnActive { return .running }
        return session.lastTurnEnd == nil ? .idle : .completed
    }

    static func items(
        sessions: [HomeDashboardRecentSession],
        pendingApprovals: [PendingApproval],
        pendingInputs: [PendingUserInputRequest],
        pinnedKeys: [SavedThreadsStore.PinnedKey],
        cancellingKeys: Set<String>
    ) -> [HomeTaskItem] {
        var approvalCounts: [String: Int] = [:]
        for approval in pendingApprovals where approval.kind != .mcpElicitation {
            guard let threadId = approval.threadId else { continue }
            approvalCounts["\(approval.serverId)/\(threadId)", default: 0] += 1
        }
        let inputKeys = Set(pendingInputs.map { "\($0.serverId)/\($0.threadId)" })
        let pinned = Set(pinnedKeys)
        return sessions.map { session in
            HomeTaskItem(
                session: session,
                state: state(
                    for: session,
                    approvalKeys: Set(approvalCounts.keys),
                    inputKeys: inputKeys,
                    cancellingKeys: cancellingKeys
                ),
                pendingApprovalCount: approvalCounts[hydrationId(session.key)] ?? 0,
                isPinned: pinned.contains(SavedThreadsStore.PinnedKey(threadKey: session.key))
            )
        }
    }

    static func hydrationId(_ key: ThreadKey) -> String {
        "\(key.serverId)/\(key.threadId)"
    }

    static func projectName(forCwd cwd: String) -> String? {
        let trimmed = cwd.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != "/" else { return nil }
        let name = (trimmed as NSString).lastPathComponent
        return name.isEmpty ? nil : name
    }

    /// Localized "18 分钟前" style text.
    static func relativeTime(_ date: Date, now: Date = Date()) -> String {
        if abs(now.timeIntervalSince(date)) < 60 {
            return String(localized: "Just now")
        }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: now)
    }
}
