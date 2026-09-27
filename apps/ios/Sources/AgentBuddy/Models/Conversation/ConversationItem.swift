import Foundation

struct ConversationItem: Identifiable, Equatable {
    let id: String
    var content: ConversationItemContent {
        didSet { refreshRenderDigest() }
    }
    var sourceTurnId: String? {
        didSet { refreshRenderDigest() }
    }
    var sourceTurnIndex: Int? {
        didSet { refreshRenderDigest() }
    }
    var timestamp: Date {
        didSet { refreshRenderDigest() }
    }
    var isFromUserTurnBoundary: Bool {
        didSet { refreshRenderDigest() }
    }
    private(set) var renderDigest: Int

    init(
        id: String,
        content: ConversationItemContent,
        sourceTurnId: String? = nil,
        sourceTurnIndex: Int? = nil,
        timestamp: Date = Date(),
        isFromUserTurnBoundary: Bool = false
    ) {
        self.id = id
        self.content = content
        self.sourceTurnId = sourceTurnId
        self.sourceTurnIndex = sourceTurnIndex
        self.timestamp = timestamp
        self.isFromUserTurnBoundary = isFromUserTurnBoundary
        self.renderDigest = Self.computeRenderDigest(
            id: id,
            content: content,
            sourceTurnId: sourceTurnId,
            sourceTurnIndex: sourceTurnIndex,
            timestamp: timestamp,
            isFromUserTurnBoundary: isFromUserTurnBoundary
        )
    }

    var isUserItem: Bool {
        if case .user = content { return true }
        return false
    }

    var isAssistantItem: Bool {
        if case .assistant = content { return true }
        return false
    }

    var agentNickname: String? {
        if case .assistant(let data) = content {
            return data.agentNickname
        }
        return nil
    }

    var agentRole: String? {
        if case .assistant(let data) = content {
            return data.agentRole
        }
        return nil
    }

    var userText: String? {
        if case .user(let data) = content {
            return data.text
        }
        return nil
    }

    var userImages: [ChatImage] {
        if case .user(let data) = content {
            return data.images
        }
        return []
    }

    var assistantText: String? {
        if case .assistant(let data) = content {
            return data.text
        }
        return nil
    }

    var isExplorationCommandItem: Bool {
        guard case .commandExecution(let data) = content else { return false }
        return data.isPureExploration
    }

    var isVisuallyEmptyNeutralItem: Bool {
        switch content {
        case .assistant(let data):
            let textIsEmpty = data.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            let nicknameIsEmpty = data.agentNickname?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true
            let roleIsEmpty = data.agentRole?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true
            return textIsEmpty && nicknameIsEmpty && roleIsEmpty
        case .codeReview(let data):
            return data.findings.isEmpty
        case .reasoning(let data):
            return (data.summary + data.content).allSatisfy {
                $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            }
        default:
            return false
        }
    }

    var widgetState: WidgetState? {
        if case .widget(let data) = content {
            return data.widgetState
        }
        return nil
    }

    func isVisible(
        reasoningDisplayMode: ConversationDetailDisplayMode,
        commandDisplayMode: ConversationDetailDisplayMode,
        toolDisplayMode: ConversationDetailDisplayMode
    ) -> Bool {
        switch content {
        case .reasoning:
            return reasoningDisplayMode.rendersRows
        case .commandExecution:
            return commandDisplayMode.rendersRows
        case .fileChange,
             .turnDiff,
             .mcpToolCall,
             .dynamicToolCall,
             .multiAgentAction,
             .webSearch,
             .imageView,
             .imageGeneration:
            return toolDisplayMode.rendersRows
        default:
            return true
        }
    }

    mutating func refreshRenderDigest() {
        renderDigest = Self.computeRenderDigest(
            id: id,
            content: content,
            sourceTurnId: sourceTurnId,
            sourceTurnIndex: sourceTurnIndex,
            timestamp: timestamp,
            isFromUserTurnBoundary: isFromUserTurnBoundary
        )
    }
}
