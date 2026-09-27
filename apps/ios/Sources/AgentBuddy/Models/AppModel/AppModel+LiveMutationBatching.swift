import Foundation

extension AppModel {
    private static let liveItemMutationCoalescingNanoseconds: UInt64 = 120_000_000 // ~8fps commands
    private static let liveThreadStateCoalescingNanoseconds: UInt64 = 150_000_000  // ~6fps metadata

    func shouldBatchLiveThreadStateUpdate(for key: ThreadKey) -> Bool {
        guard let thread = threadSnapshot(for: key) ?? cachedThreadSnapshots[key] else {
            return false
        }
        return thread.activeTurnId != nil || thread.info.status == .active
    }

    private func shouldBatchLiveCommandMutation(for key: ThreadKey) -> Bool {
        shouldBatchLiveThreadStateUpdate(for: key)
    }

    func shouldBatchCommandRowMutation(
        for key: ThreadKey,
        item: HydratedConversationItem
    ) -> Bool {
        guard shouldBatchLiveCommandMutation(for: key) else { return false }
        return shouldBatchLiveNonAssistantItem(item)
    }

    private func shouldBatchLiveNonAssistantItem(_ item: HydratedConversationItem) -> Bool {
        switch item.content {
        case .assistant, .user:
            return false
        default:
            return true
        }
    }

