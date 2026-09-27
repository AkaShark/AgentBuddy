import Foundation

extension WatchProjection {
    static func deriveSteps(from items: [HydratedConversationItem]) -> [WatchTaskStep] {
        var steps: [WatchTaskStep] = []
        for item in items.suffix(12) {
            guard let step = stepFromItem(item) else { continue }
            steps.append(step)
        }
        return Array(steps.suffix(5))
    }

    private static func stepFromItem(_ item: HydratedConversationItem) -> WatchTaskStep? {
        switch item.content {
        case .commandExecution(let data):
            return WatchTaskStep(
                tool: "bash",
                arg: compact(data.command, max: 32),
                state: mapStatus(data.status)
            )

        case .fileChange(let data):
            let primary = data.changes.first?.path ?? "patch"
            let kind = data.changes.first?.kind ?? "edit"
            return WatchTaskStep(
                tool: mapFileChangeKind(kind),
                arg: compact(primary, max: 32),
                state: mapStatus(data.status)
            )

        case .webSearch(let data):
            return WatchTaskStep(
                tool: "web_search",
                arg: compact(data.query, max: 32),
                state: data.isInProgress ? .active : .done
            )

        case .mcpToolCall(let data):
            return WatchTaskStep(
                tool: data.tool,
                arg: compact(data.contentSummary ?? "", max: 28),
                state: mapStatus(data.status)
            )

        case .dynamicToolCall(let data):
            return WatchTaskStep(
                tool: data.tool,
                arg: compact(data.contentSummary ?? "", max: 28),
                state: mapStatus(data.status)
            )

        default:
            return nil
        }
    }

    private static func mapStatus(_ status: AppOperationStatus) -> WatchTaskStep.State {
        switch status {
        case .completed, .failed, .declined: return .done
        case .inProgress: return .active
        case .pending, .unknown: return .pending
        }
    }

    private static func mapFileChangeKind(_ kind: String) -> String {
        let lower = kind.lowercased()
        if lower.contains("add") || lower.contains("create") { return "create_file" }
        if lower.contains("delete") || lower.contains("remove") { return "delete_file" }
        return "edit_file"
    }
}
