import Foundation
import Observation
import UIKit
import UserNotifications
import os

private let appLifecycleSignpostLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "com.akashark.agentbuddy",
    category: "lifecycle"
)

@MainActor
final class AppLifecycleController {
    struct BackgroundTurnReconciliation {
        let remainingKeys: Set<ThreadKey>
        let activeThreads: [AppThreadSnapshot]
        let completedNotificationThread: AppThreadSnapshot?
    }

    private var devicePushToken: Data?
    private var backgroundedTurnKeys: Set<ThreadKey> = []
    /// Active turn id of each tracked thread when the app was backgrounded,
    /// so the local completion fallback can ask Rust about that exact turn.
    private var backgroundedTurnIds: [ThreadKey: String] = [:]
    /// Current background completion observation; stale observers compare
    /// against it and stop re-arming.
    private var backgroundObservationID: UUID?
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid
    private var notificationPermissionRequested = false
    private var hasRecoveredCurrentForegroundSession = false
    private var hasEnteredBackgroundSinceLaunch = false
    private var foregroundRecoveryTask: Task<Void, Never>?
    private var foregroundRecoveryID: UUID?
    private var notificationActivatedThreadKey: ThreadKey?
    private var notificationActivatedAt: Date?
    /// Wall-clock timestamp of the most recent `appDidEnterBackground`.
    /// Used to decide whether the existing alleycat `Connection` is
    /// almost certainly dead by the time we resume — see
    /// `LONG_RESUME_THRESHOLD` and `on_long_resume`.
    private var lastBackgroundedAt: Date?

    /// Threshold for triggering proactive `Connection::close()` on
    /// resume. Tied to iroh's per-path idle timeout (default 15s): if
    /// we were suspended for longer than that, the existing path is
    /// almost certainly dead and waiting on iroh's connection-level
    /// 30s idle timer would make the next user request hang for the
    /// remainder of that window.
    private static let longResumeThreshold: TimeInterval = 15

    func setDevicePushToken(_ token: Data, client: AppClient?) {
        devicePushToken = token
        guard let client else { return }
        refreshPushRegistration(client: client)
    }

