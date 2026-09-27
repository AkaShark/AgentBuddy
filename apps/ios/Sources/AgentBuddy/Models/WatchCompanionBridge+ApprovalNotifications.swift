import Foundation
import UserNotifications

extension WatchCompanionBridge {
    // MARK: - Approval notifications

    /// Diff the current pending approvals against `notifiedApprovalIds`. For
    /// every newly arrived approval, schedule a local push with Allow/Deny
    /// inline actions so the watch can surface them on the long-look.
    func scheduleApprovalNotificationsIfNeeded() {
        let pending = AppModel.shared.snapshot?.pendingApprovals ?? []
        let summaries = AppModel.shared.snapshot?.sessionSummaries ?? []
        let summaryByKey = Dictionary(
            uniqueKeysWithValues: summaries.map { ($0.key, $0) }
        )
        let currentIds = Set(pending.map(\.id))

        for approval in pending where !notifiedApprovalIds.contains(approval.id) {
            // Skip mcp elicitations — same rule as the home projection.
            guard approval.kind != .mcpElicitation else { continue }
            let summary: AppSessionSummary? = approval.threadId.flatMap {
                summaryByKey[ThreadKey(serverId: approval.serverId, threadId: $0)]
            }
            let serverName = summary?.serverDisplayName ?? approval.serverId
            let threadTitle = summary?.title.isEmpty == false ? summary?.title : nil
            let request = Self.makeApprovalNotificationRequest(
                approval: approval,
                serverName: serverName,
                threadTitle: threadTitle
            )
            UNUserNotificationCenter.current().add(request) { error in
                if let error {
                    LLog.error(
                        "watch",
                        "approval notification add failed: \(error.localizedDescription)"
                    )
                }
            }
            notifiedApprovalIds.insert(approval.id)
        }

        // Drop ids for approvals that are no longer pending so the set can't
        // grow without bound and so a re-issued id (rare, but possible after
        // a reconnect) re-notifies.
        notifiedApprovalIds.formIntersection(currentIds)
    }

    /// Build the `UNNotificationRequest` for an approval. Exposed as a static
    /// pure function so tests can assert the wire-format without standing up
    /// a notification center.
    static func makeApprovalNotificationRequest(
        approval: PendingApproval,
        serverName: String,
        threadTitle: String?
    ) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = "Approval needed"
        content.subtitle = serverName
        content.body = approvalBody(approval: approval, threadTitle: threadTitle)
        content.sound = .default
        content.categoryIdentifier = WatchApprovalNotification.categoryIdentifier
        // Grouping by server keeps multiple pending approvals stacked under
        // a single watch banner instead of fragmenting per-thread.
        content.threadIdentifier = approval.serverId
        var info: [String: Any] = [
            WatchApprovalNotification.requestIdKey: approval.id,
            WatchApprovalNotification.serverIdKey: approval.serverId,
        ]
        if let threadId = approval.threadId {
            info[WatchApprovalNotification.threadIdKey] = threadId
        }
        content.userInfo = info

        return UNNotificationRequest(
            identifier: "agentbuddy.approval.\(approval.id)",
            content: content,
            trigger: nil
        )
    }

    private static func approvalBody(
        approval: PendingApproval,
        threadTitle: String?
    ) -> String {
        let detail: String
        switch approval.kind {
        case .command:
            detail = approval.command ?? "Run command"
        case .fileChange:
            detail = approval.path.map { "Edit \($0)" } ?? "Apply file change"
        case .permissions:
            detail = approval.reason ?? "Grant permissions"
        case .mcpElicitation:
            detail = approval.reason ?? "Input requested"
        }
        if let threadTitle, !threadTitle.isEmpty {
            return "\(threadTitle) — \(detail)"
        }
        return detail
    }
}
