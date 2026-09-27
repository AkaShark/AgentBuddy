#if !targetEnvironment(macCatalyst)
import Foundation

extension NearbyMacPairing {
    // MARK: - BLE proximity scan

    func startBLEScan() {
        guard bleScanner == nil else { return }
        let scanner = PairBLEScanner()
        bleScanner = scanner
        scanner.start { [weak self] peripheralId, _, rssi in
            self?.handleBLESample(peripheralId: peripheralId, rssi: rssi)
        }
    }

    private func handleBLESample(peripheralId: UUID, rssi: Int) {
        guard isRunning else { return }
        let tracker = bleTrackers[peripheralId] ?? PairBLEPeerTracker()
        tracker.record(rssi: rssi)
        bleTrackers[peripheralId] = tracker

        // Track the strongest peer for the debug UI; assume the strongest
        // BLE advertiser is the same Mac the user is trying to pair with.
        let strongest = bleTrackers.max { lhs, rhs in
            (lhs.value.lastRssi ?? Int.min) < (rhs.value.lastRssi ?? Int.min)
        }
        if let strongest {
            bleStrongestPeer = strongest.key
            lastRssi = strongest.value.lastRssi
            smoothedRssi = strongest.value.smoothedRssi
            bleProximity = PairBLE.Bucket.from(rssi: strongest.value.lastRssi)
            bleEstimatedDistance = strongest.value.lastRssi.flatMap { PairBLE.estimateDistanceMeters(rssi: $0) }
        }

        // Relay BLE-derived distance to the Mac when NI ranging isn't
        // producing readings (which is always, on current Macs). Throttled
        // to 5 Hz so we don't spam the WS pair channel; that's plenty for
        // the Mac's pulse animation.
        if lastDistance == nil, let est = bleEstimatedDistance {
            relayDistanceIfNeeded(distanceM: Float(est))
        }

        if tracker.hasTripped {
            triggerPairRequest(reason: "ble_rssi", distance: nil)
        }
    }

    private func relayDistanceIfNeeded(distanceM: Float) {
        let now = Date()
        guard now.timeIntervalSince(lastDistanceRelayAt) > 0.2 else { return }
        lastDistanceRelayAt = now
        guard let client = pairClient else {
            // No WS yet — first BLE samples can fire before Bonjour
            // discovery + WS connect complete. Quiet warning so we can
            // distinguish "no client" from "send failed."
            return
        }
        do {
            try client.submitNiDistance(distanceM: distanceM)
            LLog.debug("pair", "relayed distance", fields: ["distance_m": distanceM])
        } catch {
            LLog.warn("pair", "relay submit failed", fields: ["error": String(describing: error)])
        }
    }

    // MARK: - Ultrasonic Doppler reader

    func startUltrasonicReader() {
        guard ultrasonicReader == nil else { return }
        let reader = UltrasonicReader()
        reader.onSample = { [weak self] velocity, peakHz, confidence in
            Task { @MainActor in self?.handleUltrasonicSample(velocity: velocity, peakHz: peakHz, confidence: confidence) }
        }
        ultrasonicReader = reader
        Task { @MainActor in
            do {
                try await reader.start()
            } catch {
                LLog.warn("pair", "ultrasonic reader unavailable", fields: ["error": String(describing: error)])
            }
        }
    }

    private func handleUltrasonicSample(velocity: Float?, peakHz: Float?, confidence: Float?) {
        guard isRunning else { return }
        ultrasonicPeakHz = peakHz
        ultrasonicConfidence = confidence
        if let velocity {
            // Read the smoothed value back out of the reader so the UI
            // sees an EMA-stable velocity rather than per-frame jitter.
            dopplerVelocityMS = ultrasonicReader?.smoothedVelocityMS ?? velocity
        }
    }
}
#endif
