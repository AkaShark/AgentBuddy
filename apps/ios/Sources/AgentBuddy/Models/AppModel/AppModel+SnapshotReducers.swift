import Foundation

extension AppModel {
    func applySnapshot(_ snapshot: AppSnapshotRecord?) {
        let normalizedSnapshot = snapshot.map(normalizingLocalServerDisplayNames)
        let mergedSnapshot = normalizedSnapshot.map(mergingCachedThreadSnapshots)
        self.snapshot = mergedSnapshot
        if let mergedSnapshot {
            persistWakeMACs(from: mergedSnapshot.servers)
            mergedSnapshot.threads.forEach(cacheThreadSnapshot)
            lastError = nil
        }
    }

    private func persistWakeMACs(from servers: [AppServerSnapshot]) {
        for server in servers {
            SavedServerStore.updateWakeMAC(
                serverId: server.serverId,
                host: server.host,
                wakeMAC: server.wakeMac
            )
        }
    }

    private func normalizingLocalServerDisplayNames(_ snapshot: AppSnapshotRecord) -> AppSnapshotRecord {
        var snapshot = snapshot
        let fallbackName = AgentBuddyPlatform.localRuntimeDisplayName()
        for index in snapshot.servers.indices {
            guard snapshot.servers[index].isLocal else { continue }
            let displayName = snapshot.servers[index].displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            if displayName.isEmpty || displayName == "This Device" {
                snapshot.servers[index].displayName = fallbackName
            }
        }
        return snapshot
    }

    func applyThreadSnapshot(_ thread: AppThreadSnapshot) {
        let thread = mergedThreadSnapshotPreservingHydratedItems(thread)
        guard var snapshot else {
            cacheThreadSnapshot(thread)
            applySnapshot(nil)
            return
        }

        if let index = snapshot.threads.firstIndex(where: { $0.key == thread.key }) {
            snapshot.threads[index] = thread
        } else {
            snapshot.threads.append(thread)
        }
        self.snapshot = snapshot
        cacheThreadSnapshot(thread)
        lastError = nil
    }

    func applyThreadUpsert(
        _ thread: AppThreadSnapshot,
        sessionSummary: AppSessionSummary,
        agentDirectoryVersion: UInt64
    ) {
        var thread = mergedThreadSnapshotPreservingHydratedItems(thread)
        guard var snapshot else { return }

        if let index = snapshot.threads.firstIndex(where: { $0.key == thread.key }) {
            let oldThread = snapshot.threads[index]
            if oldThread.activeTurnId != nil {
                Self.preserveStreamingText(from: oldThread, into: &thread)
            }
            snapshot.threads[index] = thread
        } else {
            snapshot.threads.append(thread)
        }

        if let index = snapshot.sessionSummaries.firstIndex(where: { $0.key == sessionSummary.key }) {
            snapshot.sessionSummaries[index] = sessionSummary
        } else {
            snapshot.sessionSummaries.append(sessionSummary)
        }
        snapshot.sessionSummaries.sort(by: Self.sessionSummarySort(lhs:rhs:))
        snapshot.agentDirectoryVersion = agentDirectoryVersion
        self.snapshot = snapshot
        cacheThreadSnapshot(thread)
        lastError = nil
    }

    func applyThreadStateUpdated(
        _ state: AppThreadStateRecord,
        sessionSummary: AppSessionSummary,
        agentDirectoryVersion: UInt64
    ) {
        guard var snapshot else { return }
        guard applyThreadStateUpdated(
            to: &snapshot,
            state: state,
            sessionSummary: sessionSummary,
            agentDirectoryVersion: agentDirectoryVersion
        ) else {
            return
        }
        self.snapshot = snapshot
        if let thread = snapshot.threadSnapshot(for: state.key) {
            cacheThreadSnapshot(thread)
        }
        lastError = nil
    }

    func applyThreadItemUpsert(
        key: ThreadKey,
        item: HydratedConversationItem
    ) -> Bool {
        guard var snapshot else { return false }
        guard let threadIndex = snapshot.threads.firstIndex(where: { $0.key == key }) else {
            return false
        }

        var thread = snapshot.threads[threadIndex]
        if let itemIndex = thread.hydratedConversationItems.firstIndex(where: { $0.id == item.id }) {
            guard thread.hydratedConversationItems[itemIndex] != item else { return true }
            thread.hydratedConversationItems[itemIndex] = item
        } else {
            let insertionIndex = Self.insertionIndex(for: item, in: thread.hydratedConversationItems)
            thread.hydratedConversationItems.insert(item, at: insertionIndex)
        }

        snapshot.threads[threadIndex] = thread
        self.snapshot = snapshot
        cacheThreadSnapshot(thread)
        lastError = nil
        return true
    }

    /// Patch the matching `AppSessionSummary` in `snapshot.sessionSummaries`
    /// when the reducer hands us a freshly-derived one (via `threadItemChanged`,
    /// which now carries it as a field). Ensures home-list fields like
    /// `lastResponsePreview`, `lastToolLabel`, and `stats` track streaming
    /// items without needing a full snapshot rebuild.
    func applySessionSummary(_ summary: AppSessionSummary) {
        guard var snapshot else { return }
        if let idx = snapshot.sessionSummaries.firstIndex(where: { $0.key == summary.key }) {
            snapshot.sessionSummaries[idx] = summary
        } else {
            snapshot.sessionSummaries.append(summary)
        }
        self.snapshot = snapshot
    }

