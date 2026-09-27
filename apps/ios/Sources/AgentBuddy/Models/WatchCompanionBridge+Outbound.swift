import Foundation
import WatchConnectivity
#if canImport(WidgetKit)
import WidgetKit
#endif

extension WatchCompanionBridge {
    // MARK: - Outbound

    func push(payload: WatchSnapshotPayload) {
        guard let data = try? JSONEncoder().encode(payload) else { return }

        // Cold-launch hydration: even if the watch isn't currently paired,
        // write the latest snapshot to the App Group so the watch can seed
        // from disk on next launch (A4).
        if let defaults = UserDefaults(suiteName: Self.appGroupSuite) {
            defaults.set(data, forKey: Self.snapshotKey)
            defaults.set(Date().timeIntervalSince1970, forKey: Self.snapshotTimestampKey)
        }

        guard transport.activationState == .activated else { return }
        guard transport.isPaired else { return }

        // Throttle: coalesce rapid mutations into a single
        // updateApplicationContext call. Kept at 150ms — fast enough that
        // the watch feels live, slow enough to coalesce a turn-burst.
        pushThrottle?.cancel()
        pushThrottle = Task { @MainActor [weak self, transport] in
            try? await Task.sleep(nanoseconds: 150_000_000)
            guard !Task.isCancelled else { return }
            do {
                try transport.updateApplicationContext(["agentbuddy.snapshot": data])
                self?.lastPushedPayload = payload
            } catch {
                LLog.error("watch", "push failed: \(error.localizedDescription)")
            }
        }
    }

    func writeComplication(_ data: Data?) {
        guard let data,
              let defaults = UserDefaults(suiteName: Self.appGroupSuite)
        else { return }
        defaults.set(data, forKey: Self.complicationSnapshotKey)

        #if canImport(WidgetKit)
        for kind in Self.complicationKinds {
            WidgetCenter.shared.reloadTimelines(ofKind: kind)
        }
        #endif
    }
}
