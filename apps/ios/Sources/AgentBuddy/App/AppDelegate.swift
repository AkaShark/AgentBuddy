import UIKit
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    var pendingPushToken: Data?
    var pendingNotificationThreadKey: ThreadKey?
    var splashWindow: UIWindow?
    var minTimeElapsed = false
    var contentReady = false
    var splashDismissed = false

    weak var appRuntime: AppRuntimeController? {
        didSet {
            if let token = pendingPushToken {
                LLog.info("push", "delivering pending device token to runtime")
                appRuntime?.setDevicePushToken(token)
                pendingPushToken = nil
            }
            if let key = pendingNotificationThreadKey {
                LLog.info(
                    "push",
                    "delivering pending notification thread open to runtime",
                    fields: ["serverId": key.serverId, "threadId": key.threadId]
                )
                pendingNotificationThreadKey = nil
                openThreadFromNotification(key)
            }
        }
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        OpenAIApiKeyStore.shared.applyToEnvironment()
        AgentBuddyPlatform.bootstrapLocalRuntimeIfNeeded()
        LLog.bootstrap()


        NotificationCenter.default.addObserver(
            forName: UIApplication.protectedDataDidBecomeAvailableNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            LLog.info("lifecycle", "protected app data became available")
            OpenAIApiKeyStore.shared.applyToEnvironment()
            guard let appRuntime = self?.appRuntime else { return }
            Task { @MainActor in
                await appRuntime.restoreMissingLocalAuthStateIfNeeded()
            }
        }

        LLog.info("lifecycle", "application did finish launching")
        // Pre-initialize Rust bridges (tokio runtime) on a background thread
        // before SwiftUI accesses AppModel.shared, avoiding a priority inversion
        // where the main thread blocks on lower-QoS tokio worker init.
        DispatchQueue.global(qos: .userInitiated).async {
            AppModel.prewarmRustBridges()
        }
        application.registerForRemoteNotifications()
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().setNotificationCategories([
            UNNotificationCategory(
                identifier: PushNotificationSupport.completionCategoryIdentifier,
                actions: [],
                intentIdentifiers: [],
                options: [.allowAnnouncement]
            ),
            UNNotificationCategory(
                identifier: WatchApprovalNotification.categoryIdentifier,
                actions: [
                    UNNotificationAction(
                        identifier: WatchApprovalNotification.allowActionIdentifier,
                        title: "Allow",
                        options: []
                    ),
                    UNNotificationAction(
                        identifier: WatchApprovalNotification.denyActionIdentifier,
                        title: "Deny",
                        options: [.destructive]
                    ),
                ],
                intentIdentifiers: [],
                options: [.customDismissAction]
            ),
        ])
        OrientationResponder.shared.start()
        DispatchQueue.main.async {
            CloudKVSBridge.shared.start()
        }
        showSplashWindow()
        scheduleKeyboardWarmup()
        // Start pushing state to the paired Apple Watch, gated behind the
        // experimental feature flag. Flip the `appleWatch` feature in
        // Settings → Experimental Features to enable. No-op when disabled.
        DispatchQueue.main.async {
            if ExperimentalFeatures.shared.isEnabled(.appleWatch) {
                WatchCompanionBridge.shared.start()
            }
        }
        return true
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // Best-effort graceful shutdown of the iroh endpoint. iOS only
        // may fire this hook on OS-initiated terminations from background — swipe-up-
        // to-kill from app switcher does NOT fire it. Acceptable: the
        // cost of skipping is one "Aborting ungracefully" log on iroh's
        // side and the daemon waiting up to its idle timeout to reap
        // the final zombie.
        LLog.info("lifecycle", "applicationWillTerminate — closing alleycat endpoint")
        let semaphore = DispatchSemaphore(value: 0)
        Task { @MainActor in
            await self.appRuntime?.shutdownAlleycatEndpoint()
            semaphore.signal()
        }
        // applicationWillTerminate gets ~5s before the OS kills us.
        // Block briefly on the close handshake so iroh can flush
        // CONNECTION_CLOSE frames; bail if iroh's drain takes too long.
        _ = semaphore.wait(timeout: .now() + 2.5)
    }
}