    private func applyThreadCommandExecutionUpdated(
        key: ThreadKey,
        itemId: String,
        status: AppOperationStatus,
        exitCode: Int32?,
        durationMs: Int64?,
        processId: String?
    ) -> Bool {
        guard var snapshot else { return false }
        guard let threadIndex = snapshot.threads.firstIndex(where: { $0.key == key }) else {
            return false
        }
        guard let itemIndex = snapshot.threads[threadIndex].hydratedConversationItems.firstIndex(where: { $0.id == itemId }) else {
            return false
        }

        var item = snapshot.threads[threadIndex].hydratedConversationItems[itemIndex]
        guard case .commandExecution(var data) = item.content else {
            return false
        }
        data.status = status
        data.exitCode = exitCode
        data.durationMs = durationMs
        data.processId = processId
        item.content = .commandExecution(data)
        guard snapshot.threads[threadIndex].hydratedConversationItems[itemIndex] != item else {
            return true
        }
        snapshot.threads[threadIndex].hydratedConversationItems[itemIndex] = item
        self.snapshot = snapshot
        cacheThreadSnapshot(snapshot.threads[threadIndex])
        lastError = nil
        return true
    }

    func removeThreadSnapshot(
        for key: ThreadKey,
        agentDirectoryVersion: UInt64? = nil,
        clearCache: Bool = true
    ) {
        guard var snapshot else { return }
        snapshot.threads.removeAll { $0.key == key }
        snapshot.sessionSummaries.removeAll { $0.key == key }
        if snapshot.activeThread == key {
            snapshot.activeThread = nil
        }
        if let agentDirectoryVersion {
            snapshot.agentDirectoryVersion = agentDirectoryVersion
        }
        self.snapshot = snapshot
        if clearCache {
            cachedThreadSnapshots.removeValue(forKey: key)
        }
    }

    func updateActiveThread(_ key: ThreadKey?) {
        guard var snapshot else { return }
        snapshot.activeThread = key
        self.snapshot = snapshot
    }

    private static func preserveStreamingText(
        from oldThread: AppThreadSnapshot,
        into newThread: inout AppThreadSnapshot
    ) {
        let oldItemsById = Dictionary(
            oldThread.hydratedConversationItems.map { ($0.id, $0) },
            uniquingKeysWith: { _, last in last }
        )
        for (newIndex, newItem) in newThread.hydratedConversationItems.enumerated() {
            guard let oldItem = oldItemsById[newItem.id] else {
                continue
            }
            if let preserved = preservedStreamingContent(old: oldItem.content, new: newItem.content) {
                newThread.hydratedConversationItems[newIndex].content = preserved
            }
        }
    }

    private static func preservedStreamingContent(
        old: HydratedConversationItemContent,
        new: HydratedConversationItemContent
    ) -> HydratedConversationItemContent? {
        switch (old, new) {
        case (.assistant(let oldData), .assistant(var newData))
            where oldData.text.count > newData.text.count && oldData.text.hasPrefix(newData.text):
            newData.text = oldData.text
            return .assistant(newData)
        case (.reasoning(let oldData), .reasoning(var newData))
            where oldData.content.count > newData.content.count:
            let shared = zip(oldData.content, newData.content)
            if shared.allSatisfy({ old, new in old.hasPrefix(new) }) {
                newData.content = oldData.content
                return .reasoning(newData)
            }
            return nil
        case (.proposedPlan(let oldData), .proposedPlan(var newData))
            where oldData.content.count > newData.content.count && oldData.content.hasPrefix(newData.content):
            newData.content = oldData.content
            return .proposedPlan(newData)
        default:
            return nil
        }
    }

    static func isLiveThreadState(
        existing: AppThreadSnapshot,
        incoming: AppThreadStateRecord
    ) -> Bool {
        if existing.activeTurnId != nil || incoming.activeTurnId != nil {
            return true
        }
        return existing.info.status == .active || incoming.info.status == .active
    }

    static func sessionSummarySort(lhs: AppSessionSummary, rhs: AppSessionSummary) -> Bool {
        let lhsUpdatedAt = lhs.updatedAt ?? Int64.min
        let rhsUpdatedAt = rhs.updatedAt ?? Int64.min
        if lhsUpdatedAt != rhsUpdatedAt {
            return lhsUpdatedAt > rhsUpdatedAt
        }
        if lhs.key.serverId != rhs.key.serverId {
            return lhs.key.serverId < rhs.key.serverId
        }
        return lhs.key.threadId < rhs.key.threadId
    }

    static func insertionIndex(
        for item: HydratedConversationItem,
        in items: [HydratedConversationItem]
    ) -> Int {
        guard let targetTurnIndex = item.sourceTurnIndex.map(Int.init) else {
            return items.count
        }
        if let lastSameTurnIndex = items.lastIndex(where: { $0.sourceTurnIndex.map(Int.init) == targetTurnIndex }) {
            return lastSameTurnIndex + 1
        }
        if let nextTurnIndex = items.firstIndex(where: {
            guard let sourceTurnIndex = $0.sourceTurnIndex.map(Int.init) else { return false }
            return sourceTurnIndex > targetTurnIndex
        }) {
            return nextTurnIndex
        }
        return items.count
    }

    private static func insertionIndex(
        for item: HydratedConversationItem,
        turnIndex: Int,
        turnItemIndex: Int,
        in items: [HydratedConversationItem]
    ) -> Int {
        let sameTurnIndices = items.enumerated().compactMap { index, existing in
            existing.sourceTurnIndex.map(Int.init) == turnIndex ? index : nil
        }

        if let start = sameTurnIndices.first {
            return min(start + turnItemIndex, start + sameTurnIndices.count)
        }

        if let nextTurnIndex = items.firstIndex(where: {
            guard let sourceTurnIndex = $0.sourceTurnIndex.map(Int.init) else { return false }
            return sourceTurnIndex > turnIndex
        }) {
            return nextTurnIndex
        }

        return item.sourceTurnIndex != nil ? items.count : insertionIndex(for: item, in: items)
    }
}
