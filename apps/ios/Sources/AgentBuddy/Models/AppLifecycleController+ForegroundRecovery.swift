import Foundation
import os

extension AppLifecycleController {
    /// Threshold for triggering proactive `Connection::close()` on
    /// resume. Tied to iroh's per-path idle timeout (default 15s): if
    /// we were suspended for longer than that, the existing path is
    /// almost certainly dead and waiting on iroh's connection-level
    /// 30s idle timer would make the next user request hang for the
    /// remainder of that window.
    private static let longResumeThreshold: TimeInterval = 15

    func foregroundRecoveryKeys(
        snapshot: AppSnapshotRecord?,
        backgroundedKeys: Set<ThreadKey>
    ) -> Set<ThreadKey> {
        var keys = backgroundedKeys
        if let activeKey = snapshot?.activeThread {
            keys.insert(activeKey)
        }
        return keys
    }

    func performForegroundRecovery(
        appModel: AppModel,
        liveActivities: TurnLiveActivityController,
        needsInitialReconnect: Bool,
        keysToRefresh: Set<ThreadKey>
    ) async {
        let signpostID = OSSignpostID(log: appLifecycleSignpostLog)
        os_signpost(.begin, log: appLifecycleSignpostLog, name: "PerformForegroundRecovery", signpostID: signpostID)
        defer { os_signpost(.end, log: appLifecycleSignpostLog, name: "PerformForegroundRecovery", signpostID: signpostID) }
        LLog.info(
            "lifecycle",
            "performForegroundRecovery started",
            fields: [
                "needsInitialReconnect": needsInitialReconnect,
                "refreshKeyCount": keysToRefresh.count,
                "refreshKeys": Array(keysToRefresh).map(\.debugLabel)
            ]
        )
        // Always attempt to reconnect saved servers on foreground return.
        // The ReconnectController skips servers whose health != .disconnected,
        // so this is cheap when everything is still connected.
        let servers = SavedServerStore.reconnectRecords(
            localDisplayName: appModel.resolvedLocalServerDisplayName(),
            rememberedOnly: true
        )
        appModel.reconnectController.setMultiClankerAndQuicEnabled(enabled: true)
        appModel.reconnectController.syncSavedServers(servers: servers)

        // If we were suspended longer than iroh's per-path idle timeout,
        // the existing alleycat Connection is almost certainly dead. Kill
        // it before the user can issue a request — otherwise the worker's
        // first request would wait the full 30s connection-idle timeout
        // for iroh to declare the path dead. Fires BEFORE
        // `onAppBecameActive` so the close lands before the
        // network-change hint and saved-server reconnect.
        let backgroundDuration = lastBackgroundedAt.map { Date().timeIntervalSince($0) }
        if let duration = backgroundDuration, duration > Self.longResumeThreshold {
            LLog.info(
                "lifecycle",
                "long resume — abandoning live alleycat connections",
                fields: ["backgroundDurationSec": Int(duration)]
            )
            await appModel.reconnectController.onLongResume()
        }
        lastBackgroundedAt = nil

        let results = await appModel.reconnectController.onAppBecameActive()
        await appModel.refreshSnapshot()
        for result in results where result.needsLocalAuthRestore {
            await appModel.restoreStoredLocalAuthState(serverId: result.serverId)
        }
        await appModel.restoreMissingLocalAuthStateIfNeeded()

        if needsInitialReconnect {
            let retryResults = await appModel.reconnectController.reconnectSavedServers()
            for result in retryResults where result.needsLocalAuthRestore {
                await appModel.restoreStoredLocalAuthState(serverId: result.serverId)
            }
            await appModel.refreshSnapshot()
        }
        guard !Task.isCancelled else { return }

        let trustedLiveKeys = Set(keysToRefresh.filter {
            shouldTrustLiveThreadState(for: $0, appModel: appModel, within: 4)
        })
        let notificationActivationAge = notificationActivatedAt.map { Date().timeIntervalSince($0) }
        let reloadKeys = foregroundRecoveryKeysNeedingReload(
            keysToRefresh,
            activeThread: appModel.snapshot?.activeThread,
            trustedLiveKeys: trustedLiveKeys,
            notificationActivatedKey: notificationActivatedThreadKey,
            notificationActivationAge: notificationActivationAge
        )
        if !reloadKeys.isEmpty {
            // Force authoritative refresh: a turn that completed during a
            // long iOS suspension fired `TurnCompleted` while no client
            // connection was attached, so the local snapshot still shows
            // the turn as in-progress. The force-authoritative resume
            // either pulls back a turn-status list — embedded on legacy
            // remotes, via a tiny `thread/turns/list?items_view=notLoaded`
            // probe on paginated remotes — and feeds it to
            // `reconcile_active_turn`, which clears the stale
            // `active_turn_id` so the "thinking" spinner doesn't hang.
            await refreshTrackedThreads(
                appModel: appModel,
                keys: Array(reloadKeys),
                forceAuthoritative: true
            )
            guard !Task.isCancelled else { return }
        } else if !keysToRefresh.isEmpty {
            LLog.info(
                "lifecycle",
                "performForegroundRecovery skipped thread reloads because live state is already current",
                fields: ["refreshKeys": Array(keysToRefresh).map(\.debugLabel)]
            )
        }

        liveActivities.sync(appModel.snapshot)
        // Capture any freshly-generated alleycat device secret key from
        // this foreground's reconnect cycle.
        AppRuntimeController.shared.persistAlleycatSecretKeyIfNeeded()
        LLog.info("lifecycle", "performForegroundRecovery completed")
    }