    /// Hand Rust the current push registration: the APNs token while the user
    /// allows notifications, otherwise `nil` so Rust revokes this device's
    /// host subscriptions. Re-run whenever the token or permission may change.
    func refreshPushRegistration(client: AppClient) {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            let status = settings.authorizationStatus
            Task { @MainActor [weak self] in
                self?.applyPushRegistration(authorizationStatus: status, client: client)
            }
        }
    }

    private func applyPushRegistration(authorizationStatus: UNAuthorizationStatus, client: AppClient) {
        let environment = PushNotificationSupport.currentApnsEnvironment()
        let registration = PushNotificationSupport.registration(
            token: devicePushToken,
            authorizationStatus: authorizationStatus,
            environment: environment
        )
        LLog.info(
            "push",
            "updating push registration",
            fields: [
                "registered": registration != nil,
                "hasToken": devicePushToken != nil,
                "authorizationStatus": authorizationStatus.rawValue,
                "apnsEnvironment": String(describing: environment),
                "tokenSha256Prefix": devicePushToken.map(PushNotificationSupport.tokenFingerprint) ?? ""
            ]
        )
        client.setPushRegistration(registration: registration)
    }

    func reconnectSavedServers(appModel: AppModel) async {
        let servers = SavedServerStore.reconnectRecords(
            localDisplayName: appModel.resolvedLocalServerDisplayName(),
            rememberedOnly: true
        )
        appModel.reconnectController.setMultiClankerAndQuicEnabled(enabled: true)
        appModel.reconnectController.syncSavedServers(servers: servers)
        // Hint iroh-backed sessions about a possible network change before
        // running reconnect — healthy alleycat sessions can recover via
        // path migration without paying the full reconnect handshake.
        await appModel.reconnectController.notifyNetworkChange()
        let results = await appModel.reconnectController.reconnectSavedServers()
        await appModel.refreshSnapshot()
        for result in results where result.needsLocalAuthRestore {
            await appModel.restoreStoredLocalAuthState(serverId: result.serverId)
        }
        await appModel.restoreMissingLocalAuthStateIfNeeded()
        await appModel.refreshSnapshot()
        // If reconnecting saved alleycat servers triggered the iroh
        // endpoint bind, the Rust side may have generated a fresh
        // device secret key. Persist it so the next cold launch reuses
        // the same `EndpointId`.
        AppRuntimeController.shared.persistAlleycatSecretKeyIfNeeded()
    }

    func markThreadOpenedFromNotification(_ key: ThreadKey) {
        notificationActivatedThreadKey = key
        notificationActivatedAt = Date()
    }

    func reconnectServer(serverId: String, appModel: AppModel) async {
        let servers = SavedServerStore.reconnectRecords(
            localDisplayName: appModel.resolvedLocalServerDisplayName()
        )
        appModel.reconnectController.setMultiClankerAndQuicEnabled(enabled: true)
        appModel.reconnectController.syncSavedServers(servers: servers)
        let result = await appModel.reconnectController.reconnectServer(serverId: serverId)
        await appModel.refreshSnapshot()
        if result.needsLocalAuthRestore {
            await appModel.restoreStoredLocalAuthState(serverId: serverId)
        }
        await appModel.restoreMissingLocalAuthStateIfNeeded()
        await appModel.refreshSnapshot()
    }

    func appDidEnterBackground(
        appModel: AppModel,
        hasActiveVoiceSession: Bool,
        liveActivities: TurnLiveActivityController
    ) {
        let signpostID = OSSignpostID(log: appLifecycleSignpostLog)
        os_signpost(.begin, log: appLifecycleSignpostLog, name: "AppDidEnterBackground", signpostID: signpostID)
        defer { os_signpost(.end, log: appLifecycleSignpostLog, name: "AppDidEnterBackground", signpostID: signpostID) }
        let snapshot = appModel.snapshot
        hasEnteredBackgroundSinceLaunch = true
        hasRecoveredCurrentForegroundSession = false
        lastBackgroundedAt = Date()
        foregroundRecoveryTask?.cancel()
        foregroundRecoveryTask = nil
        foregroundRecoveryID = nil
        LLog.info(
            "lifecycle",
            "app did enter background",
            fields: [
                "hasActiveVoiceSession": hasActiveVoiceSession,
                "existingTrackedTurnCount": snapshot?.threadsWithTrackedTurns.count ?? 0
            ]
        )
        guard !hasActiveVoiceSession else { return }
        let activeThreads = snapshot?.threadsWithTrackedTurns ?? []
        guard !activeThreads.isEmpty else { return }

        backgroundedTurnKeys = Set(activeThreads.map(\.key))
        backgroundedTurnIds = Dictionary(
            activeThreads.compactMap { thread in thread.activeTurnId.map { (thread.key, $0) } },
            uniquingKeysWith: { first, _ in first }
        )
        LLog.info(
            "lifecycle",
            "tracking background turn keys",
            fields: [
                "trackedKeys": activeThreads.map(\.key.debugLabel)
            ]
        )
        liveActivities.markBackgrounded(snapshot)

        let bgID = UIApplication.shared.beginBackgroundTask { [weak self] in
            guard let self else { return }
            let expiredID = self.backgroundTaskID
            self.backgroundTaskID = .invalid
            UIApplication.shared.endBackgroundTask(expiredID)
        }
        backgroundTaskID = bgID

        let observationID = UUID()
        backgroundObservationID = observationID
        observeBackgroundTurnCompletion(
            appModel: appModel,
            liveActivities: liveActivities,
            observationID: observationID
        )
    }

    func appDidBecomeActive(
        appModel: AppModel,
        hasActiveVoiceSession: Bool,
        liveActivities: TurnLiveActivityController
    ) {
        let signpostID = OSSignpostID(log: appLifecycleSignpostLog)
        os_signpost(.begin, log: appLifecycleSignpostLog, name: "AppDidBecomeActive", signpostID: signpostID)
        defer { os_signpost(.end, log: appLifecycleSignpostLog, name: "AppDidBecomeActive", signpostID: signpostID) }
        backgroundObservationID = nil
        endBackgroundTaskIfNeeded()
        // The user may have changed notification permission in Settings.
        refreshPushRegistration(client: appModel.client)
        guard !hasActiveVoiceSession else { return }
        guard !hasRecoveredCurrentForegroundSession else { return }
        hasRecoveredCurrentForegroundSession = true
        let needsInitialReconnect = !hasEnteredBackgroundSinceLaunch
        let currentSnapshot = appModel.snapshot
        let backgroundedKeys = backgroundedTurnKeys
        backgroundedTurnKeys.removeAll()
        backgroundedTurnIds.removeAll()
        let keysToRefresh = foregroundRecoveryKeys(
            snapshot: currentSnapshot,
            backgroundedKeys: backgroundedKeys
        )
        LLog.info(
            "lifecycle",
            "app did become active",
            fields: [
                "needsInitialReconnect": needsInitialReconnect,
                "backgroundedKeyCount": backgroundedKeys.count,
                "refreshKeyCount": keysToRefresh.count,
                "refreshKeys": Array(keysToRefresh).map(\.debugLabel)
            ]
        )

        foregroundRecoveryTask?.cancel()
        let recoveryID = UUID()
        foregroundRecoveryID = recoveryID

        foregroundRecoveryTask = Task { [weak self] in
            guard let self else { return }
            defer {
                if self.foregroundRecoveryID == recoveryID {
                    self.foregroundRecoveryTask = nil
                    self.foregroundRecoveryID = nil
                }
            }

            await self.performForegroundRecovery(
                appModel: appModel,
                liveActivities: liveActivities,
                needsInitialReconnect: needsInitialReconnect,
                keysToRefresh: keysToRefresh
            )
        }
    }

    /// While the app still runs in background (the background task after
    /// `appDidEnterBackground`), post the local completion fallback as soon as
    /// the tracked turns end. There are no background push wakes anymore: once
    /// iOS suspends the app, only host-reported pushes can notify (spec §8.2).
    private func observeBackgroundTurnCompletion(
        appModel: AppModel,
        liveActivities: TurnLiveActivityController,
        observationID: UUID
    ) {
        withObservationTracking {
            _ = appModel.snapshot
        } onChange: { [weak self] in
            Task { @MainActor [weak self] in
                self?.handleBackgroundSnapshotChange(
                    appModel: appModel,
                    liveActivities: liveActivities,
                    observationID: observationID
                )
            }
        }
    }

    private func handleBackgroundSnapshotChange(
        appModel: AppModel,
        liveActivities: TurnLiveActivityController,
        observationID: UUID
    ) {
        guard backgroundObservationID == observationID,
              UIApplication.shared.applicationState == .background,
              !backgroundedTurnKeys.isEmpty,
              let snapshot = appModel.snapshot else {
            return
        }
        let reconciliation = reconcileBackgroundedTurns(
            snapshot: snapshot,
            trackedKeys: backgroundedTurnKeys
        )
        backgroundedTurnKeys = reconciliation.remainingKeys
        if let thread = reconciliation.completedNotificationThread {
            LLog.info(
                "push",
                "tracked background turns finished while app was running",
                fields: ["completedNotificationThread": thread.key.debugLabel]
            )
            liveActivities.endCurrent(phase: .completed, snapshot: snapshot)
            postLocalNotificationIfNeeded(
                thread: thread,
                turnId: backgroundedTurnIds[thread.key],
                client: appModel.client
            )
        }
        guard !backgroundedTurnKeys.isEmpty else {
            backgroundObservationID = nil
            return
        }
        observeBackgroundTurnCompletion(
            appModel: appModel,
            liveActivities: liveActivities,
            observationID: observationID
        )
    }

    func requestNotificationPermissionIfNeeded(client: AppClient) {
        guard !notificationPermissionRequested else { return }
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-test-conversation-display") {
            notificationPermissionRequested = true
            return
        }
        #endif
        notificationPermissionRequested = true
        LLog.info("push", "requesting notification permission")
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { [weak self] _, _ in
            Task { @MainActor [weak self] in
                self?.refreshPushRegistration(client: client)
            }
        }
    }

    func reconcileBackgroundedTurns(
        snapshot: AppSnapshotRecord,
        trackedKeys: Set<ThreadKey>,
        trustedLiveKeys: Set<ThreadKey> = []
    ) -> BackgroundTurnReconciliation {
        var remainingKeys: Set<ThreadKey> = []
        var activeThreads: [AppThreadSnapshot] = []
        var completedThreads: [AppThreadSnapshot] = []

        for key in trackedKeys {
            guard let thread = snapshot.threadSnapshot(for: key) else {
                // Keep tracking until we can observe a definitive thread state again.
                LLog.info(
                    "push",
                    "background turn reconciliation missing thread snapshot",
                    fields: ["key": key.debugLabel]
                )
                remainingKeys.insert(key)
                continue
            }

            let hasActiveTurn = thread.hasActiveTurn
            let hasPendingApproval = snapshot.pendingApprovals.contains(where: {
                $0.serverId == key.serverId && $0.threadId == key.threadId
            })
            let hasPendingUserInput = snapshot.pendingUserInputs.contains(where: {
                $0.isRelevant(to: key)
            })
            let hasRecentLiveUpdate = trustedLiveKeys.contains(key)
            let remainsTracked = hasActiveTurn || hasPendingApproval || hasPendingUserInput || hasRecentLiveUpdate
            LLog.info(
                "push",
                "background turn reconciliation evaluated thread",
                fields: [
                    "key": key.debugLabel,
                    "hasActiveTurn": hasActiveTurn,
                    "hasPendingApproval": hasPendingApproval,
                    "hasPendingUserInput": hasPendingUserInput,
                    "hasRecentLiveUpdate": hasRecentLiveUpdate,
                    "remainsTracked": remainsTracked
                ]
            )
            if remainsTracked {
                remainingKeys.insert(key)
                activeThreads.append(thread)
            } else {
                completedThreads.append(thread)
            }
        }

        let completedNotificationThread: AppThreadSnapshot?
        if remainingKeys.isEmpty {
            completedNotificationThread = completedThreads.first(where: {
                $0.info.parentThreadId == nil
            }) ?? completedThreads.first
        } else {
            completedNotificationThread = nil
        }

        return BackgroundTurnReconciliation(
            remainingKeys: remainingKeys,
            activeThreads: activeThreads,
            completedNotificationThread: completedNotificationThread
        )
    }

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

    private func performForegroundRecovery(
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

    private func endBackgroundTaskIfNeeded() {
        guard backgroundTaskID != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskID)
        backgroundTaskID = .invalid
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

    /// Local completion fallback for a turn that ended while the app was still
    /// running in background. Skipped when the host will push its own
    /// notification for the turn.
    private func postLocalNotificationIfNeeded(
        thread: AppThreadSnapshot,
        turnId: String?,
        client: AppClient
    ) {
        let threadKey = thread.key
        guard UIApplication.shared.applicationState != .active else {
            LLog.info(
                "push",
                "skipping local notification because app is active",
                fields: ["threadKey": threadKey.debugLabel]
            )
            return
        }
        let pushState = client.turnPushState(key: threadKey, turnId: turnId)
        guard PushNotificationSupport.shouldPostLocalCompletion(for: pushState) else {
            LLog.info(
                "push",
                "skipping local notification because the host reports this turn",
                fields: ["threadKey": threadKey.debugLabel, "pushState": String(describing: pushState)]
            )
            return
        }
        let model = thread.resolvedModel
        let threadPreview = thread.resolvedPreview
        let content = UNMutableNotificationContent()
        content.title = String(localized: "Turn completed")
        var bodyParts: [String] = []
        if !threadPreview.isEmpty { bodyParts.append(threadPreview) }
        if !model.isEmpty { bodyParts.append(model) }
        content.body = bodyParts.joined(separator: " - ")
        content.sound = .default
        content.threadIdentifier = threadKey.threadId
        content.categoryIdentifier = PushNotificationSupport.completionCategoryIdentifier
        var userInfo: [String: String] = [
            PushNotificationSupport.serverIdKey: threadKey.serverId,
            PushNotificationSupport.threadIdKey: threadKey.threadId
        ]
        if let turnId {
            userInfo[PushNotificationSupport.turnIdKey] = turnId
        }
        content.userInfo = userInfo
        let request = UNNotificationRequest(
            identifier: PushNotificationSupport.localCompletionIdentifier(for: threadKey, turnId: turnId),
            content: content,
            trigger: nil
        )
        LLog.info(
            "push",
            "posting local completion notification",
            fields: [
                "threadKey": threadKey.debugLabel,
                "pushState": String(describing: pushState),
                "model": model,
                "hasPreview": !threadPreview.isEmpty
            ]
        )
        UNUserNotificationCenter.current().add(request)
    }
}

private extension ThreadKey {
    var debugLabel: String {
        "\(serverId):\(threadId)"
    }
}
