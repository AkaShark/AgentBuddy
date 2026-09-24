import Foundation
import Observation

@MainActor
@Observable
final class AppRuntimeController {
    static let shared = AppRuntimeController()

    @ObservationIgnored private weak var appModel: AppModel?
    @ObservationIgnored private weak var voiceRuntime: VoiceRuntimeController?
    @ObservationIgnored private let lifecycle = AppLifecycleController()
    @ObservationIgnored private let liveActivities = TurnLiveActivityController()
    @ObservationIgnored private let reachability = NetworkReachabilityObserver()
    @ObservationIgnored private var pendingLiveActivitySync = false
    @ObservationIgnored private var lastLiveActivitySyncTime: CFAbsoluteTime = 0

    /// Conversation on top of the navigation stack, maintained by the UI. Used
    /// to keep foreground notifications quiet for the thread being read.
    @ObservationIgnored var visibleConversationKey: ThreadKey?
    /// Thread a notification tap asked the UI to show; the navigation stack
    /// consumes it.
    private(set) var notificationNavigationRequest: ThreadKey?

    /// Cold start: a notification tap can arrive before saved servers reconnect.
    private static let notificationConnectTimeoutMs: UInt64 = 20_000

    func bind(appModel: AppModel, voiceRuntime: VoiceRuntimeController) {
        self.appModel = appModel
        self.voiceRuntime = voiceRuntime
        lifecycle.requestNotificationPermissionIfNeeded(client: appModel.client)
        reachability.bind(appModel: appModel)
        reachability.start()
        loadAndPushAlleycatSecretKey(client: appModel.client)
    }

    /// Load the persisted iroh device secret key from the keychain (if
    /// any) and push it to the Rust client BEFORE any alleycat
    /// operation triggers the endpoint bind. After the first bind, the
    /// Rust side may have generated a fresh key — observe via
    /// `persistAlleycatSecretKeyIfNeeded`. Together these maintain a
    /// stable `EndpointId` across cold launches.
    private func loadAndPushAlleycatSecretKey(client: AppClient) {
        do {
            if let bytes = try AlleycatCredentialStore.shared.loadDeviceSecretKey() {
                client.setAlleycatSecretKey(secretKeyBytes: bytes)
                LLog.info("alleycat", "loaded persisted device secret key from keychain")
            }
        } catch {
            LLog.error("alleycat", "failed to load device secret key", error: error)
        }
    }

    /// After an alleycat operation has triggered the Rust endpoint
    /// bind, read back the actually-used bytes and persist them so the
    /// next cold launch reuses the same `EndpointId`. Idempotent — safe
    /// to call any time; if the bind hasn't happened yet, returns
    /// silently.
    func persistAlleycatSecretKeyIfNeeded() {
        guard let appModel else { return }
        guard let data = appModel.client.alleycatSecretKey() else { return }
        do {
            let existing = try AlleycatCredentialStore.shared.loadDeviceSecretKey()
            if existing == data { return }
            try AlleycatCredentialStore.shared.saveDeviceSecretKey(data)
            LLog.info("alleycat", "persisted device secret key to keychain")
        } catch {
            LLog.error("alleycat", "failed to persist device secret key", error: error)
        }
    }

    /// Best-effort graceful shutdown of the iroh endpoint. Wired from
    /// `applicationWillTerminate` (UIKit) — see comment on that hook
    /// in AgentBuddyApp.swift for reliability caveats. iroh sends a clean
    /// CONNECTION_CLOSE to peers instead of logging "Aborting
    /// ungracefully" when the static MobileClient slot is finally
    /// dropped at process exit.
    func shutdownAlleycatEndpoint() async {
        guard let appModel else { return }
        await appModel.client.shutdownAlleycatEndpoint()
    }

    func setDevicePushToken(_ token: Data) {
        lifecycle.setDevicePushToken(token, client: appModel?.client)
    }

    func reconnectSavedServers() async {
        guard let appModel else { return }
        await lifecycle.reconnectSavedServers(appModel: appModel)
    }

    func reconnectServer(serverId: String) async {
        guard let appModel else { return }
        await lifecycle.reconnectServer(serverId: serverId, appModel: appModel)
    }

    func restoreMissingLocalAuthStateIfNeeded() async {
        guard let appModel else { return }
        await appModel.restoreMissingLocalAuthStateIfNeeded()
    }

