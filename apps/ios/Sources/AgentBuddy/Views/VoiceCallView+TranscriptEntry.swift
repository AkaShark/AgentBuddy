import SwiftUI

enum VoiceTranscriptEntryKind: Equatable {
    case user
    case assistant
    case liveUser
    case liveAssistant
    case reasoning
    case tool
    case note
    case error
    case system
}

enum VoiceTranscriptMedia: Equatable {
    case userImages([ChatImage])
    case computerUse(ConversationMcpToolCallData, ComputerUseView)
    case imageGeneration(ConversationImageGenerationData)
}

struct VoiceTranscriptEntry: Identifiable, Equatable {
    let id: String
    let kind: VoiceTranscriptEntryKind
    let title: String
    let body: String
    let media: VoiceTranscriptMedia?

    init(
        id: String,
        kind: VoiceTranscriptEntryKind,
        title: String,
        body: String,
        media: VoiceTranscriptMedia? = nil
    ) {
        self.id = id
        self.kind = kind
        self.title = title
        self.body = body
        self.media = media
    }

    init?(_ item: ConversationItem) {
        switch item.content {
        case .user(let data):
            let body = data.text.trimmingCharacters(in: .whitespacesAndNewlines)
            if body.isEmpty && !data.images.isEmpty {
                self = VoiceTranscriptEntry(
                    id: item.id,
                    kind: .user,
                    title: "YOU",
                    body: "",
                    media: .userImages(data.images)
                )
            } else if !body.isEmpty {
                self = VoiceTranscriptEntry(
                    id: item.id,
                    kind: .user,
                    title: "YOU",
                    body: body,
                    media: data.images.isEmpty ? nil : .userImages(data.images)
                )
            } else {
                return nil
            }

        case .assistant(let data):
            let body = data.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !body.isEmpty else { return nil }
            let label = AgentLabelFormatter.format(
                nickname: data.agentNickname,
                role: data.agentRole
            ) ?? "CODEX"
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .assistant,
                title: label.uppercased(),
                body: body
            )

        case .codeReview(let data):
            guard let first = data.findings.first else { return nil }
            let body = ([first.title, first.body] + data.findings.dropFirst().map(\.title))
                .joined(separator: "\n\n")
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .assistant,
                title: "CODE REVIEW",
                body: body
            )

