import Foundation

struct ConversationPlanStep: Equatable {
    let step: String
    let status: HydratedPlanStepStatus
}

struct ConversationCommandAction: Equatable {
    let kind: HydratedCommandActionKind
    let command: String
    let name: String?
    let path: String?
    let query: String?
}

struct ConversationUserMessageData: Equatable {
    var text: String
    var images: [ChatImage]
}

struct ConversationAssistantMessageData: Equatable {
    var text: String
    var agentNickname: String?
    var agentRole: String?
    var phase: AppMessagePhase?
}

struct ConversationCodeReviewLineRange: Equatable {
    var start: Int
    var end: Int
}

struct ConversationCodeReviewLocation: Equatable {
    var absoluteFilePath: String
    var lineRange: ConversationCodeReviewLineRange?
}

struct ConversationCodeReviewFinding: Equatable {
    var title: String
    var body: String
    var confidenceScore: Double
    var priority: Int?
    var codeLocation: ConversationCodeReviewLocation?
}

struct ConversationCodeReviewData: Equatable {
    var findings: [ConversationCodeReviewFinding]
    var overallCorrectness: String?
    var overallExplanation: String?
    var overallConfidenceScore: Double?
}

struct ConversationReasoningData: Equatable {
    var summary: [String]
    var content: [String]
}

struct ConversationTodoListData: Equatable {
    var steps: [ConversationPlanStep]

    var completedCount: Int {
        steps.filter { $0.status == .completed }.count
    }

    var isComplete: Bool {
        !steps.isEmpty && steps.allSatisfy { $0.status == .completed }
    }
}

struct ConversationProposedPlanData: Equatable {
    var content: String
}

struct ConversationCommandExecutionData: Equatable {
    var command: String
    var cwd: String
    var status: AppOperationStatus
    var output: String?
    var exitCode: Int?
    var durationMs: Int?
    var processId: String?
    var actions: [ConversationCommandAction]

    var isInProgress: Bool {
        status == .pending || status == .inProgress
    }

    var isPureExploration: Bool {
        guard !actions.isEmpty else { return false }
        return actions.allSatisfy {
            switch $0.kind {
            case .read, .search, .listFiles:
                return true
            case .unknown:
                return false
            }
        }
    }
}

struct ConversationFileChangeEntry: Equatable {
    var path: String
    var kind: String
    var diff: String
    var additions: Int
    var deletions: Int
}

struct ConversationFileChangeData: Equatable {
    var status: AppOperationStatus
    var changes: [ConversationFileChangeEntry]
    var outputDelta: String?
}

struct ConversationTurnDiffData: Equatable {
    var diff: String
    var additions: Int
    var deletions: Int
}

struct ConversationMcpToolCallData: Equatable {
    var server: String
    var tool: String
    var status: AppOperationStatus
    var durationMs: Int?
    var argumentsJSON: String?
    var contentSummary: String?
    var structuredContentJSON: String?
    var rawOutputJSON: String?
    var errorMessage: String?
    var progressMessages: [String]
    var computerUse: ComputerUseView?

    var isInProgress: Bool {
        status == .pending || status == .inProgress
    }
}

struct ConversationDynamicToolCallData: Equatable {
    struct Metadata: Equatable {
        var key: String
        var value: String
    }

    struct Display: Equatable {
        var title: String
        var summary: String
        var metadata: [Metadata]
    }

    var namespace: String?
    var tool: String
    var status: AppOperationStatus
    var durationMs: Int?
    var success: Bool?
    var argumentsJSON: String?
    var contentSummary: String?
    var display: Display?

    var isInProgress: Bool {
        status == .pending || status == .inProgress
    }
}

struct ConversationMultiAgentState: Equatable {
    var targetId: String
    var status: AppSubagentStatus
    var message: String?
}

struct ConversationMultiAgentActionData: Equatable {
    var tool: String
    var status: AppOperationStatus
    var prompt: String?
    var targets: [String]
    var receiverThreadIds: [String]
    var agentStates: [ConversationMultiAgentState]
    /// Per-agent prompts when multiple spawn items are merged into one group.
    /// Index-aligned with `targets`/`receiverThreadIds`. Empty for non-merged items.
    var perAgentPrompts: [String] = []

    var isInProgress: Bool {
        status == .pending || status == .inProgress
    }
}

struct ConversationWebSearchData: Equatable {
    var query: String
    var actionJSON: String?
    var isInProgress: Bool
}

struct ConversationImageViewData: Equatable {
    var path: String
}

struct ConversationImageGenerationData: Equatable {
    var status: AppOperationStatus
    var revisedPrompt: String?
    var imagePNG: Data?
    var savedPath: String?

    var isInProgress: Bool {
        status == .pending || status == .inProgress
    }
}

struct ConversationWidgetData: Equatable {
    var widgetState: WidgetState
    var status: String
}

struct ConversationUserInputOptionData: Equatable {
    var label: String
    var description: String?
}

struct ConversationUserInputQuestionData: Equatable {
    var id: String
    var header: String?
    var question: String
    var answer: String
    var options: [ConversationUserInputOptionData]
}

struct ConversationUserInputResponseData: Equatable {
    var questions: [ConversationUserInputQuestionData]
}

enum ConversationDividerKind: Equatable {
    case contextCompaction(isComplete: Bool)
    case modelRerouted(fromModel: String?, toModel: String, reason: String?)
    case reviewEntered(String)
    case reviewExited(String)
    case workedFor(String)
    case generic(title: String, detail: String?)
}

struct ConversationSystemErrorData: Equatable {
    var title: String
    var message: String
    var details: String?
}

struct ConversationNoteData: Equatable {
    var title: String
    var body: String
}

enum ConversationItemContent: Equatable {
    case user(ConversationUserMessageData)
    case assistant(ConversationAssistantMessageData)
    case codeReview(ConversationCodeReviewData)
    case reasoning(ConversationReasoningData)
    case todoList(ConversationTodoListData)
    case proposedPlan(ConversationProposedPlanData)
    case commandExecution(ConversationCommandExecutionData)
    case fileChange(ConversationFileChangeData)
    case turnDiff(ConversationTurnDiffData)
    case mcpToolCall(ConversationMcpToolCallData)
    case dynamicToolCall(ConversationDynamicToolCallData)
    case multiAgentAction(ConversationMultiAgentActionData)
    case webSearch(ConversationWebSearchData)
    case imageView(ConversationImageViewData)
    case imageGeneration(ConversationImageGenerationData)
    case widget(ConversationWidgetData)
    case userInputResponse(ConversationUserInputResponseData)
    case divider(ConversationDividerKind)
    case error(ConversationSystemErrorData)
    case note(ConversationNoteData)
}
