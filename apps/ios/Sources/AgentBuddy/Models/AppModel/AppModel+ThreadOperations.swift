import Foundation

extension AppModel {
    func activateThread(_ key: ThreadKey?) {
        restoreCachedThreadSnapshotIfNeeded(for: key)
        updateActiveThread(key)
        store.setActiveThread(key: key)
        scheduleDeferredActiveThreadHydrationIfNeeded(for: key)
    }

    func resumeThread(
        key: ThreadKey,
        launchConfig: AppThreadLaunchConfig,
        cwdOverride: String?
    ) async throws -> ThreadKey {
        await restoreStoredLocalAuthIfNeeded(serverId: key.serverId, reason: "resumeThread")

        let trimmedCwdOverride = cwdOverride?.trimmingCharacters(in: .whitespacesAndNewlines)
        let requiresResumeOverrides = requiresResumeOverrides(
            for: key,
            launchConfig: launchConfig,
            cwdOverride: trimmedCwdOverride
        )
        let requiresDistinctCwdOverride = requiresResumeCwdOverride(
            for: key,
            cwdOverride: trimmedCwdOverride
        )

        if requiresResumeOverrides {
            return try await client.resumeThread(
                serverId: key.serverId,
                params: launchConfig.threadResumeRequest(
                    threadId: key.threadId,
                    cwdOverride: requiresDistinctCwdOverride ? trimmedCwdOverride : nil
                )
            )
        }

        // No overrides: let Rust do a normal external resume/read path.
        try await store.externalResumeThread(key: key, hostId: nil)
        return key
    }

    func reloadThread(
        key: ThreadKey,
        launchConfig: AppThreadLaunchConfig,
        cwdOverride: String?
    ) async throws -> ThreadKey {
        await restoreStoredLocalAuthIfNeeded(serverId: key.serverId, reason: "reloadThread")

        let trimmedCwdOverride = cwdOverride?.trimmingCharacters(in: .whitespacesAndNewlines)
        let requiresResumeOverrides = requiresResumeOverrides(
            for: key,
            launchConfig: launchConfig,
            cwdOverride: trimmedCwdOverride
        )
        let requiresDistinctCwdOverride = requiresResumeCwdOverride(
            for: key,
            cwdOverride: trimmedCwdOverride
        )

        if requiresResumeOverrides {
            return try await client.resumeThread(
                serverId: key.serverId,
                params: launchConfig.threadResumeRequest(
                    threadId: key.threadId,
                    cwdOverride: requiresDistinctCwdOverride ? trimmedCwdOverride : nil
                )
            )
        }

        // No overrides: let Rust do a normal external resume/read path.
        try await store.externalResumeThread(key: key, hostId: nil)
        return key
    }

    /// Force a fresh resume so the store reconciles `active_turn_id`
    /// against the server's authoritative view. Use after a long resume /
    /// push wake — the in-flight turn the local snapshot shows as running
    /// may have completed during the background window with no
    /// `TurnCompleted` event delivered.
    ///
    /// On v0.125+ remotes this runs `thread/resume` with
    /// `excludeTurns: true` and then a tiny `thread/turns/list` probe
    /// (`limit: 5`, `itemsView: notLoaded`) — turn skeletons only — to feed
    /// `reconcile_active_turn`. Pulling the full embedded turn list here
    /// would OOM on long threads. Legacy remotes that don't implement
    /// `thread/turns/list` still get the embedded turn list via
    /// `excludeTurns: false`, since there is no other way to learn turn
    /// status there.
    func forceRefreshThreadAuthoritative(key: ThreadKey) async throws {
        try await store.forceRefreshThreadAuthoritative(key: key)
    }

