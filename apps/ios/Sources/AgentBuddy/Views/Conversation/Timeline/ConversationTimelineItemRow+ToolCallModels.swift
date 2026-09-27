import SwiftUI

extension ConversationTimelineItemRow {
    func makeFileChangeModel(_ data: ConversationFileChangeData) -> ToolCallCardModel {
        let changedPaths = data.changes.map(\.path)
        let summary = fileChangeSummary(for: data)

        let diffSections = data.changes.compactMap { change -> ToolCallSection? in
            guard !change.diff.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            let label = data.changes.count > 1 ? workspaceTitle(for: change.path) : ""
            return .diff(label: label, content: change.diff)
        }

        var sections: [ToolCallSection] = []
        if diffSections.isEmpty, !changedPaths.isEmpty {
            sections.append(.list(label: "Files", items: changedPaths.map(workspaceTitle(for:))))
        }
        sections.append(contentsOf: diffSections)
        if let outputDelta = data.outputDelta?.trimmingCharacters(in: .whitespacesAndNewlines), !outputDelta.isEmpty {
            sections.append(.text(label: "Output", content: outputDelta))
        }

        return ToolCallCardModel(
            kind: .fileChange,
            title: "File Change",
            summary: summary.plainText,
            attributedSummary: summary.attributedText,
            status: data.status.toolCallStatus,
            duration: nil,
            sections: sections
        )
    }

    private func fileChangeSummary(for data: ConversationFileChangeData) -> (plainText: String, attributedText: AttributedString?) {
        guard !data.changes.isEmpty else {
            return ("File changes", nil)
        }

        let additions = data.changes.reduce(0) { $0 + $1.additions }
        let deletions = data.changes.reduce(0) { $0 + $1.deletions }
        let hasCountSummary = additions > 0 || deletions > 0

        if data.changes.count == 1, let change = data.changes.first {
            let verb = fileChangeVerb(for: change.kind)
            let filename = workspaceTitle(for: change.path)
            guard hasCountSummary else {
                return ("\(verb) \(filename)", nil)
            }

            let plainText = "\(verb) \(filename) +\(additions) -\(deletions)"

            var attributed = AttributedString()

            var verbText = AttributedString("\(verb) ")
            verbText.foregroundColor = AgentBuddyTheme.textSecondary
            attributed.append(verbText)

            var fileText = AttributedString(filename)
            fileText.foregroundColor = AgentBuddyTheme.accent
            attributed.append(fileText)

            var additionsText = AttributedString(" +\(additions)")
            additionsText.foregroundColor = AgentBuddyTheme.success
            attributed.append(additionsText)

            var deletionsText = AttributedString(" -\(deletions)")
            deletionsText.foregroundColor = AgentBuddyTheme.danger
            attributed.append(deletionsText)

            return (plainText, attributed)
        }

        guard hasCountSummary else {
            return ("Changed \(data.changes.count) files", nil)
        }

        let plainText = "Changed \(data.changes.count) files +\(additions) -\(deletions)"
        var attributed = AttributedString("Changed \(data.changes.count) files")
        attributed.foregroundColor = AgentBuddyTheme.textSystem

        var additionsText = AttributedString(" +\(additions)")
        additionsText.foregroundColor = AgentBuddyTheme.success
        attributed.append(additionsText)

        var deletionsText = AttributedString(" -\(deletions)")
        deletionsText.foregroundColor = AgentBuddyTheme.danger
        attributed.append(deletionsText)

        return (plainText, attributed)
    }

    private func fileChangeVerb(for kind: String) -> String {
        switch kind.lowercased() {
        case "add":
            return "Added"
        case "delete":
            return "Deleted"
        case "update":
            return "Edited"
        default:
            return "Changed"
        }
    }

