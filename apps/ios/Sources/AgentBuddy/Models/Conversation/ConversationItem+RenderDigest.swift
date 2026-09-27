import Foundation

extension ConversationItem {
    static func computeRenderDigest(
        id: String,
        content: ConversationItemContent,
        sourceTurnId: String?,
        sourceTurnIndex: Int?,
        timestamp: Date,
        isFromUserTurnBoundary: Bool
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(id)
        hasher.combine(sourceTurnId)
        hasher.combine(sourceTurnIndex)
        hasher.combine(timestamp.timeIntervalSince1970)
        hasher.combine(isFromUserTurnBoundary)
        combine(content: content, into: &hasher)
        return hasher.finalize()
    }

    private static func combine(content: ConversationItemContent, into hasher: inout Hasher) {
        switch content {
        case .user(let data):
            hasher.combine("user")
            hasher.combine(data.text)
            hasher.combine(data.images.count)
            for image in data.images {
                hasher.combine(image.cacheKey)
            }
        case .assistant(let data):
            hasher.combine("assistant")
            hasher.combine(data.text)
            hasher.combine(data.agentNickname)
            hasher.combine(data.agentRole)
            hasher.combine(data.phase)
        case .codeReview(let data):
            hasher.combine("codeReview")
            hasher.combine(data.overallCorrectness)
            hasher.combine(data.overallExplanation)
            hasher.combine(data.overallConfidenceScore)
            for finding in data.findings {
                hasher.combine(finding.title)
                hasher.combine(finding.body)
                hasher.combine(finding.confidenceScore)
                hasher.combine(finding.priority)
                hasher.combine(finding.codeLocation?.absoluteFilePath)
                hasher.combine(finding.codeLocation?.lineRange?.start)
                hasher.combine(finding.codeLocation?.lineRange?.end)
            }
        case .reasoning(let data):
            hasher.combine("reasoning")
            hasher.combine(data.summary)
            hasher.combine(data.content)
        case .todoList(let data):
            hasher.combine("todoList")
            for step in data.steps {
                hasher.combine(step.step)
                hasher.combine(String(describing: step.status))
            }
        case .proposedPlan(let data):
            hasher.combine("proposedPlan")
            hasher.combine(data.content)
        case .commandExecution(let data):
            hasher.combine("commandExecution")
            hasher.combine(data.command)
            hasher.combine(data.cwd)
            hasher.combine(String(describing: data.status))
            hasher.combine(data.output)
            hasher.combine(data.exitCode)
            hasher.combine(data.durationMs)
            hasher.combine(data.processId)
            for action in data.actions {
                hasher.combine(String(describing: action.kind))
                hasher.combine(action.command)
                hasher.combine(action.name)
                hasher.combine(action.path)
                hasher.combine(action.query)
            }
        case .fileChange(let data):
            hasher.combine("fileChange")
            hasher.combine(String(describing: data.status))
            hasher.combine(data.outputDelta)
            for change in data.changes {
                hasher.combine(change.path)
                hasher.combine(change.kind)
                hasher.combine(change.diff)
            }
        case .turnDiff(let data):
            hasher.combine("turnDiff")
            hasher.combine(data.diff)
        case .mcpToolCall(let data):
            hasher.combine("mcpToolCall")
            hasher.combine(data.server)
            hasher.combine(data.tool)
            hasher.combine(String(describing: data.status))
            hasher.combine(data.durationMs)
            hasher.combine(data.argumentsJSON)
            hasher.combine(data.contentSummary)
            hasher.combine(data.structuredContentJSON)
            hasher.combine(data.rawOutputJSON)
            hasher.combine(data.errorMessage)
            hasher.combine(data.progressMessages)
        case .dynamicToolCall(let data):
            hasher.combine("dynamicToolCall")
            hasher.combine(data.tool)
            hasher.combine(String(describing: data.status))
            hasher.combine(data.durationMs)
            hasher.combine(data.success)
            hasher.combine(data.argumentsJSON)
            hasher.combine(data.contentSummary)
        case .multiAgentAction(let data):
            hasher.combine("multiAgentAction")
            hasher.combine(data.tool)
            hasher.combine(String(describing: data.status))
            hasher.combine(data.prompt)
            hasher.combine(data.targets)
            hasher.combine(data.receiverThreadIds)
            hasher.combine(data.perAgentPrompts)
            for state in data.agentStates {
                hasher.combine(state.targetId)
                hasher.combine(state.status)
                hasher.combine(state.message)
            }
        case .webSearch(let data):
            hasher.combine("webSearch")
            hasher.combine(data.query)
            hasher.combine(data.actionJSON)
            hasher.combine(data.isInProgress)
        case .imageView(let data):
            hasher.combine("imageView")
            hasher.combine(data.path)
        case .imageGeneration(let data):
            hasher.combine("imageGeneration")
            hasher.combine(String(describing: data.status))
            hasher.combine(data.revisedPrompt)
            hasher.combine(data.imagePNG?.count)
            hasher.combine(data.savedPath)
        case .widget(let data):
            hasher.combine("widget")
            hasher.combine(data.status)
            hasher.combine(data.widgetState.callId)
            hasher.combine(data.widgetState.title)
            hasher.combine(data.widgetState.widgetHTML)
            hasher.combine(data.widgetState.width)
            hasher.combine(data.widgetState.height)
            hasher.combine(data.widgetState.isFinalized)
        case .userInputResponse(let data):
            hasher.combine("userInputResponse")
            for question in data.questions {
                hasher.combine(question.id)
                hasher.combine(question.header)
                hasher.combine(question.question)
                hasher.combine(question.answer)
                for option in question.options {
                    hasher.combine(option.label)
                    hasher.combine(option.description)
                }
            }
        case .divider(let divider):
            hasher.combine("divider")
            switch divider {
            case .contextCompaction(let isComplete):
                hasher.combine("contextCompaction")
                hasher.combine(isComplete)
            case .modelRerouted(let fromModel, let toModel, let reason):
                hasher.combine("modelRerouted")
                hasher.combine(fromModel)
                hasher.combine(toModel)
                hasher.combine(reason)
            case .reviewEntered(let review):
                hasher.combine("reviewEntered")
                hasher.combine(review)
            case .reviewExited(let review):
                hasher.combine("reviewExited")
                hasher.combine(review)
            case .workedFor(let duration):
                hasher.combine("workedFor")
                hasher.combine(duration)
            case .generic(let title, let detail):
                hasher.combine("genericDivider")
                hasher.combine(title)
                hasher.combine(detail)
            }
        case .error(let data):
            hasher.combine("error")
            hasher.combine(data.title)
            hasher.combine(data.message)
            hasher.combine(data.details)
        case .note(let data):
            hasher.combine("note")
            hasher.combine(data.title)
            hasher.combine(data.body)
        }
    }
}
