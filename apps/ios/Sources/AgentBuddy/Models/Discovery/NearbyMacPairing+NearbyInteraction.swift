#if !targetEnvironment(macCatalyst)
import Foundation
import NearbyInteraction
import simd

extension NearbyMacPairing {
    /// Spin up a local NISession so we can ship a discovery token in the
    /// pair_hello. Returns the base64-encoded token, or an empty string when
    /// this device has no UWB radio. The Rust pair host accepts empty tokens
    /// and downgrades to "no NI ranging" — BLE proximity still gates pair.
    func prepareNISession() -> String {
        guard NISession.isSupported else {
            LLog.info("pair", "NISession unsupported on this iPhone — relying on BLE proximity")
            return ""
        }
        let session = NISession()
        let delegate = NearbyMacPairingNIDelegateBox { [weak self] obj in
            Task { @MainActor in self?.handleNIUpdate(obj) }
        } invalidated: { [weak self] err in
            Task { @MainActor in self?.handleNIInvalidated(err) }
        }
        session.delegate = delegate
        niDelegateBox = delegate
        niSession = session
        guard let token = session.discoveryToken,
              let encoded = try? encodeDiscoveryToken(token)
        else {
            LLog.warn("pair", "NISession produced no discovery token — relying on BLE proximity")
            return ""
        }
        return encoded
    }

    func handleMacNIToken(b64: String) {
        // Empty/missing token is the expected case for current Macs (no
        // U1/U2 on any shipping Mac); BLE proximity is the trigger then.
        guard !b64.isEmpty, let session = niSession else {
            if state == .handshaking {
                LLog.info("pair", "no NI ranging — BLE proximity is the trigger")
            }
            return
        }
        guard let tokenData = Data(base64Encoded: b64), !tokenData.isEmpty,
              let macToken = decodeDiscoveryToken(tokenData)
        else {
            LLog.warn("pair", "mac NI discovery token undecodable; falling back to BLE-only proximity")
            return
        }
        pendingMacDiscoveryToken = macToken
        let config = NINearbyPeerConfiguration(peerToken: macToken)
        session.run(config)
        LLog.info("pair", "NISession started against mac token")
    }

    private func handleNIUpdate(_ object: NINearbyObject?) {
        let distance = object?.distance
        lastDistance = distance
        lastDirection = object?.direction
        if #available(iOS 16.0, *) {
            lastHorizontalAngle = object?.horizontalAngle
        }
        lastUpdate = Date()
        if let distance {
            // Fire-and-forget distance ping for the Mac UI affordance.
            try? pairClient?.submitNiDistance(distanceM: distance)
            if distance <= Self.pairDistanceThreshold {
                triggerPairRequest(reason: "ni_distance", distance: distance)
            }
        }
    }

    private func handleNIInvalidated(_ error: Error) {
        LLog.warn("pair", "NISession invalidated", fields: ["error": String(describing: error)])
        // BLE proximity can still drive the pair trigger — only fail the
        // flow if we have no fallback signal in flight.
        niSession = nil
        niDelegateBox = nil
    }
}

// MARK: - NI token coding helpers

private enum NICodingError: Error {
    case archiveFailed
}

private func encodeDiscoveryToken(_ token: NIDiscoveryToken) throws -> String {
    let data = try NSKeyedArchiver.archivedData(
        withRootObject: token,
        requiringSecureCoding: true
    )
    return data.base64EncodedString()
}

private func decodeDiscoveryToken(_ data: Data) -> NIDiscoveryToken? {
    return try? NSKeyedUnarchiver.unarchivedObject(
        ofClass: NIDiscoveryToken.self,
        from: data
    )
}

// MARK: - NI delegate shim

/// Minimal NSObject delegate box so `NearbyMacPairing` (an `Observable`
/// class) can stay non-NSObject. Delegates the two callbacks we care
/// about: `didUpdate nearbyObjects` → distance updates, and session
/// invalidation → teardown hook.
final class NearbyMacPairingNIDelegateBox: NSObject, NISessionDelegate, @unchecked Sendable {
    private let onUpdate: (NINearbyObject?) -> Void
    private let onInvalidated: (Error) -> Void

    init(
        onUpdate: @escaping (NINearbyObject?) -> Void,
        invalidated: @escaping (Error) -> Void
    ) {
        self.onUpdate = onUpdate
        self.onInvalidated = invalidated
    }

    func session(_ session: NISession, didUpdate nearbyObjects: [NINearbyObject]) {
        // We paired with exactly one peer (the Mac) so the first object is
        // ours.
        onUpdate(nearbyObjects.first)
    }

    func session(_ session: NISession, didInvalidateWith error: Error) {
        onInvalidated(error)
    }

    func sessionWasSuspended(_ session: NISession) {}
    func sessionSuspensionEnded(_ session: NISession) {}
}
#endif
