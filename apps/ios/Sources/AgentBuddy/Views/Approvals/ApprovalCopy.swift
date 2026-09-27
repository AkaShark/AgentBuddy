import SwiftUI

/// User-facing wording per approval kind. Kept in one place so the inline card,
/// the banner and the result card describe the same request the same way.
enum ApprovalCopy {
    static func title(for kind: ApprovalKind) -> LocalizedStringKey {
        switch kind {
        case .command: return "Run this command?"
        case .fileChange: return "Change these files?"
        case .permissions: return "Grant more access?"
        case .mcpElicitation: return "A tool needs your input"
        }
    }

    static func question(for kind: ApprovalKind, hostName: String?) -> LocalizedStringKey {
        let host = hostName ?? String(localized: "your computer")
        switch kind {
        case .command: return "AgentBuddy wants to run a command on \(host)."
        case .fileChange: return "AgentBuddy wants to change files on \(host)."
        case .permissions: return "AgentBuddy is asking for broader access on \(host)."
        case .mcpElicitation: return "Answer this request on \(host)."
        }
    }

    static func sessionScopeExplanation(for kind: ApprovalKind) -> LocalizedStringKey {
        switch kind {
        case .command: return "Similar commands in this task won't ask again until the session ends. You can still stop the task at any time."
        case .fileChange: return "File changes in this task won't ask again until the session ends. You can still stop the task at any time."
        case .permissions, .mcpElicitation: return "This access stays granted for the rest of this task's session."
        }
    }

    static func outcomeTitle(_ kind: ApprovalOutcome.Kind) -> LocalizedStringKey {
        switch kind {
        case .allowedOnce: return "Allowed this once"
        case .allowedForSession: return "Allowed for this session"
        case .denied: return "Denied"
        case .aborted: return "Task stop requested"
        case .resolvedElsewhere: return "Handled elsewhere"
        }
    }

    static func outcomeDetail(_ kind: ApprovalOutcome.Kind) -> LocalizedStringKey {
        switch kind {
        case .allowedOnce, .allowedForSession: return "AgentBuddy is continuing. You'll see the result here when it's done."
        case .denied: return "AgentBuddy was told not to do this and will look for another way."
        case .aborted: return "The host is stopping the task."
        case .resolvedElsewhere: return "This request was answered on another device or is no longer pending."
        }
    }

    static func outcomeSymbol(_ kind: ApprovalOutcome.Kind) -> String {
        switch kind {
        case .allowedOnce, .allowedForSession: return "checkmark.circle"
        case .denied: return "xmark.circle"
        case .aborted: return "stop.circle"
        case .resolvedElsewhere: return "arrow.triangle.2.circlepath"
        }
    }
}