    func enqueueThreadStateUpdate(
        _ state: AppThreadStateRecord,
        sessionSummary: AppSessionSummary,
        agentDirectoryVersion: UInt64
    ) {
        pendingThreadStateEvents[state.key] = PendingThreadStateEvent(
            state: state,
            sessionSummary: sessionSummary,
            agentDirectoryVersion: agentDirectoryVersion
        )

        guard pendingThreadStateTask == nil else { return }
        pendingThreadStateTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.liveThreadStateCoalescingNanoseconds)
            guard let self else { return }
            await self.flushPendingThreadStateUpdates()
        }
    }

    private func flushPendingThreadStateUpdates() async {
        let events = pendingThreadStateEvents.values
        pendingThreadStateEvents.removeAll()
        pendingThreadStateTask = nil
        guard !events.isEmpty else { return }

        let relatedMutationKeys = Set(events.map(\.state.key))
        let relatedMutations = drainPendingCommandRowMutations(for: relatedMutationKeys)
        if !relatedMutations.isEmpty {
            let refreshKeys = applyCombinedLiveMutationBatch(
                Array(events),
                mutations: relatedMutations
            )
            for key in refreshKeys {
                await refreshThreadSnapshot(key: key)
            }
            return
        }

        for event in events {
            applyThreadStateUpdated(
                event.state,
                sessionSummary: event.sessionSummary,
                agentDirectoryVersion: event.agentDirectoryVersion
            )
        }
    }

    private func commandRowMutationKey(key: ThreadKey, itemId: String) -> String {
        "\(key.serverId)::\(key.threadId)::\(itemId)"
    }

    func enqueueCommandRowUpsert(
        key: ThreadKey,
        item: HydratedConversationItem
    ) {
        let mutationKey = commandRowMutationKey(key: key, itemId: item.id)
        var mutation = pendingCommandRowMutations[mutationKey]
            ?? PendingCommandRowMutation(key: key, itemId: item.id)
        mutation.upsertItem = item
        pendingCommandRowMutations[mutationKey] = mutation
        schedulePendingCommandRowMutationsFlush()
    }

    private func schedulePendingCommandRowMutationsFlush() {
        guard pendingCommandRowMutationTask == nil else { return }
        pendingCommandRowMutationTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: Self.liveItemMutationCoalescingNanoseconds)
            guard let self else { return }
            await self.flushPendingCommandRowMutations()
        }
    }

    private func flushPendingCommandRowMutations() async {
        let mutations = Array(pendingCommandRowMutations.values)
        pendingCommandRowMutations.removeAll()
        pendingCommandRowMutationTask = nil
        guard !mutations.isEmpty else { return }

        let relatedStateKeys = Set(mutations.map(\.key))
        let relatedStateEvents = drainPendingThreadStateEvents(for: relatedStateKeys)
        if !relatedStateEvents.isEmpty {
            let refreshKeys = applyCombinedLiveMutationBatch(
                relatedStateEvents,
                mutations: mutations
            )
            for key in refreshKeys {
                await refreshThreadSnapshot(key: key)
            }
            return
        }

        let refreshKeys = applyCommandRowMutationBatch(mutations)
        for key in refreshKeys {
            await refreshThreadSnapshot(key: key)
        }
    }

    private func applyCommandRowMutationBatch(
        _ mutations: [PendingCommandRowMutation]
    ) -> Set<ThreadKey> {
        guard var snapshot else {
            return Set(mutations.map(\.key))
        }

        var mutated = false
        var touchedThreadIndexes: Set<Int> = []
        let refreshKeys = applyCommandRowMutationBatch(
            mutations,
            to: &snapshot,
            touchedThreadIndexes: &touchedThreadIndexes,
            mutated: &mutated
        )

        if mutated {
            self.snapshot = snapshot
            for threadIndex in touchedThreadIndexes {
                cacheThreadSnapshot(snapshot.threads[threadIndex])
            }
            lastError = nil
        }

        return refreshKeys
    }

    private func drainPendingThreadStateEvents(
        for keys: Set<ThreadKey>
    ) -> [PendingThreadStateEvent] {
        guard !keys.isEmpty else { return [] }
        let drained = pendingThreadStateEvents
            .filter { keys.contains($0.key) }
            .map(\.value)
        for key in keys {
            pendingThreadStateEvents.removeValue(forKey: key)
        }
        if pendingThreadStateEvents.isEmpty {
            pendingThreadStateTask?.cancel()
            pendingThreadStateTask = nil
        }
        return drained
    }

    private func drainPendingCommandRowMutations(
        for keys: Set<ThreadKey>
    ) -> [PendingCommandRowMutation] {
        guard !keys.isEmpty else { return [] }
        let drained = pendingCommandRowMutations
            .values
            .filter { keys.contains($0.key) }
        pendingCommandRowMutations = pendingCommandRowMutations.filter { _, value in
            !keys.contains(value.key)
        }
        if pendingCommandRowMutations.isEmpty {
            pendingCommandRowMutationTask?.cancel()
            pendingCommandRowMutationTask = nil
        }
        return Array(drained)
    }

    private func applyCombinedLiveMutationBatch(
        _ stateEvents: [PendingThreadStateEvent],
        mutations: [PendingCommandRowMutation]
    ) -> Set<ThreadKey> {
        guard var snapshot else {
            return Set(stateEvents.map(\.state.key)).union(mutations.map(\.key))
        }

        var mutated = false
        var refreshKeys: Set<ThreadKey> = []
        var touchedThreadIndexes: Set<Int> = []

        for event in stateEvents {
            if applyThreadStateUpdated(
                to: &snapshot,
                state: event.state,
                sessionSummary: event.sessionSummary,
                agentDirectoryVersion: event.agentDirectoryVersion
            ) {
                mutated = true
                if let threadIndex = snapshot.threads.firstIndex(where: { $0.key == event.state.key }) {
                    touchedThreadIndexes.insert(threadIndex)
                }
            }
        }

        let commandRefreshKeys = applyCommandRowMutationBatch(
            mutations,
            to: &snapshot,
            touchedThreadIndexes: &touchedThreadIndexes,
            mutated: &mutated
        )
        refreshKeys.formUnion(commandRefreshKeys)

        if mutated {
            self.snapshot = snapshot
            for threadIndex in touchedThreadIndexes {
                cacheThreadSnapshot(snapshot.threads[threadIndex])
            }
            lastError = nil
        }

        return refreshKeys
    }

    @discardableResult
    func applyThreadStateUpdated(
        to snapshot: inout AppSnapshotRecord,
        state: AppThreadStateRecord,
        sessionSummary: AppSessionSummary,
        agentDirectoryVersion: UInt64
    ) -> Bool {
        guard let threadIndex = snapshot.threads.firstIndex(where: { $0.key == state.key }) else {
            return false
        }

        var thread = snapshot.threads[threadIndex]
        let shouldPreserveLiveTimestamps = Self.isLiveThreadState(
            existing: thread,
            incoming: state
        )
        let isVisibleActiveLiveThread = shouldPreserveLiveTimestamps && snapshot.activeThread == state.key
        var effectiveInfo = state.info
        if shouldPreserveLiveTimestamps {
            effectiveInfo.updatedAt = thread.info.updatedAt
        }
        thread.info = effectiveInfo
        thread.collaborationMode = state.collaborationMode
        thread.model = state.model
        thread.reasoningEffort = state.reasoningEffort
        thread.effectiveApprovalPolicy = state.effectiveApprovalPolicy
        thread.effectiveSandboxPolicy = state.effectiveSandboxPolicy
        thread.queuedFollowUps = state.queuedFollowUps
        thread.activeTurnId = state.activeTurnId
        thread.activePlanProgress = state.activePlanProgress
        thread.pendingPlanImplementationPrompt = state.pendingPlanImplementationPrompt
        thread.contextTokensUsed = state.contextTokensUsed
        thread.modelContextWindow = state.modelContextWindow
        thread.rateLimits = state.rateLimits
        thread.realtimeSessionId = state.realtimeSessionId
        thread.goal = state.goal
        thread.olderTurnsCursor = state.olderTurnsCursor
        thread.initialTurnsLoaded = state.initialTurnsLoaded
        let threadChanged = snapshot.threads[threadIndex] != thread
        snapshot.threads[threadIndex] = thread

        let sessionSummaryChanged: Bool
        if let index = snapshot.sessionSummaries.firstIndex(where: { $0.key == sessionSummary.key }) {
            let existingSummary = snapshot.sessionSummaries[index]
            var effectiveSessionSummary = sessionSummary
            if shouldPreserveLiveTimestamps {
                effectiveSessionSummary.updatedAt = existingSummary.updatedAt
            }
            sessionSummaryChanged = existingSummary != effectiveSessionSummary
            snapshot.sessionSummaries[index] = effectiveSessionSummary
        } else {
            sessionSummaryChanged = true
            snapshot.sessionSummaries.append(sessionSummary)
        }
        if sessionSummaryChanged {
            snapshot.sessionSummaries.sort(by: Self.sessionSummarySort(lhs:rhs:))
        }
        let agentDirectoryChanged = snapshot.agentDirectoryVersion != agentDirectoryVersion
        if isVisibleActiveLiveThread && !threadChanged && !agentDirectoryChanged {
            return false
        }
        snapshot.agentDirectoryVersion = agentDirectoryVersion
        return threadChanged || sessionSummaryChanged || agentDirectoryChanged
    }

    private func applyCommandRowMutationBatch(
        _ mutations: [PendingCommandRowMutation],
        to snapshot: inout AppSnapshotRecord,
        touchedThreadIndexes: inout Set<Int>,
        mutated: inout Bool
    ) -> Set<ThreadKey> {
        var refreshKeys: Set<ThreadKey> = []

        for mutation in mutations {
            guard let threadIndex = snapshot.threads.firstIndex(where: { $0.key == mutation.key }) else {
                refreshKeys.insert(mutation.key)
                continue
            }

            guard let item = mutation.upsertItem else { continue }
            var thread = snapshot.threads[threadIndex]

            if let itemIndex = thread.hydratedConversationItems.firstIndex(where: { $0.id == item.id }) {
                guard thread.hydratedConversationItems[itemIndex] != item else { continue }
                thread.hydratedConversationItems[itemIndex] = item
            } else {
                let insertionIndex = Self.insertionIndex(for: item, in: thread.hydratedConversationItems)
                thread.hydratedConversationItems.insert(item, at: insertionIndex)
            }

            snapshot.threads[threadIndex] = thread
            touchedThreadIndexes.insert(threadIndex)
            mutated = true
        }

        return refreshKeys
    }
}
