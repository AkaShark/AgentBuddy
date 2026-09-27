import Foundation
import UserNotifications

extension AppLifecycleController {
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
}
