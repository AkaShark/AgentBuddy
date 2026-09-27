import UIKit
import UserNotifications

extension AppDelegate {
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        LLog.info(
            "push",
            "device token received",
            fields: [
                "bytes": deviceToken.count,
                "sha256Prefix": PushNotificationSupport.tokenFingerprint(deviceToken)
            ]
        )
        if let appRuntime {
            appRuntime.setDevicePushToken(deviceToken)
        } else {
            pendingPushToken = deviceToken
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        LLog.error("push", "registration failed", error: error)
    }

    /// Host-reported completion pushes are visible alerts; there is no silent
    /// keep-alive push to act on anymore.
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        LLog.info(
            "push",
            "remote notification received; no background work",
            fields: ["applicationState": application.applicationState.debugName]
        )
        completionHandler(.noData)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let key = PushNotificationSupport.threadKey(from: notification.request.content.userInfo)
        let options = PushNotificationSupport.presentationOptions(
            isAppInForeground: UIApplication.shared.applicationState != .background
        )
        LLog.info(
            "push",
            "presenting foreground notification",
            fields: [
                "serverId": key?.serverId ?? "",
                "threadId": key?.threadId ?? "",
                "suppressed": options.isEmpty
            ]
        )
        completionHandler(options)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        LLog.info(
            "push",
            "user opened notification",
            payloadJson: notificationPayloadJson(response.notification.request.content.userInfo)
        )

        let info = response.notification.request.content.userInfo
        let actionId = response.actionIdentifier
        if actionId == WatchApprovalNotification.allowActionIdentifier ||
            actionId == WatchApprovalNotification.denyActionIdentifier,
            let requestId = info[WatchApprovalNotification.requestIdKey] as? String {
            let approve = actionId == WatchApprovalNotification.allowActionIdentifier
            Task { @MainActor in
                do {
                    try await AppModel.shared.store.respondToApproval(
                        requestId: requestId,
                        decision: approve ? .accept : .decline
                    )
                } catch {
                    LLog.error(
                        "push",
                        "approval action dispatch failed: \(error.localizedDescription)"
                    )
                }
                completionHandler()
            }
            return
        }

        if let key = PushNotificationSupport.threadKey(from: info) {
            openThreadFromNotification(key)
        }
        completionHandler()
    }

    func openThreadFromNotification(_ key: ThreadKey) {
        LLog.info(
            "push",
            "open thread from notification",
            fields: ["serverId": key.serverId, "threadId": key.threadId]
        )
        if appRuntime == nil {
            pendingNotificationThreadKey = key
            return
        }

        Task { @MainActor [weak self] in
            guard let self, let appRuntime = self.appRuntime else { return }
            await appRuntime.openThreadFromNotification(key: key)
        }
    }

    private func notificationPayloadJson(_ userInfo: [AnyHashable: Any]) -> String? {
        guard !userInfo.isEmpty else { return nil }
        let payload = Dictionary(uniqueKeysWithValues: userInfo.map { key, value in
            (String(describing: key), String(describing: value))
        })
        guard let data = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]),
              let json = String(data: data, encoding: .utf8)
        else {
            return nil
        }
        return json
    }
}

private extension UIApplication.State {
    var debugName: String {
        switch self {
        case .active:
            return "active"
        case .inactive:
            return "inactive"
        case .background:
            return "background"
        @unknown default:
            return "unknown"
        }
    }
}