    func makeMcpModel(_ data: ConversationMcpToolCallData) -> ToolCallCardModel {
        var sections: [ToolCallSection] = []
        if let arguments = data.argumentsJSON, !arguments.isEmpty {
            sections.append(.json(label: "Arguments", content: arguments))
        }
        if let contentSummary = data.contentSummary, !contentSummary.isEmpty {
            sections.append(.text(label: "Result", content: contentSummary))
        }
        if let structured = data.structuredContentJSON, !structured.isEmpty {
            sections.append(.json(label: "Structured", content: structured))
        }
        if let raw = data.rawOutputJSON, !raw.isEmpty {
            sections.append(.json(label: "Raw Output", content: raw))
        }
        if !data.progressMessages.isEmpty {
            sections.append(.progress(label: "Progress", items: data.progressMessages))
        }
        if let error = data.errorMessage, !error.isEmpty {
            sections.append(.text(label: "Error", content: error))
        }

        let summary = data.server.isEmpty
            ? data.tool
            : "\(data.server).\(data.tool)"

        return ToolCallCardModel(
            kind: .mcpToolCall,
            title: "MCP Tool Call",
            summary: summary,
            status: data.status.toolCallStatus,
            duration: timelineFormatDuration(data.durationMs),
            sections: sections
        )
    }

    func makeDynamicToolModel(_ data: ConversationDynamicToolCallData) -> ToolCallCardModel {
        var sections: [ToolCallSection] = []
        var metadata: [ToolCallKeyValue] = []
        if let display = data.display {
            metadata.append(contentsOf: display.metadata.map {
                ToolCallKeyValue(key: $0.key, value: $0.value)
            })
        }
        if let namespace = data.namespace, !namespace.isEmpty {
            metadata.append(ToolCallKeyValue(key: "Namespace", value: namespace))
        }
        if let success = data.success {
            metadata.append(ToolCallKeyValue(key: "Success", value: success ? "true" : "false"))
        }
        if !metadata.isEmpty {
            sections.append(.kv(label: "Metadata", entries: metadata))
        }
        if let arguments = data.argumentsJSON, !arguments.isEmpty {
            sections.append(.json(label: "Arguments", content: arguments))
        }
        if let contentSummary = data.contentSummary, !contentSummary.isEmpty {
            sections.append(.text(label: "Result", content: contentSummary))
        }
        let title = data.display?.title ?? "Dynamic Tool Call"
        let summary = data.display?.summary
            ?? data.namespace.map { "\($0).\(data.tool)" }
            ?? data.tool

        return ToolCallCardModel(
            kind: .mcpToolCall,
            title: title,
            summary: summary,
            status: data.status.toolCallStatus,
            duration: timelineFormatDuration(data.durationMs),
            sections: sections
        )
    }

    func makeWebSearchModel(_ data: ConversationWebSearchData) -> ToolCallCardModel {
        var sections: [ToolCallSection] = []
        if !data.query.isEmpty {
            sections.append(.text(label: "Query", content: data.query))
        }
        if let action = data.actionJSON, !action.isEmpty {
            sections.append(.json(label: "Action", content: action))
        }
        return ToolCallCardModel(
            kind: .webSearch,
            title: "Web Search",
            summary: data.query.isEmpty ? "Web search" : "Web search for \(data.query)",
            status: data.isInProgress ? .inProgress : .completed,
            duration: nil,
            sections: sections
        )
    }

    func makeImageViewModel(_ data: ConversationImageViewData) -> ToolCallCardModel {
        let trimmedPath = data.path.trimmingCharacters(in: .whitespacesAndNewlines)
        let displayName = workspaceTitle(for: trimmedPath)
        return ToolCallCardModel(
            kind: .imageView,
            title: "Image View",
            summary: displayName.isEmpty ? "Image" : displayName,
            status: .completed,
            duration: nil,
            sections: [
                .kv(
                    label: "Metadata",
                    entries: [ToolCallKeyValue(key: "Path", value: trimmedPath)]
                )
            ],
            initiallyExpanded: true
        )
    }
}
