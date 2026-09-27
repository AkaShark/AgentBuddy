import SwiftUI
import UIKit

struct ConversationTurnRow: View, Equatable {
    let turn: TranscriptTurn
    let isExpanded: Bool
    let canCollapse: Bool
    let isLastTurn: Bool
    let viewportHeight: CGFloat
    let showTypingIndicator: Bool
    let serverId: String
    let originThreadId: String?
    let agentDirectoryVersion: UInt64
    @Environment(\.textScale) private var textScale
    @ScaledMetric(relativeTo: .body) private var bodyLineHeight: CGFloat = BuddyTextStyle.body.lineHeight
    @ScaledMetric(relativeTo: .caption) private var captionLineHeight: CGFloat = BuddyTextStyle.caption.lineHeight
    let messageActionsDisabled: Bool
    let onToggleExpansion: () -> Void
    let onStreamingSnapshotRendered: (() -> Void)?
    let onLiveContentLayoutChanged: (() -> Void)?
    let resolveTargetLabel: (String) -> String?
    let onWidgetPrompt: (String) -> Void
    let onEditUserItem: (ConversationItem) -> Void
    let onForkFromUserItem: (ConversationItem) -> Void
    var onOpenConversation: ((ThreadKey) -> Void)? = nil

    static func == (lhs: ConversationTurnRow, rhs: ConversationTurnRow) -> Bool {
        lhs.turn.id == rhs.turn.id &&
            lhs.turn.renderDigest == rhs.turn.renderDigest &&
            lhs.turn.isLive == rhs.turn.isLive &&
            lhs.isExpanded == rhs.isExpanded &&
            lhs.canCollapse == rhs.canCollapse &&
            lhs.isLastTurn == rhs.isLastTurn &&
            lhs.viewportHeight == rhs.viewportHeight &&
            lhs.showTypingIndicator == rhs.showTypingIndicator &&
            lhs.serverId == rhs.serverId &&
            lhs.originThreadId == rhs.originThreadId &&
            lhs.agentDirectoryVersion == rhs.agentDirectoryVersion &&
            lhs.messageActionsDisabled == rhs.messageActionsDisabled
    }

    var body: some View {
        if isExpanded {
            expandedContent
        } else {
            collapsedCard
        }
    }

    private var expandedContent: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            ConversationTurnTimeline(
                items: turn.items,
                isLive: turn.isLive,
                serverId: serverId,
                originThreadId: originThreadId,
                agentDirectoryVersion: agentDirectoryVersion,
                messageActionsDisabled: messageActionsDisabled,
                onStreamingSnapshotRendered: onStreamingSnapshotRendered,
                onLiveContentLayoutChanged: onLiveContentLayoutChanged,
                resolveTargetLabel: resolveTargetLabel,
                onWidgetPrompt: onWidgetPrompt,
                onEditUserItem: onEditUserItem,
                onForkFromUserItem: onForkFromUserItem,
                onOpenConversation: onOpenConversation
            )

            TypingIndicator()
                .opacity(showTypingIndicator ? 1 : 0)
                .animation(nil)

