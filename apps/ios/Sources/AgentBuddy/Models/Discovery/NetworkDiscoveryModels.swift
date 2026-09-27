import Foundation

struct BonjourDiscoverySeed: Hashable {
    let name: String
    let host: String
    let port: UInt16?
    let serviceType: String
}

struct TailscalePeerIdentity: Equatable {
    let ip: String
    let name: String?
}

struct TailscaleAvailability: Equatable, Sendable {
    let appInstalled: Bool
    let likelyActiveTunnel: Bool

    var shouldSurfaceDiscoveryNotice: Bool {
        appInstalled || likelyActiveTunnel
    }

    var logDescription: String {
        "installed=\(appInstalled) likelyActive=\(likelyActiveTunnel)"
    }
}

enum TailscalePeerParseError: Error, Equatable {
    case unsupportedSurface
    case invalidPayload
}