        case .reasoning(let data):
            let chunks = (data.summary + data.content)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            guard !chunks.isEmpty else { return nil }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .reasoning,
                title: "REASONING",
                body: chunks.joined(separator: "\n\n")
            )

        case .todoList(let data):
            guard !data.steps.isEmpty else { return nil }
            let lines = data.steps.map { "[\(planStepLabel($0.status))] \($0.step)" }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "PLAN",
                body: lines.joined(separator: "\n")
            )

        case .proposedPlan(let data):
            let body = data.content.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !body.isEmpty else { return nil }
            self = VoiceTranscriptEntry(id: item.id, kind: .tool, title: "PLAN", body: body)

        case .commandExecution(let data):
            let chunks = [data.command, data.status.displayLabel, data.output]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            guard !chunks.isEmpty else { return nil }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "COMMAND",
                body: chunks.joined(separator: "\n\n")
            )

        case .fileChange(let data):
            var chunks = ["Status: \(data.status.displayLabel)"]
            let changeSummaries = data.changes.map { "\($0.kind.uppercased()) \($0.path)\n\($0.diff)" }
            chunks.append(contentsOf: changeSummaries)
            if let outputDelta = data.outputDelta?.trimmingCharacters(in: .whitespacesAndNewlines),
               !outputDelta.isEmpty {
                chunks.append(outputDelta)
            }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "FILE CHANGES",
                body: chunks.joined(separator: "\n\n")
            )

        case .turnDiff(let data):
            let body = data.diff.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !body.isEmpty else { return nil }
            self = VoiceTranscriptEntry(id: item.id, kind: .tool, title: "DIFF", body: body)

        case .mcpToolCall(let data):
            var chunks = ["\(data.server) / \(data.tool)", "Status: \(data.status.displayLabel)"]
            if let summary = data.contentSummary?.trimmingCharacters(in: .whitespacesAndNewlines),
               !summary.isEmpty {
                chunks.append(summary)
            }
            if !data.progressMessages.isEmpty {
                chunks.append(data.progressMessages.joined(separator: "\n"))
            }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "MCP TOOL",
                body: chunks.joined(separator: "\n\n"),
                media: data.computerUse.map { .computerUse(data, $0) }
            )

        case .dynamicToolCall(let data):
            var chunks = [data.tool, "Status: \(data.status.displayLabel)"]
            if let summary = data.contentSummary?.trimmingCharacters(in: .whitespacesAndNewlines),
               !summary.isEmpty {
                chunks.append(summary)
            }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "TOOL",
                body: chunks.joined(separator: "\n\n")
            )

        case .multiAgentAction(let data):
            var chunks = ["Tool: \(data.tool)", "Status: \(data.status.displayLabel)"]
            if let prompt = data.prompt?.trimmingCharacters(in: .whitespacesAndNewlines),
               !prompt.isEmpty {
                chunks.append(prompt)
            }
            if !data.targets.isEmpty {
                chunks.append("Targets: \(data.targets.joined(separator: ", "))")
            }
            if !data.agentStates.isEmpty {
                let states = data.agentStates.map { "\($0.targetId): \($0.status)\($0.message.map { " - \($0)" } ?? "")" }
                chunks.append(states.joined(separator: "\n"))
            }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "COLLABORATION",
                body: chunks.joined(separator: "\n\n")
            )

        case .webSearch(let data):
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "WEB SEARCH",
                body: [data.query, data.actionJSON].compactMap { $0 }.joined(separator: "\n\n")
            )

        case .imageView(let data):
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "IMAGE VIEW",
                body: data.path
            )

        case .imageGeneration(let data):
            let statusWord: String = {
                switch data.status {
                case .completed: return "Completed"
                case .failed: return "Failed"
                default: return "Generating"
                }
            }()
            let body = [statusWord, data.revisedPrompt ?? ""]
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .joined(separator: "\n\n")
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "IMAGE GENERATION",
                body: body.isEmpty ? statusWord : body,
                media: .imageGeneration(data)
            )

        case .widget(let data):
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .tool,
                title: "WIDGET",
                body: "Interactive widget rendered: \(data.widgetState.title)"
            )

        case .userInputResponse(let data):
            let chunks = data.questions.map { question in
                "\(question.header ?? question.id)\n\(question.question)\n\(question.answer)"
            }
            guard !chunks.isEmpty else { return nil }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .system,
                title: "USER INPUT",
                body: chunks.joined(separator: "\n\n")
            )

        case .divider(let kind):
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .system,
                title: "SYSTEM",
                body: Self.dividerText(kind)
            )

        case .error(let data):
            let parts = [data.message, data.details].compactMap { $0 }.filter { !$0.isEmpty }
            guard !parts.isEmpty else { return nil }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .error,
                title: data.title.isEmpty ? "ERROR" : data.title.uppercased(),
                body: parts.joined(separator: "\n\n")
            )

        case .note(let data):
            let body = [data.title, data.body]
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .joined(separator: "\n\n")
            guard !body.isEmpty else { return nil }
            self = VoiceTranscriptEntry(
                id: item.id,
                kind: .note,
                title: data.title.isEmpty ? "NOTE" : data.title.uppercased(),
                body: body
            )
        }
    }

    static func live(from session: VoiceSessionState) -> VoiceTranscriptEntry? {
        let text = session.transcriptText?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !text.isEmpty else { return nil }
        let speaker = session.transcriptSpeaker?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            ? session.transcriptSpeaker!
            : (session.phase == .speaking ? "Codex" : "You")
        let kind: VoiceTranscriptEntryKind = speaker == "Codex" ? .liveAssistant : .liveUser
        let title = speaker == "Codex" ? "CODEX LIVE" : "YOU LIVE"
        return VoiceTranscriptEntry(
            id: "live-\(speaker.lowercased())",
            kind: kind,
            title: title,
            body: text
        )
    }

    private static func dividerText(_ kind: ConversationDividerKind) -> String {
        switch kind {
        case .contextCompaction(let isComplete):
            return isComplete ? "Context compaction completed." : "Context compaction in progress."
        case .modelRerouted(let fromModel, let toModel, let reason):
            return [fromModel, toModel, reason].compactMap { $0 }.joined(separator: " -> ")
        case .reviewEntered(let detail),
             .reviewExited(let detail),
             .workedFor(let detail):
            return detail
        case .generic(let title, let detail):
            return [title, detail].compactMap { $0 }.joined(separator: "\n\n")
        }
    }
}

private func planStepLabel(_ status: HydratedPlanStepStatus) -> String {
    switch status {
    case .pending:
        return "pending"
    case .inProgress:
        return "in_progress"
    case .completed:
        return "completed"
    }
}
