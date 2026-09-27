import SwiftUI

enum ConversationStreamingViewportPolicy {
    static func shouldMaintainBottomAnchor(
        isStreaming: Bool,
        isNearBottom: Bool,
        autoFollowStreaming: Bool,
        userIsDraggingScroll: Bool
    ) -> Bool {
        guard !userIsDraggingScroll else { return false }
        if isStreaming {
            return autoFollowStreaming
        }
        return isNearBottom
    }

    static func isStreaming(_ threadStatus: ConversationStatus) -> Bool {
        if case .thinking = threadStatus {
            return true
        }
        return false
    }
}
