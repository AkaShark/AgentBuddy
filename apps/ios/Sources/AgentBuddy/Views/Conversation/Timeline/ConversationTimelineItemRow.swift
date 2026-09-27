import SwiftUI
import UIKit

struct ConversationTimelineItemRow: View, Equatable {
    private let renderCache = MessageRenderCache.shared
    @Environment(ThemeManager.self) private var themeManager

    let item: ConversationItem
    let serverId: String
    let originThreadId: String?
    let agentDirectoryVersion: UInt64
    let isPreferredExpandedCommandRow: Bool
    let isLiveTurn: Bool
    let isStreamingMessage: Bool
    let shouldPreserveRichDetail: Bool
    let reasoningDisplayMode: ConversationDetailDisplayMode
    let commandDisplayMode: ConversationDetailDisplayMode
    let toolDisplayMode: ConversationDetailDisplayMode
    let messageActionsDisabled: Bool
    let onStreamingSnapshotRendered: (() -> Void)?
    let onLiveContentLayoutChanged: (() -> Void)?
    let resolveTargetLabel: (String) -> String?
    let onWidgetPrompt: (String) -> Void
    let onEditUserItem: (ConversationItem) -> Void
    let onForkFromUserItem: (ConversationItem) -> Void
    var onOpenConversation: ((ThreadKey) -> Void)? = nil

    static func == (lhs: ConversationTimelineItemRow, rhs: ConversationTimelineItemRow) -> Bool {
        let isAssistant = lhs.item.isAssistantItem
        // For assistant rows: the StreamingRendererCoordinator owns the
        // streaming→finished lifecycle.  Skip digest, richDetail, AND
        // isStreamingMessage so the bubble body never re-evaluates when
        // a tool call arrives and a new assistant message takes over as
        // the "streaming" item.  Re-rendering the bubble would recreate
        // StreamingMarkdownContentView and replay the token reveal.
        let result = lhs.item.id == rhs.item.id &&
            (isAssistant || lhs.item.renderDigest == rhs.item.renderDigest) &&
            (isAssistant || lhs.shouldPreserveRichDetail == rhs.shouldPreserveRichDetail) &&
            (isAssistant || lhs.isStreamingMessage == rhs.isStreamingMessage) &&
            lhs.serverId == rhs.serverId &&
            lhs.originThreadId == rhs.originThreadId &&
            lhs.agentDirectoryVersion == rhs.agentDirectoryVersion &&
            lhs.isPreferredExpandedCommandRow == rhs.isPreferredExpandedCommandRow &&
            lhs.isLiveTurn == rhs.isLiveTurn &&
            lhs.reasoningDisplayMode == rhs.reasoningDisplayMode &&
            lhs.commandDisplayMode == rhs.commandDisplayMode &&
            lhs.toolDisplayMode == rhs.toolDisplayMode &&
            lhs.messageActionsDisabled == rhs.messageActionsDisabled
        return result
    }

