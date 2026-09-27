import Foundation
import Observation
import UIKit
import UserNotifications

extension AppLifecycleController {
    /// While the app still runs in background (the background task after
    /// `appDidEnterBackground`), post the local completion fallback as soon as
    /// the tracked turns end. There are no background push wakes anymore: once
    /// iOS suspends the app, only host-reported pushes can notify (spec §8.2).
    func observeBackgroundTurnCompletion(
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
