import SwiftUI
import HairballUI
import UIKit

enum ConversationLiveDetailRetentionPolicy {
    static func retainedRichDetailItemIDs(for items: [ConversationItem]) -> Set<String> {
        var retained = Set<String>()

        if let active = items.last(where: { $0.liveDetailStatus == .inProgress }) {
            retained.insert(active.id)
        }

        if let latestCompleted = items.reversed().first(where: { item in
            guard let status = item.liveDetailStatus else { return false }
            return status != .inProgress
        }) {
            retained.insert(latestCompleted.id)
        }

        return retained
    }
}

struct ConversationTurnTimeline: View {
    @AppStorage(ConversationDisplayPreferenceKey.reasoning) private var reasoningDisplayModeRaw = ConversationDetailDisplayMode.collapsed.rawValue
    @AppStorage(ConversationDisplayPreferenceKey.commands) private var commandDisplayModeRaw = ConversationDetailDisplayMode.collapsed.rawValue
    @AppStorage(ConversationDisplayPreferenceKey.tools) private var toolDisplayModeRaw = ConversationDetailDisplayMode.collapsed.rawValue

    let items: [ConversationItem]
    let isLive: Bool
    let serverId: String
    let originThreadId: String?
    let agentDirectoryVersion: UInt64
    let messageActionsDisabled: Bool
    let onStreamingSnapshotRendered: (() -> Void)?
    let onLiveContentLayoutChanged: (() -> Void)?
    let resolveTargetLabel: (String) -> String?
    let onWidgetPrompt: (String) -> Void
    let onEditUserItem: (ConversationItem) -> Void
    let onForkFromUserItem: (ConversationItem) -> Void
    var onOpenConversation: ((ThreadKey) -> Void)? = nil

    var body: some View {
        timelineContent
    }

    private var timelineContent: some View {
        let rows = rowDescriptors
        let retainedRichDetailItemIDs = ConversationLiveDetailRetentionPolicy.retainedRichDetailItemIDs(for: items)
        let commandDisplayMode = ConversationDetailDisplayMode.resolve(commandDisplayModeRaw)
        let latestCommandExecutionItemId = rows.reversed().compactMap { row -> String? in
            guard case .item(let item) = row,
                  case .commandExecution(let data) = item.content,
                  !data.isPureExploration else { return nil }
            return item.id
        }.first

        let firstReplyIndex = rows.firstIndex { !$0.isUserRow }

        return VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                    if index == firstReplyIndex {
                        AssistantSpeakerHeader()
                    }
                    rowView(
                        row,
                        isLastRow: index == rows.indices.last,
                        isPreferredExpandedCommandRow: row.preferredExpandedCommandRow(
                            latestCommandExecutionItemId: latestCommandExecutionItemId,
                            commandDisplayMode: commandDisplayMode
                        ),
                        retainedRichDetailItemIDs: retainedRichDetailItemIDs
                    )
                }
                    .id(row.id)
                    .modifier(RowEntranceModifier(isAssistantRow: row.isAssistantRow))
                    .onGeometryChange(for: CGFloat.self) { geometry in
                        geometry.size.height
                    } action: { oldHeight, newHeight in
                        guard isLive, abs(newHeight - oldHeight) > 0.5 else { return }
                        onLiveContentLayoutChanged?()
                    }
            }
        }
    }

    private var rowDescriptors: [ConversationTimelineRowDescriptor] {
        ConversationTimelineRowDescriptor.mergeConsecutiveExplorationRows(
            ConversationTimelineRowDescriptor.build(from: items)
        )
        .filter {
            $0.isVisible(
                reasoningDisplayMode: reasoningDisplayMode,
                commandDisplayMode: commandDisplayMode,
                toolDisplayMode: toolDisplayMode
            )
        }
    }

    private var streamingAssistantItemId: String? {
        guard isLive else { return nil }
        return items.last(where: \.isAssistantItem)?.id
    }

    private var reasoningDisplayMode: ConversationDetailDisplayMode {
        ConversationDetailDisplayMode.resolve(reasoningDisplayModeRaw)
    }

    private var commandDisplayMode: ConversationDetailDisplayMode {
        ConversationDetailDisplayMode.resolve(commandDisplayModeRaw)
    }

    private var toolDisplayMode: ConversationDetailDisplayMode {
        ConversationDetailDisplayMode.resolve(toolDisplayModeRaw)
    }

    // Returns AnyView rather than `some View` with @ViewBuilder so the result
    // type doesn't fan out to Group<_ConditionalContent<_ConditionalContent<…>, …>>.
    // Time Profiler showed 44% of main-thread CPU in `outlined destroy` of that
    // nested union; AnyView's per-node diff overhead is cheaper than destroying
    // the union every SwiftUI pass.
    private func rowView(
        _ row: ConversationTimelineRowDescriptor,
        isLastRow: Bool,
        isPreferredExpandedCommandRow: Bool,
        retainedRichDetailItemIDs: Set<String>
    ) -> AnyView {
        switch row {
        case .item(let item):
            return AnyView(
                ConversationTimelineItemRow(
                    item: item,
                    serverId: serverId,
                    originThreadId: originThreadId,
                    agentDirectoryVersion: agentDirectoryVersion,
                    isPreferredExpandedCommandRow: isPreferredExpandedCommandRow,
                    isLiveTurn: isLive,
                    isStreamingMessage: item.id == streamingAssistantItemId,
                    shouldPreserveRichDetail: retainedRichDetailItemIDs.contains(item.id),
                    reasoningDisplayMode: reasoningDisplayMode,
                    commandDisplayMode: commandDisplayMode,
                    toolDisplayMode: toolDisplayMode,
                    messageActionsDisabled: messageActionsDisabled,
                    onStreamingSnapshotRendered: item.id == streamingAssistantItemId ? onStreamingSnapshotRendered : nil,
                    onLiveContentLayoutChanged: onLiveContentLayoutChanged,
                    resolveTargetLabel: resolveTargetLabel,
                    onWidgetPrompt: onWidgetPrompt,
                    onEditUserItem: onEditUserItem,
                    onForkFromUserItem: onForkFromUserItem,
                    onOpenConversation: onOpenConversation
                )
                .equatable()
            )
        case .exploration(let id, let items):
            return AnyView(
                ConversationExplorationGroupRow(
                    id: id,
                    items: items,
                    showsCollapsedPreview: isLastRow,
                    displayMode: commandDisplayMode
                )
            )
        case .subagentGroup(_, let merged, _):
            return AnyView(
                SubagentCardView(
                    data: merged,
                    serverId: serverId
                )
            )
        }
    }
}

private extension ConversationItem {
    var liveDetailStatus: ToolCallStatus? {
        switch content {
        case .commandExecution(let data):
            return data.status.toolCallStatus
        case .fileChange(let data):
            return data.status.toolCallStatus
        case .mcpToolCall(let data):
            return data.status.toolCallStatus
        case .dynamicToolCall(let data):
            return data.status.toolCallStatus
        case .webSearch(let data):
            return data.isInProgress ? .inProgress : .completed
        case .imageView:
            return .completed
        case .imageGeneration(let data):
            return data.status.toolCallStatus
        default:
            return nil
        }
    }
}
