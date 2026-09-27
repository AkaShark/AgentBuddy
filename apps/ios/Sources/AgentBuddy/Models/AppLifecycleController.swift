import Foundation
import Observation
import UIKit
import UserNotifications
import os

let appLifecycleSignpostLog = OSLog(
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

    var devicePushToken: Data?
    var backgroundedTurnKeys: Set<ThreadKey> = []
    /// Active turn id of each tracked thread when the app was backgrounded,
    /// so the local completion fallback can ask Rust about that exact turn.
    var backgroundedTurnIds: [ThreadKey: String] = [:]
    /// Current background completion observation; stale observers compare
    /// against it and stop re-arming.
    var backgroundObservationID: UUID?
    private var backgroundTaskID: UIBackgroundTaskIdentifier = .invalid
    var notificationPermissionRequested = false
    private var hasRecoveredCurrentForegroundSession = false
    private var hasEnteredBackgroundSinceLaunch = false
    private var foregroundRecoveryTask: Task<Void, Never>?
    private var foregroundRecoveryID: UUID?
    var notificationActivatedThreadKey: ThreadKey?
    var notificationActivatedAt: Date?
    /// Wall-clock timestamp of the most recent `appDidEnterBackground`.
    /// Used to decide whether the existing alleycat `Connection` is
    /// almost certainly dead by the time we resume — see
    /// `LONG_RESUME_THRESHOLD` and `on_long_resume`.
    var lastBackgroundedAt: Date?

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

    private func endBackgroundTaskIfNeeded() {
        guard backgroundTaskID != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskID)
        backgroundTaskID = .invalid
    }
}

extension ThreadKey {
    var debugLabel: String {
        "\(serverId):\(threadId)"
    }
}