    private func refreshTrackedThreads(
        appModel: AppModel,
        keys: [ThreadKey],
        forceAuthoritative: Bool = false
    ) async {
        guard !keys.isEmpty else { return }
        let signpostID = OSSignpostID(log: appLifecycleSignpostLog)
        os_signpost(.begin, log: appLifecycleSignpostLog, name: "RefreshTrackedThreads", signpostID: signpostID)
        defer { os_signpost(.end, log: appLifecycleSignpostLog, name: "RefreshTrackedThreads", signpostID: signpostID) }
        LLog.info(
            "lifecycle",
            "refreshTrackedThreads started",
            fields: [
                "keys": keys.map(\.debugLabel),
                "forceAuthoritative": forceAuthoritative
            ]
        )

        let activeKey = appModel.snapshot?.activeThread
        var orderedKeys: [ThreadKey] = []
        if let activeKey, keys.contains(activeKey) {
            orderedKeys.append(activeKey)
        }
        orderedKeys.append(contentsOf: keys.filter { key in
            guard let activeKey else { return true }
            return key != activeKey
        })

        if let firstKey = orderedKeys.first {
            await reloadTrackedThread(
                appModel: appModel,
                key: firstKey,
                forceAuthoritative: forceAuthoritative
            )
        }

        let remainingKeys = Array(orderedKeys.dropFirst())
        for key in remainingKeys {
            await reloadTrackedThread(
                appModel: appModel,
                key: key,
                forceAuthoritative: forceAuthoritative
            )
        }
        LLog.info("lifecycle", "refreshTrackedThreads completed", fields: ["keyCount": keys.count])
    }

    private func reloadTrackedThread(
        appModel: AppModel,
        key: ThreadKey,
        forceAuthoritative: Bool = false
    ) async {
        // After a long resume / push wake the locally-cached snapshot may
        // have missed `TurnCompleted` events — the in-flight turn fired
        // those while the app was frozen, no client connection was
        // attached to the per-thread subscription set, and the events
        // were dropped. The regular `reloadThread` short-circuits via
        // the direct-resume marker, so we'd keep showing the stale
        // active turn until the user manually refreshes. Force-
        // authoritative bypasses both short-circuits and feeds
        // `reconcile_active_turn` from a turn-status list (embedded on
        // legacy remotes; via a small `thread/turns/list` probe on
        // paginated remotes — see `forceRefreshThreadAuthoritative`).
        if forceAuthoritative {
            LLog.info(
                "lifecycle",
                "reloadTrackedThread started (authoritative)",
                fields: ["key": key.debugLabel]
            )
            do {
                try await appModel.forceRefreshThreadAuthoritative(key: key)
                await appModel.refreshThreadSnapshot(key: key)
                LLog.info(
                    "lifecycle",
                    "reloadTrackedThread completed (authoritative)",
                    fields: ["key": key.debugLabel]
                )
            } catch {
                LLog.error(
                    "lifecycle",
                    "reloadTrackedThread (authoritative) failed",
                    error: error,
                    fields: ["key": key.debugLabel]
                )
            }
            return
        }

        let snapshot = appModel.snapshot
        let existing = snapshot?.threadSnapshot(for: key)
        let cwd = existing?.info.cwd?.trimmingCharacters(in: .whitespacesAndNewlines)
        let config = AppThreadLaunchConfig(
            model: existing?.resolvedModel,
            approvalPolicy: nil,
            sandbox: nil,
            developerInstructions: nil,
            persistExtendedHistory: true
        )
        LLog.info(
            "lifecycle",
            "reloadTrackedThread started",
            fields: [
                "key": key.debugLabel,
                "cwdOverride": cwd?.isEmpty == false ? (cwd ?? "") : ""
            ]
        )
        do {
            let resolvedKey = try await appModel.reloadThread(
                key: key,
                launchConfig: config,
                cwdOverride: cwd?.isEmpty == false ? cwd : nil
            )
            await appModel.refreshThreadSnapshot(key: resolvedKey)
            LLog.info("lifecycle", "reloadTrackedThread completed", fields: ["key": key.debugLabel])
        } catch {
            LLog.error(
                "lifecycle",
                "reloadTrackedThread failed",
                error: error,
                fields: ["key": key.debugLabel]
            )
        }
    }

    private func shouldTrustLiveThreadState(
        for key: ThreadKey,
        appModel: AppModel,
        within interval: TimeInterval = 3
    ) -> Bool {
        // Always refresh tracked threads after lifecycle transitions; Rust
        // will no-op when the state is already fresh.
        false
    }

    func foregroundRecoveryKeysNeedingReload(
        _ keys: Set<ThreadKey>,
        activeThread: ThreadKey?,
        trustedLiveKeys: Set<ThreadKey>,
        notificationActivatedKey: ThreadKey?,
        notificationActivationAge: TimeInterval?
    ) -> Set<ThreadKey> {
        keys.filter { key in
            guard trustedLiveKeys.contains(key) else { return true }
            if activeThread == key {
                return false
            }
            guard notificationActivatedKey == key,
                  let notificationActivationAge else {
                return true
            }
            return notificationActivationAge > 6
        }
    }
}
