import Foundation

struct TranscriptTurn: Identifiable, Equatable {
    static let collapsedExcerptLimit = 180

    struct Preview: Equatable {
        let primaryText: String
        let secondaryText: String?
        let durationText: String?
        let imageCount: Int
        let toolCallCount: Int
        let eventCount: Int
        let widgetCount: Int
    }

    let id: String
    let items: [ConversationItem]
    let preview: Preview
    let isLive: Bool
    let isCollapsedByDefault: Bool
    let renderDigest: Int

    static func build(
        from items: [ConversationItem],
        threadStatus: ConversationStatus,
        expandedRecentTurnCount: Int = 3
    ) -> [TranscriptTurn] {
        let isStreaming: Bool
        if case .thinking = threadStatus {
            isStreaming = true
        } else {
            isStreaming = false
        }
        let groupedItems = mergeTrailingStreamingGroups(in: group(items), isStreaming: isStreaming)
        guard !groupedItems.isEmpty else { return [] }

        let lastIndex = groupedItems.index(before: groupedItems.endIndex)

        let collapseBoundary = max(0, groupedItems.count - expandedRecentTurnCount)

        return groupedItems.enumerated().map { index, turnItems in
            let isLive = isStreaming && index == lastIndex
            return TranscriptTurn(
                id: turnIdentifier(for: turnItems, ordinal: index),
                items: turnItems,
                preview: makePreview(from: turnItems),
                isLive: isLive,
                isCollapsedByDefault: index < collapseBoundary,
                renderDigest: makeRenderDigest(from: turnItems, isLive: isLive)
            )
        }
    }

    func withCollapsedByDefault(_ isCollapsedByDefault: Bool) -> TranscriptTurn {
        TranscriptTurn(
            id: id,
            items: items,
            preview: preview,
            isLive: isLive,
            isCollapsedByDefault: isCollapsedByDefault,
            renderDigest: renderDigest
        )
    }

    func replacingItems(_ items: [ConversationItem]) -> TranscriptTurn {
        TranscriptTurn(
            id: id,
            items: items,
            preview: Self.makePreview(from: items),
            isLive: isLive,
            isCollapsedByDefault: isCollapsedByDefault,
            renderDigest: Self.makeRenderDigest(from: items, isLive: isLive)
        )
    }

    func replacingRenderableItems(_ items: [ConversationItem]) -> TranscriptTurn {
        TranscriptTurn(
            id: id,
            items: items,
            preview: preview,
            isLive: isLive,
            isCollapsedByDefault: isCollapsedByDefault,
            renderDigest: Self.makeRenderDigest(from: items, isLive: isLive)
        )
    }

    static func mergeConsecutiveExplorationTurnsForRendering(
        _ turns: [TranscriptTurn]
    ) -> [TranscriptTurn] {
        var merged: [TranscriptTurn] = []
        var explorationBuffer: [TranscriptTurn] = []

        func flushExplorationBuffer() {
            guard !explorationBuffer.isEmpty else { return }
            if explorationBuffer.count == 1, let single = explorationBuffer.first {
                merged.append(single)
            } else if let mergedTurn = mergedExplorationTurn(from: explorationBuffer) {
                merged.append(mergedTurn)
            }
            explorationBuffer.removeAll(keepingCapacity: true)
        }

        for turn in turns {
            guard let renderableTurn = renderableTurn(from: turn) else { continue }
            if renderableTurn.items.allSatisfy(\.isExplorationCommandItem) {
                explorationBuffer.append(renderableTurn)
            } else {
                flushExplorationBuffer()
                merged.append(renderableTurn)
            }
        }

        flushExplorationBuffer()
        return merged
    }

    private static func group(_ items: [ConversationItem]) -> [[ConversationItem]] {
        var groups: [[ConversationItem]] = []
        var current: [ConversationItem] = []
        var currentSourceTurnId: String?

        for item in items {
            let startsNewTurn =
                !current.isEmpty &&
                (
                    item.isFromUserTurnBoundary ||
                    (
                        item.sourceTurnId != nil &&
                        currentSourceTurnId != nil &&
                        item.sourceTurnId != currentSourceTurnId
                    )
                )

            if startsNewTurn {
                groups.append(current)
                current = [item]
            } else {
                current.append(item)
            }

            // Adopt the first non-nil sourceTurnId in the group so that
            // assistant items following a user-boundary item (which has
            // sourceTurnId == nil) stay in the same turn.
            if currentSourceTurnId == nil {
                currentSourceTurnId = item.sourceTurnId
            } else if current.count == 1 {
                currentSourceTurnId = current.first?.sourceTurnId
            }
        }

        if !current.isEmpty {
            groups.append(current)
        }

        return groups
    }

    private static func mergeTrailingStreamingGroups(
        in groups: [[ConversationItem]],
        isStreaming: Bool
    ) -> [[ConversationItem]] {
        guard isStreaming, groups.count > 1 else { return groups }
        guard let liveTurnStartIndex = groups.lastIndex(where: containsLiveTurnBoundary) else {
            return groups
        }
        guard liveTurnStartIndex < groups.index(before: groups.endIndex) else {
            return groups
        }

        let mergedLiveTurn = groups[liveTurnStartIndex...].flatMap { $0 }
        return Array(groups[..<liveTurnStartIndex]) + [mergedLiveTurn]
    }

    private static func containsLiveTurnBoundary(_ items: [ConversationItem]) -> Bool {
        items.contains { item in
            item.isFromUserTurnBoundary || item.isUserItem
        }
    }

    private static func renderableTurn(from turn: TranscriptTurn) -> TranscriptTurn? {
        let visibleItems = turn.items.filter { !$0.isVisuallyEmptyNeutralItem }
        guard !visibleItems.isEmpty else { return nil }
        guard visibleItems.count != turn.items.count else { return turn }
        return turn.replacingRenderableItems(visibleItems)
    }

    private static func mergedExplorationTurn(from turns: [TranscriptTurn]) -> TranscriptTurn? {
        guard let first = turns.first else { return nil }
        let items = turns.flatMap(\.items)
        let isLive = turns.contains(where: \.isLive)
        return TranscriptTurn(
            id: "exploration-turn-\(first.id)",
            items: items,
            preview: makePreview(from: items),
            isLive: isLive,
            isCollapsedByDefault: turns.allSatisfy(\.isCollapsedByDefault),
            renderDigest: makeRenderDigest(from: items, isLive: isLive)
        )
    }

    private static func turnIdentifier(for items: [ConversationItem], ordinal: Int) -> String {
        if let first = items.first {
            if let sourceTurnId = items.first(where: { $0.sourceTurnId != nil })?.sourceTurnId {
                return "turn-\(sourceTurnId)-\(first.id)"
            }
            return "turn-\(first.id)"
        }
        return "turn-\(ordinal)"
    }

    private static func makeRenderDigest(from items: [ConversationItem], isLive: Bool) -> Int {
        var hasher = Hasher()
        hasher.combine(items.count)
        hasher.combine(isLive)
        for item in items {
            hasher.combine(item.id)
            hasher.combine(item.renderDigest)
        }
        return hasher.finalize()
    }
}