    func refreshThreadIncludingTurns(key: ThreadKey) async throws -> ThreadKey {
        do {
            // 1. Refresh thread metadata only — title, status, model,
            //    active_turn_id, etc. The full historical turn list is
            //    append-only on the server, so we don't need to re-pull it
            //    here. Sending `include_turns: true` would have the server
            //    reconstruct the entire rollout in one response, which is
            //    unbounded and OOMs the device on long threads.
            let nextKey = try await client.readThread(
                serverId: key.serverId,
                params: AppReadThreadRequest(
                    threadId: key.threadId,
                    includeTurns: false
                )
            )
            // 2. Reload the most-recent N turns via the paginated path. On
            //    v0.125+ remotes this hits `thread/turns/list` (bounded by
            //    `initialTurnPageSize`). On older remotes that don't
            //    implement `thread/turns/list`, the Rust client transparently
            //    falls back to `thread/resume(excludeTurns: false)` which
            //    pulls the embedded turn list — preserving the prior
            //    reload-button behavior for legacy servers.
            await loadInitialTurns(threadId: nextKey)
            if let threadSnapshot = try await store.threadSnapshot(key: nextKey) {
                applyThreadSnapshot(threadSnapshot)
            } else {
                await refreshThreadSnapshot(key: nextKey)
            }
            return nextKey
        } catch {
            lastError = error.localizedDescription
            throw error
        }
    }

    private func requiresResumeCwdOverride(
        for key: ThreadKey,
        cwdOverride: String?
    ) -> Bool {
        guard let normalizedOverride = cwdOverride, !normalizedOverride.isEmpty else {
            return false
        }

        let existingCwd =
            threadSnapshot(for: key)?.info.cwd?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? snapshot?.sessionSummary(for: key)?.cwd.trimmingCharacters(in: .whitespacesAndNewlines)

        guard let existingCwd, !existingCwd.isEmpty else {
            return true
        }
        return existingCwd != normalizedOverride
    }

    private func requiresResumeOverrides(
        for key: ThreadKey,
        launchConfig: AppThreadLaunchConfig,
        cwdOverride: String?
    ) -> Bool {
        if !(launchConfig.developerInstructions?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true) {
            return true
        }
        if !launchConfig.persistExtendedHistory {
            return true
        }
        if requiresResumeCwdOverride(for: key, cwdOverride: cwdOverride) {
            return true
        }

        guard let existingThread = threadSnapshot(for: key) else {
            let existingModel = snapshot?.sessionSummary(for: key)?.model.trimmingCharacters(in: .whitespacesAndNewlines)
            let requestedModel = launchConfig.model?.trimmingCharacters(in: .whitespacesAndNewlines)
            if let requestedModel, !requestedModel.isEmpty, requestedModel != existingModel {
                return true
            }
            return launchConfig.approvalPolicy != nil
                || launchConfig.sandbox != nil
        }

        let requestedModel = launchConfig.model?.trimmingCharacters(in: .whitespacesAndNewlines)
        let existingModel = (existingThread.model ?? existingThread.info.model)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if let requestedModel, !requestedModel.isEmpty, requestedModel != existingModel {
            return true
        }
        if let requestedApproval = launchConfig.approvalPolicy,
           requestedApproval != existingThread.effectiveApprovalPolicy {
            return true
        }
        if let requestedSandbox = launchConfig.sandbox,
           requestedSandbox != existingThread.effectiveSandboxPolicy?.launchOverrideMode {
            return true
        }
        return false
    }

    func renameThread(serverId: String, threadId: String, title rawTitle: String) async throws {
        let title = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }
        let key = ThreadKey(serverId: serverId, threadId: threadId)

        _ = try await client.renameThread(
            serverId: serverId,
            params: AppRenameThreadRequest(threadId: threadId, name: title)
        )
        applyLocalThreadTitle(title, for: key)
        await refreshSnapshot()
        applyLocalThreadTitle(title, for: key)
    }

    private func applyLocalThreadTitle(_ title: String, for key: ThreadKey) {
        guard var snapshot else { return }
        guard snapshot.applyLocalThreadTitle(title, for: key) else { return }
        self.snapshot = snapshot
        if let thread = snapshot.threadSnapshot(for: key) {
            cacheThreadSnapshot(thread)
        }
        lastError = nil
    }

    func startTurn(key: ThreadKey, payload: AppComposerPayload) async throws {
        await restoreStoredLocalAuthIfNeeded(serverId: key.serverId, reason: "startTurn")

        do {
            try await store.startTurn(
                key: key,
                params: payload.turnStartRequest(threadId: key.threadId)
            )
        } catch {
            lastError = error.localizedDescription
            throw error
        }
    }
}