    // 16-case switch returns AnyView rather than `some View` so the body type
    // doesn't resolve to a 4-deep `Group<_ConditionalContent<…>>` nested union.
    // Time Profiler on 2026-04-18 showed that union's `outlined destroy` +
    // witness-table accessor accounting for ~49% of main-thread CPU on device.
    var body: AnyView {
        switch item.content {
        case .user(let data):
            return AnyView(userRow(data))
        case .assistant(let data):
            return AnyView(assistantRow(data))
        case .codeReview(let data):
            return AnyView(ConversationCodeReviewRow(data: data))
        case .reasoning(let data):
            guard reasoningDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(ConversationReasoningRow(data: data, displayMode: reasoningDisplayMode))
        case .todoList(let data):
            return AnyView(ConversationTodoListRow(data: data))
        case .proposedPlan(let data):
            return AnyView(ConversationProposedPlanRow(data: data))
        case .commandExecution(let data):
            guard commandDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(commandExecutionRow(data))
        case .fileChange(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(toolCallRow(makeFileChangeModel(data)))
        case .turnDiff(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(ConversationTurnDiffRow(data: data))
        case .mcpToolCall(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            if let view = data.computerUse {
                return AnyView(
                    ComputerUseToolCallView(
                        data: data,
                        view: view,
                        externalExpanded: toolDefaultExpanded(isFailed: data.status == .failed)
                    )
                )
            } else {
                return AnyView(toolCallRow(makeMcpModel(data)))
            }
        case .dynamicToolCall(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            if CrossServerTools.isRichTool(data.tool) {
                return AnyView(CrossServerToolResultView(data: data))
            } else {
                return AnyView(toolCallRow(makeDynamicToolModel(data)))
            }
        case .multiAgentAction(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(
                SubagentCardView(
                    data: data,
                    serverId: serverId
                )
            )
        case .webSearch(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(toolCallRow(makeWebSearchModel(data)))
        case .imageView(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(toolCallRow(makeImageViewModel(data)))
        case .imageGeneration(let data):
            guard toolDisplayMode.rendersRows else { return AnyView(EmptyView()) }
            return AnyView(
                ImageGenerationToolCallView(
                    data: data,
                    externalExpanded: toolDefaultExpanded(isFailed: data.status == .failed)
                )
            )
        case .widget(let data):
            return AnyView(
                WidgetContainerView(
                    widget: data.widgetState,
                    originThreadId: originThreadId,
                    onMessage: handleWidgetMessage
                )
            )
        case .userInputResponse(let data):
            return AnyView(ConversationUserInputResponseRow(data: data))
        case .divider(let kind):
            return AnyView(ConversationDividerRow(kind: kind, isLiveTurn: isLiveTurn))
        case .error(let data):
            return AnyView(
                ConversationSystemCardRow(
                    title: data.title.isEmpty ? String(localized: "Error") : data.title,
                    content: [data.message, data.details].compactMap { $0 }.joined(separator: "\n\n"),
                    accent: AgentBuddyTheme.danger,
                    iconName: "exclamationmark.triangle.fill",
                )
            )
        case .note(let data):
            return AnyView(
                ConversationSystemCardRow(
                    title: data.title,
                    content: data.body,
                    accent: AgentBuddyTheme.textSecondary,
                    iconName: "info.circle.fill"
                )
            )
        }
    }

    @ViewBuilder
    private func commandExecutionRow(_ data: ConversationCommandExecutionData) -> some View {
        ConversationCommandExecutionRow(
            data: data,
            isPreferredExpanded: commandDefaultExpanded(data),
            displayMode: commandDisplayMode
        )
    }

    @ViewBuilder
    private func toolCallRow(_ model: ToolCallCardModel) -> some View {
        ToolCallCardView(
            model: model,
            serverId: serverId,
            externalExpanded: toolDefaultExpanded(isFailed: model.status == .failed)
        )
    }

    private func toolDefaultExpanded(isFailed: Bool) -> Bool {
        if toolDisplayMode == .collapsed,
           !isLiveTurn,
           shouldPreserveRichDetail {
            return true
        }
        return toolDisplayMode.defaultExpanded(isFailed: isFailed)
    }

    private func commandDefaultExpanded(_ data: ConversationCommandExecutionData) -> Bool {
        switch commandDisplayMode {
        case .expanded:
            return true
        case .collapsed:
            return data.isInProgress || data.status == .failed
        case .hidden:
            return false
        }
    }

    private func userRow(_ data: ConversationUserMessageData) -> some View {
        UserBubble(text: data.text, images: data.images)
            .contextMenu {
                if !data.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Button("Copy") {
                        UIPasteboard.general.string = data.text
                    }
                }

                if item.isFromUserTurnBoundary {
                    Button("Edit Message") {
                        onEditUserItem(item)
                    }
                    .disabled(messageActionsDisabled)

                    Button("Fork From Here") {
                        onForkFromUserItem(item)
                    }
                    .disabled(messageActionsDisabled)
                }
            }
    }

    @ViewBuilder
    private func assistantRow(_ data: ConversationAssistantMessageData) -> some View {
        let assistantLabel = AgentLabelFormatter.format(
            nickname: data.agentNickname,
            role: data.agentRole
        )

        StreamingAssistantBubble(
            itemId: item.id,
            text: data.text,
            isStreaming: isStreamingMessage,
            label: assistantLabel,
            themeVersion: themeManager.themeVersion,
            onSnapshotRendered: isStreamingMessage ? onStreamingSnapshotRendered : nil
        )
    }

    private func handleWidgetMessage(_ body: Any) {
        guard let dict = body as? [String: Any],
              let type = dict["_type"] as? String else { return }
        switch type {
        case "sendPrompt":
            if let text = dict["text"] as? String, !text.isEmpty {
                onWidgetPrompt(text)
            }
        case "openLink":
            if let urlString = dict["url"] as? String, let url = URL(string: urlString) {
                UIApplication.shared.open(url)
            }
        default:
            break
        }
    }
}
