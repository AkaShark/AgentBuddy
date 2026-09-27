import SwiftUI
import Hairball
import HairballUI
import Nuke
import NukeUI
import UIKit

// MARK: - Full message bubble (used in conversation)

struct MessageBubbleView: View {
    private let renderCache = MessageRenderCache.shared
    @Environment(\.activeThreadKey) private var activeThreadKey
    let message: ChatMessage
    let serverId: String?
    let originThreadId: String?
    let agentDirectoryVersion: UInt64
    let isStreamingMessage: Bool
    let actionsDisabled: Bool
    let onStreamingSnapshotRendered: (() -> Void)?
    let resolveTargetLabel: ((String) -> String?)?
    let onWidgetPrompt: ((String) -> Void)?
    let onEditUserMessage: ((ChatMessage) -> Void)?
    let onForkFromUserMessage: ((ChatMessage) -> Void)?
    private let contentFontSize = AgentBuddyFont.mintBodyPointSize

    init(
        message: ChatMessage,
        serverId: String? = nil,
        originThreadId: String? = nil,
        agentDirectoryVersion: UInt64 = 0,
        isStreamingMessage: Bool = false,
        actionsDisabled: Bool = false,
        onStreamingSnapshotRendered: (() -> Void)? = nil,
        resolveTargetLabel: ((String) -> String?)? = nil,
        onWidgetPrompt: ((String) -> Void)? = nil,
        onEditUserMessage: ((ChatMessage) -> Void)? = nil,
        onForkFromUserMessage: ((ChatMessage) -> Void)? = nil
    ) {
        self.message = message
        self.serverId = serverId
        self.originThreadId = originThreadId
        self.agentDirectoryVersion = agentDirectoryVersion
        self.isStreamingMessage = isStreamingMessage
        self.actionsDisabled = actionsDisabled
        self.onStreamingSnapshotRendered = onStreamingSnapshotRendered
        self.resolveTargetLabel = resolveTargetLabel
        self.onWidgetPrompt = onWidgetPrompt
        self.onEditUserMessage = onEditUserMessage
        self.onForkFromUserMessage = onForkFromUserMessage
    }

    var body: some View {
        Group {
            if message.role == .user {
                userBubbleWithActions
            } else if message.role == .assistant {
                assistantContent
            } else if isReasoning {
                HStack(alignment: .top, spacing: 0) {
                    reasoningContent
                    Spacer(minLength: 20)
                }
            } else {
                HStack(alignment: .top, spacing: 0) {
                    systemBubble
                    Spacer(minLength: 20)
                }
            }
        }
    }

    private var renderRevisionKey: MessageRenderCache.RevisionKey {
        MessageRenderCache.makeRevisionKey(
            for: message,
            serverId: serverId,
            agentDirectoryVersion: agentDirectoryVersion,
            isStreaming: isStreamingMessage
        )
    }

    private var resolvedOriginThreadId: String? {
        originThreadId ?? activeThreadKey?.threadId
    }

    private var isReasoning: Bool {
        let trimmed = message.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("### ") else { return false }
        let firstLine = trimmed.prefix(while: { $0 != "\n" })
        return firstLine.lowercased().contains("reason")
    }

    private var supportsUserActions: Bool {
        message.role == .user &&
            message.isFromUserTurnBoundary &&
            message.sourceTurnIndex != nil
    }

