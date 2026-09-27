import Foundation

extension WatchProjection {
    static func approval(_ approval: PendingApproval) -> WatchApproval {
        let kind: WatchApproval.Kind
        switch approval.kind {
        case .command:        kind = .command
        case .fileChange:     kind = .fileChange
        case .permissions:    kind = .permissions
        case .mcpElicitation: kind = .mcpElicitation
        }

        let (command, target, diff) = describe(approval)

        return WatchApproval(
            id: approval.id,
            command: command,
            target: target,
            diffSummary: diff,
            kind: kind
        )
    }

    static func approvalLabel(_ approval: PendingApproval) -> String {
        switch approval.kind {
        case .command:        return compact(approval.command ?? "command", max: 32)
        case .fileChange:     return compact(approval.path ?? "file change", max: 32)
        case .permissions:    return "permissions"
        case .mcpElicitation: return "mcp input"
        }
    }

    private static func describe(_ approval: PendingApproval) -> (command: String, target: String, diff: String) {
        switch approval.kind {
        case .command:
            let cmd = approval.command ?? "command"
            return (
                command: compact(cmd, max: 60),
                target: approval.cwd ?? "",
                diff: approval.reason.map { compact($0, max: 60) } ?? ""
            )

        case .fileChange:
            let path = approval.path ?? "file"
            return (
                command: "edit_file",
                target: compact(path, max: 60),
                diff: approval.grantRoot.map { compact($0, max: 48) } ?? ""
            )

        case .permissions:
            return (
                command: "permissions",
                target: approval.reason ?? "grant access",
                diff: ""
            )

        case .mcpElicitation:
            return (
                command: "mcp",
                target: approval.reason ?? "input requested",
                diff: ""
            )
        }
    }
}
