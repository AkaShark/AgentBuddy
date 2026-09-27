import Foundation

extension AppModel {
    /// Mutate an in-flight widget bubble's `HydratedWidgetData` so the
    /// timeline `WidgetWebView` picks up the growing HTML via its existing
    /// `Coordinator.scheduleUpdate` debounce. The reducer guarantees
    /// `is_finalized == false` on these; the finalized update arrives
    /// separately as `.threadItemChanged` and must win.
    func applyStreamingWidget(
        key: ThreadKey,
        itemId: String,
        widget: HydratedWidgetData
    ) {
        guard var snapshot else {
            LLog.warn("streaming", "applyStreamingWidget: no snapshot")
            return
        }
        guard let threadIndex = snapshot.threads.firstIndex(where: { $0.key == key }) else {
            LLog.warn("streaming", "applyStreamingWidget: thread not in snapshot",
                      fields: ["threadId": key.threadId, "htmlLen": widget.widgetHtml.count])
            return
        }
        var thread = snapshot.threads[threadIndex]
        if let itemIndex = thread.hydratedConversationItems.firstIndex(where: { $0.id == itemId }) {
            var item = thread.hydratedConversationItems[itemIndex]
            if case .widget(let existing) = item.content, existing.isFinalized { return }
            if case .widget(let existing) = item.content, existing == widget { return }
            item.content = .widget(widget)
            thread.hydratedConversationItems[itemIndex] = item
            LLog.info("streaming", "widget delta mutated existing",
                      fields: ["itemId": itemId, "htmlLen": widget.widgetHtml.count])
        } else {
            let placeholder = HydratedConversationItem(
                id: itemId,
                content: .widget(widget),
                sourceTurnId: thread.activeTurnId,
                sourceTurnIndex: nil,
                timestamp: nil,
                isFromUserTurnBoundary: false
            )
            thread.hydratedConversationItems.append(placeholder)
            LLog.info("streaming", "widget delta inserted placeholder",
                      fields: ["itemId": itemId, "htmlLen": widget.widgetHtml.count,
                               "sourceTurnId": thread.activeTurnId ?? "nil"])
        }
        snapshot.threads[threadIndex] = thread
        self.snapshot = snapshot
        cacheThreadSnapshot(thread)
    }

    func applyThreadStreamingDelta(
        key: ThreadKey,
        itemId: String,
        kind: ThreadStreamingDeltaKind,
        text: String
    ) -> Bool {
        guard var snapshot else { return false }
        guard let threadIndex = snapshot.threads.firstIndex(where: { $0.key == key }) else {
            return false
        }

        var thread = snapshot.threads[threadIndex]
        guard let itemIndex = thread.hydratedConversationItems.firstIndex(where: { $0.id == itemId }) else {
            return false
        }

        var item = thread.hydratedConversationItems[itemIndex]
        guard let updatedContent = applyingStreamingDelta(
            kind: kind,
            text: text,
            to: item.content
        ) else {
            return false
        }

        item.content = updatedContent
        guard thread.hydratedConversationItems[itemIndex] != item else {
            return true
        }

        thread.hydratedConversationItems[itemIndex] = item
        snapshot.threads[threadIndex] = thread
        self.snapshot = snapshot
        cacheThreadSnapshot(thread)
        lastError = nil
        return true
    }

    private func applyingStreamingDelta(
        kind: ThreadStreamingDeltaKind,
        text: String,
        to content: HydratedConversationItemContent
    ) -> HydratedConversationItemContent? {
        switch (kind, content) {
        case (.assistantText, .assistant(var data)):
            data.text += text
            return .assistant(data)
        case (.reasoningText, .reasoning(var data)):
            if data.content.isEmpty {
                data.content.append(text)
            } else {
                data.content[data.content.index(before: data.content.endIndex)] += text
            }
            return .reasoning(data)
        case (.planText, .proposedPlan(var data)):
            data.content += text
            return .proposedPlan(data)
        case (.commandOutput, .commandExecution(var data)):
            data.output = (data.output ?? "") + text
            return .commandExecution(data)
        case (.mcpProgress, .mcpToolCall(var data)):
            if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                data.progressMessages.append(text)
            }
            return .mcpToolCall(data)
        default:
            return nil
        }
    }
}