    private var canCopyMessageText: Bool {
        !message.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var userBubbleWithActions: some View {
        UserBubble(text: message.text, images: message.images)
            .contextMenu {
                if canCopyMessageText {
                    Button("Copy Message") {
                        UIPasteboard.general.string = message.text
                    }
                }

                if supportsUserActions {
                    Button("Edit Message") {
                        onEditUserMessage?(message)
                    }
                    .disabled(actionsDisabled || onEditUserMessage == nil)

                    Button("Fork From Here") {
                        onForkFromUserMessage?(message)
                    }
                    .disabled(actionsDisabled || onForkFromUserMessage == nil)
                }
            }
    }

    @ViewBuilder
    private var assistantContent: some View {
        Group {
            if isStreamingMessage {
                StreamingAssistantBubble(
                    itemId: message.id.uuidString,
                    text: message.text,
                    isStreaming: true,
                    label: assistantAgentLabel,
                    onSnapshotRendered: onStreamingSnapshotRendered
                )
            } else {
                AssistantBlocksBubble(
                    segments: assistantSegmentsForRendering,
                    label: assistantAgentLabel
                )
            }
        }
        .contextMenu {
            if canCopyMessageText {
                Button("Copy Message") {
                    UIPasteboard.general.string = message.text
                }
            }
        }
    }

    private var assistantAgentLabel: String? {
        AgentLabelFormatter.format(
            nickname: message.agentNickname,
            role: message.agentRole
        )
    }

    private var reasoningContent: some View {
        let (_, body) = extractSystemTitleAndBody(message.text)
        return Text(normalizedReasoningText(body))
            .buddyText(.label, weight: .regular)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contextMenu {
                if canCopyMessageText {
                    Button("Copy Message") {
                        UIPasteboard.general.string = message.text
                    }
                }
            }
    }

    @ViewBuilder
    private var systemBubble: some View {
        if let widget = message.widgetState {
            WidgetContainerView(
                widget: widget,
                originThreadId: resolvedOriginThreadId,
                onMessage: handleWidgetMessage
            )
        } else {
            let parsed = systemParseResultForRendering
            switch parsed {
            case .recognized(let model):
                ToolCallCardView(model: model, serverId: serverId)
            case .unrecognized:
                genericSystemBubble
            }
        }
    }

    private func handleWidgetMessage(_ body: Any) {
        guard let dict = body as? [String: Any],
              let type = dict["_type"] as? String else { return }
        switch type {
        case "sendPrompt":
            if let text = dict["text"] as? String, !text.isEmpty {
                onWidgetPrompt?(text)
            }
        case "openLink":
            if let urlStr = dict["url"] as? String, let url = URL(string: urlStr) {
                UIApplication.shared.open(url)
            }
        default:
            break
        }
    }

    private var genericSystemBubble: some View {
        let (title, body) = extractSystemTitleAndBody(message.text)
        let markdown = title == nil ? message.text : body
        let displayTitle = title ?? String(localized: "System")

        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                Image(systemName: "info.circle")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
                Text(verbatim: displayTitle)
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
            }

            if !markdown.isEmpty {
                AgentBuddyMarkdownView(
                    markdown: markdown,
                    style: .system,
                    bodySize: contentFontSize,
                    codeSize: AgentBuddyFont.mintCodePointSize
                )
                    .padding(.top, BuddySpacing.xs)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.soft, radius: BuddyRadius.detailCard, padding: BuddySpacing.md)
        .contextMenu {
            if canCopyMessageText {
                Button("Copy Message") {
                    UIPasteboard.general.string = message.text
                }
            }
        }
    }

    private func extractSystemTitleAndBody(_ text: String) -> (String?, String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("### ") else { return (nil, trimmed) }
        let lines = trimmed.split(separator: "\n", omittingEmptySubsequences: false)
        guard let first = lines.first else { return (nil, trimmed) }
        let title = first.dropFirst(4).trimmingCharacters(in: .whitespacesAndNewlines)
        let body = lines.dropFirst().joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        return (title.isEmpty ? nil : title, body)
    }

    private func normalizedReasoningText(_ body: String) -> String {
        body
            .components(separatedBy: .newlines)
            .map { line in
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                if trimmed.hasPrefix("**"), trimmed.hasSuffix("**"), trimmed.count > 4 {
                    return String(trimmed.dropFirst(2).dropLast(2))
                }
                return line
            }
            .joined(separator: "\n")
    }

    private var assistantSegmentsForRendering: [MessageRenderCache.AssistantSegment] {
        renderCache.assistantSegments(
            for: message,
            key: renderRevisionKey
        )
    }

    private var systemParseResultForRendering: ToolCallParseResult {
        renderCache.systemParseResult(
            for: message,
            key: renderRevisionKey,
            resolveTargetLabel: resolveTargetLabel
        )
    }
}

#if DEBUG
#Preview("Message Bubbles") {
    AgentBuddyPreviewScene {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                ForEach(AgentBuddyPreviewData.sampleMessages) { message in
                    MessageBubbleView(
                        message: message,
                        serverId: AgentBuddyPreviewData.sampleServer.id
                    )
                }
            }
            .padding(16)
        }
    }
}
#endif
