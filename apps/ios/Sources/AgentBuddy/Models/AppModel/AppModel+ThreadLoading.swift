import Foundation

extension AppModel {
    func scheduleDeferredActiveThreadHydrationIfNeeded(for key: ThreadKey?) {
        guard let key else {
            pendingActiveThreadHydrationTask?.cancel()
            pendingActiveThreadHydrationTask = nil
            pendingActiveThreadHydrationKey = nil
            return
        }

        guard let thread = threadSnapshot(for: key),
              shouldAttemptDeferredHydration(for: thread) else {
            if pendingActiveThreadHydrationKey == key {
                pendingActiveThreadHydrationTask?.cancel()
                pendingActiveThreadHydrationTask = nil
                pendingActiveThreadHydrationKey = nil
            }
            return
        }

        guard pendingActiveThreadHydrationKey != key || pendingActiveThreadHydrationTask == nil else {
            return
        }

        pendingActiveThreadHydrationTask?.cancel()
        pendingActiveThreadHydrationKey = key
        pendingActiveThreadHydrationTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard let self else { return }
            await self.hydrateActiveThreadIfNeeded(key: key)
        }
    }

    private func hydrateActiveThreadIfNeeded(key: ThreadKey) async {
        defer {
            if pendingActiveThreadHydrationKey == key {
                pendingActiveThreadHydrationTask = nil
                pendingActiveThreadHydrationKey = nil
            }
        }

        guard snapshot?.activeThread == key,
              let thread = threadSnapshot(for: key),
              shouldAttemptDeferredHydration(for: thread) else {
            return
        }

        do {
            let nextKey = try await client.readThread(
                serverId: key.serverId,
                params: AppReadThreadRequest(
                    threadId: key.threadId,
                    includeTurns: false
                )
            )
            if let threadSnapshot = try await store.threadSnapshot(key: nextKey) {
                applyThreadSnapshot(threadSnapshot)
            } else {
                await refreshThreadSnapshot(key: nextKey)
            }
        } catch {
            lastError = error.localizedDescription
        }
    }

    private func shouldAttemptDeferredHydration(for thread: AppThreadSnapshot) -> Bool {
        guard thread.hydratedConversationItems.isEmpty else { return false }
        let preview = thread.info.preview?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let title = thread.info.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return !preview.isEmpty || !title.isEmpty || thread.hasActiveTurn
    }

    func hydrateThreadPermissions(for key: ThreadKey, appState: AppState) async -> ThreadKey? {
        if let existing = threadSnapshot(for: key) {
            appState.hydratePermissions(from: existing)
            if !hasAuthoritativePermissions(existing) {
                scheduleBackgroundThreadPermissionHydration(for: key, appState: appState)
            }
            return key
        }

        if snapshot?.sessionSummary(for: key) != nil {
            scheduleBackgroundThreadPermissionHydration(for: key, appState: appState)
            return key
        }

        do {
            let nextKey = try await client.readThread(
                serverId: key.serverId,
                params: AppReadThreadRequest(
                    threadId: key.threadId,
                    includeTurns: false
                )
            )
            if let threadSnapshot = try await store.threadSnapshot(key: nextKey) {
                applyThreadSnapshot(threadSnapshot)
                appState.hydratePermissions(from: threadSnapshot)
            } else {
                await refreshSnapshot()
                appState.hydratePermissions(from: snapshot?.threadSnapshot(for: nextKey))
            }
            return nextKey
        } catch {
            lastError = error.localizedDescription
            return nil
        }
    }

    private func scheduleBackgroundThreadPermissionHydration(
        for key: ThreadKey,
        appState: AppState
    ) {
        Task { [weak self] in
            guard let self else { return }
            do {
                let nextKey = try await client.readThread(
                    serverId: key.serverId,
                    params: AppReadThreadRequest(
                        threadId: key.threadId,
                        includeTurns: false
                    )
                )
                if let threadSnapshot = try await store.threadSnapshot(key: nextKey) {
                    applyThreadSnapshot(threadSnapshot)
                    appState.hydratePermissions(from: threadSnapshot)
                } else {
                    await refreshSnapshot()
                    appState.hydratePermissions(from: snapshot?.threadSnapshot(for: nextKey))
                }
            } catch {
                lastError = error.localizedDescription
            }
        }
    }

    func ensureThreadLoaded(
        key: ThreadKey,
        maxAttempts: Int = 5
    ) async -> ThreadKey? {
        if threadSnapshot(for: key) != nil {
            return key
        }

        var currentKey = key
        for attempt in 0..<maxAttempts {
            var readSucceeded = false
            do {
                try await store.externalResumeThread(key: currentKey, hostId: nil)
                store.setActiveThread(key: currentKey)
                readSucceeded = true
            } catch {
                lastError = error.localizedDescription
            }

            if readSucceeded {
                await refreshLoadedThreadSnapshot(key: currentKey)
                if threadSnapshot(for: currentKey) != nil {
                    return currentKey
                }
            }

            if !readSucceeded {
                do {
                    _ = try await client.listThreads(
                        serverId: currentKey.serverId,
                        params: AppListThreadsRequest(
                            cursor: nil,
                            limit: 80,
                            sortKey: .updatedAt,
                            sortDirection: .desc,
                            archived: nil,
                            cwd: nil,
                            searchTerm: nil,
                            useStateDbOnly: false,
                            runtimeKinds: nil
                        )
                    )
                } catch {
                    lastError = error.localizedDescription
                }

                await refreshLoadedThreadSnapshot(key: currentKey)
                if threadSnapshot(for: currentKey) != nil {
                    return currentKey
                }
            }

            if attempt + 1 < maxAttempts {
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
        }

        if let activeKey = snapshot?.activeThread,
           activeKey.serverId == currentKey.serverId,
           threadSnapshot(for: activeKey) != nil {
            return activeKey
        }

        return nil
    }

    private static let initialTurnPageSize: UInt32 = 5
    private static let olderTurnPageSize: UInt32 = 5

    /// Fetch the first page of turns for a thread whose `initialTurnsLoaded`
    /// is still false. Called after a resume that sent `exclude_turns: true`
    /// against a v0.125+ server.
    func loadInitialTurns(threadId key: ThreadKey) async {
        await loadTurnPage(key: key, cursor: nil, limit: Self.initialTurnPageSize)
    }

    func loadInitialTurnsIfNeeded(threadId key: ThreadKey) async {
        guard threadSnapshot(for: key)?.initialTurnsLoaded != true else {
            return
        }
        await loadInitialTurns(threadId: key)
    }

    /// Fetch the next older page of turns using the thread's current cursor.
    /// No-op when no cursor is available (older-turns button should be hidden
    /// in that case).
    func loadOlderTurns(threadId key: ThreadKey) async {
        guard let cursor = threadSnapshot(for: key)?.olderTurnsCursor,
              !cursor.isEmpty else {
            return
        }
        await loadTurnPage(key: key, cursor: cursor, limit: Self.olderTurnPageSize)
    }

    private func loadTurnPage(key: ThreadKey, cursor: String?, limit: UInt32) async {
        if loadingTurnPageThreadKeys.contains(key) { return }
        loadingTurnPageThreadKeys.insert(key)
        defer { loadingTurnPageThreadKeys.remove(key) }

        do {
            _ = try await store.loadThreadTurnsPage(
                key: key,
                cursor: cursor,
                limit: limit
            )
        } catch {
            lastError = error.localizedDescription
        }
    }

    private func refreshLoadedThreadSnapshot(key: ThreadKey) async {
        do {
            if let thread = try await store.threadSnapshot(key: key) {
                applyThreadSnapshot(thread)
            } else {
                await refreshSnapshot()
            }
        } catch {
            lastError = error.localizedDescription
            await refreshSnapshot()
        }
    }

    private func hasAuthoritativePermissions(_ thread: AppThreadSnapshot) -> Bool {
        threadPermissionsAreAuthoritative(
            approvalPolicy: thread.effectiveApprovalPolicy,
            sandboxPolicy: thread.effectiveSandboxPolicy
        )
    }
}
