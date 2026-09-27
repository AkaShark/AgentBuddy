import Foundation

extension AppModel {
    func handleStoreUpdate(_ update: AppStoreUpdateRecord) async {
        switch update {
        case .threadUpserted(let thread, let sessionSummary, let agentDirectoryVersion):
            applyThreadUpsert(
                thread,
                sessionSummary: sessionSummary,
                agentDirectoryVersion: agentDirectoryVersion
            )
        case .threadMetadataChanged(let state, let sessionSummary, let agentDirectoryVersion):
            if shouldBatchLiveThreadStateUpdate(for: state.key) {
                enqueueThreadStateUpdate(
                    state,
                    sessionSummary: sessionSummary,
                    agentDirectoryVersion: agentDirectoryVersion
                )
            } else {
                applyThreadStateUpdated(
                    state,
                    sessionSummary: sessionSummary,
                    agentDirectoryVersion: agentDirectoryVersion
                )
            }
        case .threadItemChanged(let key, let item, let sessionSummary):
            let isBatched = shouldBatchCommandRowMutation(for: key, item: item)
            if isBatched {
                enqueueCommandRowUpsert(key: key, item: item)
            } else if !applyThreadItemUpsert(key: key, item: item) {
                scheduleThreadSnapshotRefresh(for: key)
            }
            // Reducer piggybacks the refreshed per-thread summary on every
            // item change, so the home dashboard's session-summary driven
            // fields (stats, last tool label, etc.) stay in sync with the
            // stream without waiting for a full snapshot rebuild.
            applySessionSummary(sessionSummary)
        case .threadStreamingDelta(let key, let itemId, let kind, let text):
            switch kind {
            case .assistantText:
                if !applyThreadStreamingDelta(key: key, itemId: itemId, kind: kind, text: text) {
                    scheduleThreadSnapshotRefresh(for: key)
                }
                StreamingRendererCoordinator.shared.appendDelta(text, for: itemId)
            default:
                if !applyThreadStreamingDelta(key: key, itemId: itemId, kind: kind, text: text) {
                    scheduleThreadSnapshotRefresh(for: key)
                }
            }
        case .threadRemoved(let key, let agentDirectoryVersion):
            removeThreadSnapshot(for: key, agentDirectoryVersion: agentDirectoryVersion)
        case .activeThreadChanged(let key):
            updateActiveThread(key)
            if let key, threadSnapshot(for: key) == nil {
                await refreshThreadSnapshot(key: key)
            }
            scheduleDeferredActiveThreadHydrationIfNeeded(for: key)
        case .pendingApprovalsChanged:
            await refreshSnapshot()
        case .pendingUserInputsChanged:
            await refreshSnapshot()
        case .serverChanged:
            scheduleSnapshotRefreshDebounced()
        case .serverRemoved:
            await refreshSnapshot()
        case .fullResync:
            await refreshSnapshot()
        case .voiceSessionChanged:
            await refreshSnapshot()
        case .realtimeTranscriptUpdated:
            break
        case .realtimeHandoffRequested:
            break
        case .realtimeSpeechStarted:
            break
        case .realtimeStarted:
            await refreshSnapshot()
        case .realtimeSdp:
            break
        case .realtimeOutputAudioDelta:
            break
        case .realtimeError:
            await refreshSnapshot()
        case .realtimeClosed:
            await refreshSnapshot()
        case .savedAppsChanged:
            SavedAppsStore.shared.reload()
        case .dynamicWidgetStreaming(let key, let itemId, _, let widget):
            applyStreamingWidget(key: key, itemId: itemId, widget: widget)
        case .terminalSessionsChanged:
            await refreshSnapshot()
        }
    }

    func refreshThreadSnapshot(key: ThreadKey) async {
        guard snapshot != nil else {
            await refreshSnapshot()
            return
        }

        do {
            guard let threadSnapshot = try await store.threadSnapshot(key: key) else {
                if cachedThreadSnapshots[key] == nil {
                    removeThreadSnapshot(for: key, clearCache: false)
                }
                return
            }
            applyThreadSnapshot(threadSnapshot)
        } catch {
            lastError = error.localizedDescription
            await refreshSnapshot()
        }
    }

    private func scheduleThreadSnapshotRefresh(for key: ThreadKey) {
        pendingThreadRefreshKeys.insert(key)
        guard pendingThreadRefreshTask == nil else { return }
        pendingThreadRefreshTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 16_000_000)
            guard let self else { return }
            let keys = self.pendingThreadRefreshKeys
            self.pendingThreadRefreshKeys.removeAll()
            self.pendingThreadRefreshTask = nil
            for key in keys {
                await self.refreshThreadSnapshot(key: key)
            }
        }
    }
}