    func openThreadFromNotification(key: ThreadKey) async {
        guard let appModel else { return }
        LLog.info(
            "push",
            "runtime opening thread from notification",
            fields: ["serverId": key.serverId, "threadId": key.threadId]
        )
        lifecycle.markThreadOpenedFromNotification(key)
        let connected = await appModel.client.awaitServerConnected(
            serverId: key.serverId,
            timeoutMs: Self.notificationConnectTimeoutMs
        )
        if !connected {
            LLog.warn(
                "push",
                "notification server not connected before timeout; opening thread anyway",
                fields: ["serverId": key.serverId, "threadId": key.threadId]
            )
        }
        appModel.activateThread(key)
        notificationNavigationRequest = key

        guard let resolvedKey = await appModel.ensureThreadLoaded(key: key) else {
            LLog.warn(
                "push",
                "notification thread could not be resolved",
                fields: ["serverId": key.serverId, "threadId": key.threadId]
            )
            return
        }
        lifecycle.markThreadOpenedFromNotification(resolvedKey)
        appModel.activateThread(resolvedKey)
        if resolvedKey != key {
            notificationNavigationRequest = resolvedKey
        }
        // A cached snapshot can predate the turn's end (events missed while
        // suspended): reconcile with the host instead of trusting the push.
        if !(await refreshThreadAuthoritatively(resolvedKey, appModel: appModel)),
           await appModel.client.awaitServerConnected(
               serverId: resolvedKey.serverId,
               timeoutMs: Self.notificationConnectTimeoutMs
           ) {
            // Foreground recovery may have replaced the connection meanwhile.
            _ = await refreshThreadAuthoritatively(resolvedKey, appModel: appModel)
        }
        await appModel.refreshThreadSnapshot(key: resolvedKey)
        LLog.info(
            "push",
            "notification thread resolved and activated",
            fields: ["serverId": resolvedKey.serverId, "threadId": resolvedKey.threadId]
        )
    }

    func consumeNotificationNavigationRequest() -> ThreadKey? {
        let key = notificationNavigationRequest
        notificationNavigationRequest = nil
        return key
    }

    private func refreshThreadAuthoritatively(_ key: ThreadKey, appModel: AppModel) async -> Bool {
        do {
            try await appModel.forceRefreshThreadAuthoritative(key: key)
            return true
        } catch {
            LLog.warn(
                "push",
                "authoritative refresh of notification thread failed",
                fields: [
                    "serverId": key.serverId,
                    "threadId": key.threadId,
                    "error": error.localizedDescription
                ]
            )
            return false
        }
    }

    func handleSnapshot(_ snapshot: AppSnapshotRecord?) {
        let now = CFAbsoluteTimeGetCurrent()
        let elapsed = now - lastLiveActivitySyncTime
        if elapsed >= 3.0 {
            lastLiveActivitySyncTime = now
            liveActivities.sync(snapshot)
        } else if !pendingLiveActivitySync {
            pendingLiveActivitySync = true
            let delay = 3.0 - elapsed
            Task { @MainActor [weak self] in
                try? await Task.sleep(for: .seconds(delay))
                guard let self else { return }
                self.pendingLiveActivitySync = false
                self.lastLiveActivitySyncTime = CFAbsoluteTimeGetCurrent()
                self.liveActivities.sync(self.appModel?.snapshot)
            }
        }
    }

    func appDidEnterBackground() {
        guard let appModel else { return }
        appModel.reconnectController.onAppEnteredBackground()
        lifecycle.appDidEnterBackground(
            appModel: appModel,
            hasActiveVoiceSession: voiceRuntime?.activeVoiceSession != nil,
            liveActivities: liveActivities
        )
    }

    func appDidBecomeInactive() {
        guard let appModel else { return }
        appModel.reconnectController.onAppBecameInactive()
    }

    func appDidBecomeActive() {
        guard let appModel else { return }
        // Keep lifecycle state in sync even when foreground recovery exits early
        // for an already-running voice session.
        appModel.reconnectController.noteAppBecameActive()
        lifecycle.appDidBecomeActive(
            appModel: appModel,
            hasActiveVoiceSession: voiceRuntime?.activeVoiceSession != nil,
            liveActivities: liveActivities
        )
    }
}
