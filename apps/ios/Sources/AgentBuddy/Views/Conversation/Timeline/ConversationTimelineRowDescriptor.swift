import SwiftUI

enum ConversationTimelineRowDescriptor: Identifiable, Equatable {
    case item(ConversationItem)
    case exploration(id: String, items: [ConversationItem])
    case subagentGroup(id: String, merged: ConversationMultiAgentActionData, sourceItems: [ConversationItem])

    var id: String {
        switch self {
        case .item(let item):
            return item.id
        case .exploration(let id, _):
            return id
        case .subagentGroup(let id, _, _):
            return id
        }
    }

    var isAssistantRow: Bool {
        guard case .item(let item) = self else { return false }
        return item.isAssistantItem
    }

    var isUserRow: Bool {
        guard case .item(let item) = self else { return false }
        return item.isUserItem
    }

    func preferredExpandedCommandRow(
        latestCommandExecutionItemId: String?,
        commandDisplayMode: ConversationDetailDisplayMode
    ) -> Bool {
        guard commandDisplayMode == .collapsed else {
            return commandDisplayMode == .expanded
        }
        guard case .item(let item) = self,
              case .commandExecution(let data) = item.content,
              !data.isPureExploration else {
            return false
        }
        return item.id == latestCommandExecutionItemId
    }

    func isVisible(
        reasoningDisplayMode: ConversationDetailDisplayMode,
        commandDisplayMode: ConversationDetailDisplayMode,
        toolDisplayMode: ConversationDetailDisplayMode
    ) -> Bool {
        switch self {
        case .item(let item):
            return item.isVisible(
                reasoningDisplayMode: reasoningDisplayMode,
                commandDisplayMode: commandDisplayMode,
                toolDisplayMode: toolDisplayMode
            )
        case .exploration:
            return commandDisplayMode.rendersRows
        case .subagentGroup:
            return toolDisplayMode.rendersRows
        }
    }

    static func build(from items: [ConversationItem]) -> [ConversationTimelineRowDescriptor] {
        var rows: [ConversationTimelineRowDescriptor] = []
        var explorationBuffer: [ConversationItem] = []
        var subagentBuffer: [(item: ConversationItem, data: ConversationMultiAgentActionData)] = []
        var subagentTool: String?

        func flushExplorationBuffer() {
            guard !explorationBuffer.isEmpty else { return }
            let seed = explorationBuffer.first?.id ?? UUID().uuidString
            rows.append(.exploration(id: "exploration-\(seed)", items: explorationBuffer))
            explorationBuffer.removeAll(keepingCapacity: true)
        }

        func flushSubagentBuffer() {
            guard !subagentBuffer.isEmpty else { return }
            if subagentBuffer.count == 1 {
                rows.append(.item(subagentBuffer[0].item))
            } else {
                let seed = subagentBuffer.first?.item.id ?? UUID().uuidString
                // Merge all targets, threadIds, states, pick the latest status
                var mergedTargets: [String] = []
                var mergedThreadIds: [String] = []
                var mergedStates: [ConversationMultiAgentState] = []
                var mergedPrompts: [String] = []
                var latestStatus: AppOperationStatus = .completed
                let tool = subagentBuffer.first?.data.tool ?? "spawnAgent"

                for entry in subagentBuffer {
                    mergedTargets.append(contentsOf: entry.data.targets)
                    mergedThreadIds.append(contentsOf: entry.data.receiverThreadIds)
                    mergedStates.append(contentsOf: entry.data.agentStates)
                    if let p = entry.data.prompt, !p.isEmpty {
                        mergedPrompts.append(p)
                    }
                    if entry.data.isInProgress {
                        latestStatus = .inProgress
                    }
                }

                let merged = ConversationMultiAgentActionData(
                    tool: tool,
                    status: latestStatus,
                    prompt: nil,
                    targets: mergedTargets,
                    receiverThreadIds: mergedThreadIds,
                    agentStates: mergedStates,
                    perAgentPrompts: mergedPrompts
                )
                rows.append(.subagentGroup(
                    id: "subagent-group-\(seed)",
                    merged: merged,
                    sourceItems: subagentBuffer.map(\.item)
                ))
            }
            subagentBuffer.removeAll(keepingCapacity: true)
            subagentTool = nil
        }

        for item in items {
            if item.isVisuallyEmptyNeutralItem {
                continue
            } else if case .multiAgentAction(let data) = item.content {
                let tool = data.tool.lowercased()
                if let currentTool = subagentTool, currentTool == tool {
                    subagentBuffer.append((item, data))
                } else {
                    flushExplorationBuffer()
                    flushSubagentBuffer()
                    subagentBuffer.append((item, data))
                    subagentTool = tool
                }
            } else if case .commandExecution(let data) = item.content, data.isPureExploration {
                flushSubagentBuffer()
                explorationBuffer.append(item)
            } else {
                flushExplorationBuffer()
                flushSubagentBuffer()
                rows.append(.item(item))
            }
        }

        flushExplorationBuffer()
        flushSubagentBuffer()
        return rows
    }

    static func mergeConsecutiveExplorationRows(
        _ rows: [ConversationTimelineRowDescriptor]
    ) -> [ConversationTimelineRowDescriptor] {
        var mergedRows: [ConversationTimelineRowDescriptor] = []
        var explorationAccumulator: (id: String, items: [ConversationItem])?

        func flushAccumulator() {
            guard let accumulator = explorationAccumulator else { return }
            mergedRows.append(
                .exploration(
                    id: accumulator.id,
                    items: accumulator.items
                )
            )
            explorationAccumulator = nil
        }

        for row in rows {
            switch row {
            case .exploration(let id, let items):
                if var existing = explorationAccumulator {
                    existing.items.append(contentsOf: items)
                    explorationAccumulator = existing
                } else {
                    explorationAccumulator = (id: id, items: items)
                }
            case .item(let item) where item.isExplorationCommandItem:
                if var existing = explorationAccumulator {
                    existing.items.append(item)
                    explorationAccumulator = existing
                } else {
                    explorationAccumulator = (id: "exploration-\(item.id)", items: [item])
                }
            default:
                flushAccumulator()
                mergedRows.append(row)
            }
        }

        flushAccumulator()
        return mergedRows
    }
}