            if canCollapse {
                Button("Show Less", systemImage: "chevron.up", action: onToggleExpansion)
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.link)
                    .frame(minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
                    .buttonStyle(.plain)
            }
        }
    }

    private var collapsedCard: some View {
        Button(action: onToggleExpansion) {
            previewTextBlock
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, BuddySpacing.md)
                .padding(.top, BuddySpacing.sm)
                .padding(.bottom, collapsedFooterReservedInset)
                .timelineDetailCard()
                .overlay(alignment: .bottomLeading) {
                    footerRow
                        .padding(.horizontal, BuddySpacing.md)
                        .padding(.bottom, BuddySpacing.sm)
                }
        }
        .buttonStyle(.plain)
        .contentShape(RoundedRectangle(cornerRadius: BuddyRadius.detailCard, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilitySummary)
    }

    private var previewTextBlock: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: turn.preview.primaryText)
                .buddyText(.body, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
                .truncationMode(.tail)
                .allowsTightening(true)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(verbatim: responsePreviewText)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .lineLimit(2)
                .truncationMode(.tail)
                .multilineTextAlignment(.leading)
                .frame(
                    maxWidth: .infinity,
                    minHeight: collapsedResponseHeight,
                    maxHeight: collapsedResponseHeight,
                    alignment: .topLeading
                )
                .mask(responsePreviewMask)
        }
        .frame(maxWidth: .infinity, minHeight: collapsedPreviewHeight, maxHeight: collapsedPreviewHeight, alignment: .topLeading)
    }

    private var footerRow: some View {
        HStack(alignment: .center, spacing: 10) {
            if !footerMetadataItems.isEmpty {
                HStack(spacing: 10) {
                    ForEach(footerMetadataItems, id: \.id) { item in
                        CollapsedTurnMetaItem(systemImage: item.systemImage, text: item.text)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Spacer(minLength: BuddySpacing.xs)
            TimelineDisclosureChevron(expanded: false)
        }
        .padding(.horizontal, 2)
        .padding(.bottom, 2)
    }

    private var collapsedPreviewHeight: CGFloat { collapsedPrimaryLineHeight + collapsedResponseHeight + 4 }
    private var collapsedFooterReservedInset: CGFloat { collapsedFooterHeight + 10 }
    private var collapsedFooterHeight: CGFloat { max(captionLineHeight * textScale, 14) }

    private var responsePreviewMask: some View {
        LinearGradient(
            stops: [
                .init(color: Color.primary, location: 0),
                .init(color: Color.primary, location: 0.55),
                .init(color: Color.primary.opacity(0.58), location: 0.82),
                .init(color: Color.primary.opacity(0.24), location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private var collapsedPrimaryLineHeight: CGFloat { collapsedPreviewLineHeight }
    private var collapsedResponseHeight: CGFloat { (collapsedPreviewLineHeight * 2) + 2 }
    private var collapsedPreviewLineHeight: CGFloat { bodyLineHeight * textScale }

    private var footerMetadataItems: [CollapsedTurnMeta] {
        var items: [CollapsedTurnMeta] = []
        if let durationText = turn.preview.durationText {
            items.append(CollapsedTurnMeta(id: "duration", systemImage: "clock", text: durationText))
        }
        if turn.preview.toolCallCount > 0 {
            items.append(CollapsedTurnMeta(id: "tools", systemImage: "chevron.left.forwardslash.chevron.right", text: "\(turn.preview.toolCallCount)"))
        }
        if turn.preview.eventCount > 0 {
            items.append(CollapsedTurnMeta(id: "events", systemImage: "sparkles", text: "\(turn.preview.eventCount)"))
        }
        if turn.preview.widgetCount > 0 {
            items.append(CollapsedTurnMeta(id: "widgets", systemImage: "rectangle.3.group", text: "\(turn.preview.widgetCount)"))
        }
        if turn.preview.imageCount > 0 {
            items.append(CollapsedTurnMeta(id: "images", systemImage: "photo", text: "\(turn.preview.imageCount)"))
        }
        return items
    }

    private var secondaryPreviewText: String? {
        guard let secondaryText = turn.preview.secondaryText, secondaryText != turn.preview.primaryText else { return nil }
        return secondaryText
    }

    private var responsePreviewText: String { secondaryPreviewText ?? turn.preview.primaryText }

    private var accessibilitySummary: String {
        var parts = [turn.preview.primaryText]
        if let secondaryPreviewText { parts.append(secondaryPreviewText) }
        if let durationText = turn.preview.durationText { parts.append("Duration \(durationText)") }
        if turn.preview.toolCallCount > 0 { parts.append("\(turn.preview.toolCallCount) tool \(turn.preview.toolCallCount == 1 ? "call" : "calls")") }
        if turn.preview.widgetCount > 0 { parts.append("\(turn.preview.widgetCount) \(turn.preview.widgetCount == 1 ? "widget" : "widgets")") }
        if turn.preview.eventCount > 0 { parts.append("\(turn.preview.eventCount) \(turn.preview.eventCount == 1 ? "event" : "events")") }
        if turn.preview.imageCount > 0 { parts.append("\(turn.preview.imageCount) \(turn.preview.imageCount == 1 ? "image" : "images")") }
        return parts.joined(separator: ". ")
    }
}

private struct CollapsedTurnMeta: Identifiable {
    let id: String
    let systemImage: String
    let text: String
}

private struct CollapsedTurnMetaItem: View {
    let systemImage: String
    let text: String

    var body: some View {
        HStack(spacing: BuddySpacing.xxs) {
            Image(systemName: systemImage)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            Text(verbatim: text)
                .buddyText(.caption)
                .monospacedDigit()
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .lineLimit(1)
        }
    }
}
