import Foundation
import WatchConnectivity

/// WCSessionDelegate proxy. Declared as a separate class so the bridge can
/// own a single activation + delegate lifecycle.
final class WatchCompanionSessionDelegate: NSObject, WCSessionDelegate {
    nonisolated func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        // Bail unless the session actually came up clean. `inactive` and
        // `notActivated` show up during a watch app reinstall or pairing
        // change; firing a re-push then would race against an unsettled
        // session and either drop on the floor or surface an error.
        guard state == .activated, error == nil else { return }
        Task { @MainActor in
            // On activation, re-push so the watch gets current state.
            _ = await WatchCompanionBridge.shared.handleInbound(["kind": "snapshot.request"])
        }
    }

    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}
    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        WCSession.default.activate()
    }
    nonisolated func sessionWatchStateDidChange(_ session: WCSession) {
        Task { @MainActor in
            _ = await WatchCompanionBridge.shared.handleInbound(["kind": "snapshot.request"])
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in
            _ = await WatchCompanionBridge.shared.handleInbound(message)
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        Task { @MainActor in
            let reply = await WatchCompanionBridge.shared.handleInbound(message)
            replyHandler(reply ?? ["ok": true])
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        Task { @MainActor in
            _ = await WatchCompanionBridge.shared.handleInbound(userInfo)
        }
    }
}
