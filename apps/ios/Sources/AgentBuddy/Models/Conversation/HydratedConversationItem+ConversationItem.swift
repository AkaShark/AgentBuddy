import Foundation
import CoreGraphics

extension HydratedConversationItem {
    var conversationItem: ConversationItem {
        ConversationItem(
            id: id,
            content: content.conversationItemContent(itemId: id),
            sourceTurnId: sourceTurnId,
            sourceTurnIndex: sourceTurnIndex.map(Int.init),
            timestamp: timestamp.map(Date.init(timeIntervalSince1970:)) ?? Date(),
            isFromUserTurnBoundary: isFromUserTurnBoundary
        )
    }
}

private extension HydratedConversationItemContent {
    func conversationItemContent(itemId: String) -> ConversationItemContent {
        switch self {
        case .user(let data):
            let images = data.imageDataUris.map(ChatImage.init(source:))
            return .user(ConversationUserMessageData(text: data.text, images: images))
        case .assistant(let data):
            return .assistant(
                ConversationAssistantMessageData(
                    text: data.text,
                    agentNickname: data.agentNickname,
                    agentRole: data.agentRole,
                    phase: data.phase
                )
            )
        case .codeReview(let data):
            return .codeReview(
                ConversationCodeReviewData(
                    findings: data.findings.map {
                        ConversationCodeReviewFinding(
                            title: $0.title,
                            body: $0.body,
                            confidenceScore: $0.confidenceScore,
                            priority: $0.priority.map(Int.init),
                            codeLocation: $0.codeLocation.map {
                                ConversationCodeReviewLocation(
                                    absoluteFilePath: $0.absoluteFilePath,
                                    lineRange: $0.lineRange.map {
                                        ConversationCodeReviewLineRange(
                                            start: Int($0.start),
                                            end: Int($0.end)
                                        )
                                    }
                                )
                            }
                        )
                    },
                    overallCorrectness: data.overallCorrectness,
                    overallExplanation: data.overallExplanation,
                    overallConfidenceScore: data.overallConfidenceScore
                )
            )
        case .reasoning(let data):
            return .reasoning(ConversationReasoningData(summary: data.summary, content: data.content))
        case .todoList(let data):
            return .todoList(
                ConversationTodoListData(
                    steps: data.steps.map {
                        ConversationPlanStep(step: $0.step, status: $0.status)
                    }
                )
            )
        case .proposedPlan(let data):
            return .proposedPlan(ConversationProposedPlanData(content: data.content))
        case .commandExecution(let data):
            return .commandExecution(
                ConversationCommandExecutionData(
                    command: data.command,
                    cwd: data.cwd,
                    status: data.status,
                    output: data.output,
                    exitCode: data.exitCode.map(Int.init),
                    durationMs: data.durationMs.map(Int.init),
                    processId: data.processId,
                    actions: data.actions.map {
                        ConversationCommandAction(
                            kind: $0.kind,
                            command: $0.command,
                            name: $0.name,
                            path: $0.path,
                            query: $0.query
                        )
                    }
                )
            )
        case .fileChange(let data):
            return .fileChange(
                ConversationFileChangeData(
                    status: data.status,
                    changes: data.changes.map {
                        ConversationFileChangeEntry(
                            path: $0.path,
                            kind: $0.kind,
                            diff: $0.diff,
                            additions: Int($0.additions),
                            deletions: Int($0.deletions)
                        )
                    },
                    outputDelta: nil
                )
            )
        case .turnDiff(let data):
            let stats = DiffStats(diff: data.diff)
            return .turnDiff(
                ConversationTurnDiffData(
                    diff: data.diff,
                    additions: stats.additions,
                    deletions: stats.deletions
                )
            )
        case .mcpToolCall(let data):
            return .mcpToolCall(
                ConversationMcpToolCallData(
                    server: data.server,
                    tool: data.tool,
                    status: data.status,
                    durationMs: data.durationMs.map(Int.init),
                    argumentsJSON: data.argumentsJson,
                    contentSummary: data.contentSummary,
                    structuredContentJSON: data.structuredContentJson,
                    rawOutputJSON: data.rawOutputJson,
                    errorMessage: data.errorMessage,
                    progressMessages: data.progressMessages,
                    computerUse: data.computerUse
                )
            )
        case .dynamicToolCall(let data):
            return .dynamicToolCall(
                ConversationDynamicToolCallData(
                    namespace: data.namespace,
                    tool: data.tool,
                    status: data.status,
                    durationMs: data.durationMs.map(Int.init),
                    success: data.success,
                    argumentsJSON: data.argumentsJson,
                    contentSummary: data.contentSummary,
                    display: data.display.map {
                        ConversationDynamicToolCallData.Display(
                            title: $0.title,
                            summary: $0.summary,
                            metadata: $0.metadata.map {
                                ConversationDynamicToolCallData.Metadata(
                                    key: $0.key,
                                    value: $0.value
                                )
                            }
                        )
                    }
                )
            )
        case .multiAgentAction(let data):
            return .multiAgentAction(
                ConversationMultiAgentActionData(
                    tool: data.tool,
                    status: data.status,
                    prompt: data.prompt,
                    targets: data.targets,
                    receiverThreadIds: data.receiverThreadIds,
                    agentStates: data.agentStates.map {
                        ConversationMultiAgentState(
                            targetId: $0.targetId,
                            status: $0.status,
                            message: $0.message
                        )
                    }
                )
            )
        case .webSearch(let data):
            return .webSearch(
                ConversationWebSearchData(
                    query: data.query,
                    actionJSON: data.actionJson,
                    isInProgress: data.isInProgress
                )
            )
        case .imageView(let data):
            return .imageView(
                ConversationImageViewData(
                    path: data.path
                )
            )
        case .imageGeneration(let data):
            return .imageGeneration(
                ConversationImageGenerationData(
                    status: data.status,
                    revisedPrompt: data.revisedPrompt,
                    imagePNG: data.imagePng,
                    savedPath: data.savedPath
                )
            )
        case .widget(let data):
            return .widget(
                ConversationWidgetData(
                    widgetState: WidgetState(
                        callId: itemId,
                        title: data.title,
                        widgetHTML: data.widgetHtml,
                        width: CGFloat(data.width),
                        height: CGFloat(data.height),
                        isFinalized: data.isFinalized,
                        appId: data.appId.flatMap { $0.isEmpty ? nil : $0 }
                    ),
                    status: data.status
                )
            )
        case .userInputResponse(let data):
            return .userInputResponse(
                ConversationUserInputResponseData(
                    questions: data.questions.map {
                        ConversationUserInputQuestionData(
                            id: $0.id,
                            header: $0.header,
                            question: $0.question,
                            answer: $0.answer,
                            options: $0.options.map {
                                ConversationUserInputOptionData(
                                    label: $0.label,
                                    description: $0.description
                                )
                            }
                        )
                    }
                )
            )
        case .divider(let data):
            switch data {
            case .contextCompaction(let isComplete):
                return .divider(.contextCompaction(isComplete: isComplete))
            case .modelRerouted(let fromModel, let toModel, let reason):
                return .divider(
                    .modelRerouted(
                        fromModel: fromModel,
                        toModel: toModel,
                        reason: reason
                    )
                )
            case .reviewEntered(let review):
                return .divider(.reviewEntered(review))
            case .reviewExited(let review):
                return .divider(.reviewExited(review))
            }
        case .error(let data):
            return .error(
                ConversationSystemErrorData(
                    title: data.title,
                    message: data.message,
                    details: data.details
                )
            )
        case .note(let data):
            return .note(ConversationNoteData(title: data.title, body: data.body))
        }
    }
}
