/// Identifiers shared between the iPhone scheduler and notification action
/// handler so approval banners stay in lockstep across app targets.
enum WatchApprovalNotification {
    static let categoryIdentifier = "agentbuddy.approval"
    static let allowActionIdentifier = "agentbuddy.approval.allow"
    static let denyActionIdentifier = "agentbuddy.approval.deny"
    static let requestIdKey = "requestId"
    static let serverIdKey = "serverId"
    static let threadIdKey = "threadId"
}
