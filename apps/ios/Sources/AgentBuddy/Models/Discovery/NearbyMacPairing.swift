#if !targetEnvironment(macCatalyst)
import Foundation
import NearbyInteraction
import Observation
import UIKit
import simd

enum NearbyMacPairingState: Equatable {
    case searching
    case connecting
    case handshaking
    case awaitingConfirm
    case paired
    case rejected
    case failed
}

/// iOS-only first-launch onboarding: browse for a nearby `_litter-pair._tcp.`
/// service, open a WebSocket pair session via Rust, run NISession against
/// the Mac's NI discovery token, and save a SavedServer on accept. Runs
/// only when SavedServerStore has no user-remembered servers. The class
/// is single-shot: each attempt runs a fresh instance via `start()`.
///
/// Wiring: platform (Swift) owns Bonjour browse + NISession; Rust owns the
/// pair protocol state machine and reports transitions through a polled
/// event stream. We re-expose state as an `@Observable` so SwiftUI can
/// render the onboarding view directly.
@MainActor
@Observable
final class NearbyMacPairing: NSObject {
    static let shared = NearbyMacPairing()

    /// Bonjour service type we advertise on and browse for pairing.
    private static let pairServiceType = "_litter-pair._tcp."
    /// How long to browse for a pair service before showing a "couldn't
    /// find" fallback UI.
    private static let browseTimeout: TimeInterval = 20
    /// Distance threshold (meters, NI-derived) at which we auto-send
    /// pair_request. Only fires when both peers have UWB and `NISession`
    /// produces real readings; otherwise BLE proximity is the trigger.
    static let pairDistanceThreshold: Float = 1.0
    /// Upper bound on the entire onboarding flow before we treat it as a
    /// timeout and let the user fall back to manual discovery.
    private static let flowTimeout: TimeInterval = 60

    // MARK: - Public observable state

    var state: NearbyMacPairingState = .searching
    var discoveredMacName: String?
    var lastDistance: Float?
    var lastDirection: simd_float3?
    var lastHorizontalAngle: Float?
    var lastUpdate: Date?
    var isRunning: Bool = false
    /// When true, suppress the auto pair_request submission so we can stay
    /// in the ranging state indefinitely for debugging UWB/BLE readings.
    var debugMode: Bool = false

    /// BLE-derived proximity state, exposed for the debug view. The scanner
    /// runs whenever the flow is active, regardless of UWB capability — it's
    /// the actual proximity signal on Macs (which all lack a U1/U2 chip).
    var lastRssi: Int?
    var smoothedRssi: Float?
    var bleProximity: PairBLE.Bucket = .unknown
    var bleEstimatedDistance: Double?

    /// Ultrasonic Doppler velocity (m/s, positive = approaching the Mac).
    /// Smoothed via EMA in `UltrasonicReader`. Updates ~12 Hz when the
    /// 19 kHz carrier is detectable.
    var dopplerVelocityMS: Float?
    /// Detected ultrasonic peak frequency in Hz, or nil when below the
    /// detection threshold (Mac out of acoustic range).
    var ultrasonicPeakHz: Float?
    /// Confidence proxy in [0, ~10ish]; >2 means the carrier is clearly
    /// audible to the iPhone mic.
    var ultrasonicConfidence: Float?

    /// Set when pair_accept arrives. Consumed by `AgentBuddyApp`/ContentView to
    /// open a connection and dismiss onboarding.
    var completedServer: SavedServer?

    // MARK: - Internals

    let appClient = AppClient()
    private var browser: BonjourServiceDiscoverer?
    private var browseTask: Task<Void, Never>?
    private var flowTimeoutTask: Task<Void, Never>?
    var pollTask: Task<Void, Never>?
    var pairClient: PairClientHandle?
    var niSession: NISession?
    var niDelegateBox: NearbyMacPairingNIDelegateBox?
    var pendingMacDiscoveryToken: NIDiscoveryToken?
    var bleScanner: PairBLEScanner?
    var bleTrackers: [UUID: PairBLEPeerTracker] = [:]
    var bleStrongestPeer: UUID?
    var ultrasonicReader: UltrasonicReader?
    var lastDistanceRelayAt: Date = .distantPast
    var didSendPairRequest = false
    var deviceName: String = UIDevice.current.name
    private weak var appModel: AppModel?

    override private init() {
        super.init()
    }

    /// Start the onboarding flow. Idempotent while running. No-op if the
    /// user already has remembered servers. Runs on every iPhone — UWB is
    /// optional now that BLE proximity covers Macs without a U1/U2 chip.
    func startIfNeeded(appModel: AppModel) {
        guard !isRunning else { return }
        guard SavedServerStore.rememberedServers().isEmpty else {
            LLog.info("pair", "skipping onboarding, user already has remembered servers")
            return
        }
        self.appModel = appModel
        isRunning = true
        debugMode = false
        resetTransientState()
        startFlowTimeout()
        startBrowse()
        startBLEScan()
        startUltrasonicReader()
    }

    /// Debug-only entry point. Browses for the Mac pair host and runs the
    /// same NISession + BLE proximity stack the onboarding flow uses, but
    /// skips the auto pair_request submission so the iPhone keeps streaming
    /// distance/RSSI updates indefinitely. Bypasses the saved-server gate.
    func startForDebug() {
        teardown()
        isRunning = true
        debugMode = true
        resetTransientState()
        startBrowse()
        startBLEScan()
        startUltrasonicReader()
    }

    /// Public entry point for the "Pair" feature surfaced in Settings →
    /// Experimental. Same as `startIfNeeded` but skips the saved-server
    /// gate so users can re-pair after they already have remembered
    /// servers. Auto-pair trigger is enabled (debugMode=false).
    func startPairing(appModel: AppModel) {
        guard !isRunning else { return }
        self.appModel = appModel
        isRunning = true
        debugMode = false
        resetTransientState()
        startFlowTimeout()
        startBrowse()
        startBLEScan()
        startUltrasonicReader()
    }

    /// Stop the debug session and reset state.
    func stopDebug() {
        isRunning = false
        debugMode = false
        teardown()
        state = .searching
        resetTransientState()
    }

    private func resetTransientState() {
        state = .searching
        discoveredMacName = nil
        lastDistance = nil
        lastDirection = nil
        lastHorizontalAngle = nil
        lastUpdate = nil
        lastRssi = nil
        smoothedRssi = nil
        bleProximity = .unknown
        bleEstimatedDistance = nil
        dopplerVelocityMS = nil
        ultrasonicPeakHz = nil
        ultrasonicConfidence = nil
        completedServer = nil
        didSendPairRequest = false
        bleTrackers.removeAll()
        bleStrongestPeer = nil
        lastDistanceRelayAt = .distantPast
    }

    /// User tapped "Skip" / "Set up manually" — stop everything and let
    /// ContentView dismiss the onboarding sheet.
    func cancel() {
        isRunning = false
        teardown()
    }

    /// Retry after a rejected or failed attempt. Same entry point as
    /// `startIfNeeded` but without the remembered-server check.
    func retry() {
        guard appModel != nil else { return }
        teardown()
        debugMode = false
        isRunning = true
        resetTransientState()
        startFlowTimeout()
        startBrowse()
        startBLEScan()
        startUltrasonicReader()
    }

    // MARK: - Browse

    private func startBrowse() {
        let browser = BonjourServiceDiscoverer(serviceType: Self.pairServiceType)
        self.browser = browser
        browseTask = Task { @MainActor [weak self] in
            let seeds = await browser.discover(timeout: Self.browseTimeout)
            guard let self, self.isRunning else { return }
            self.browser = nil
            // Pick the first responsive pair service. The Mac only
            // advertises a single service per process.
            guard let pick = seeds.first(where: { $0.port != nil }) else {
                LLog.info("pair", "no pair service seen within timeout")
                self.state = .failed
                return
            }
            self.discoveredMacName = pick.name
            LLog.info(
                "pair",
                "pair service discovered",
                fields: [
                    "name": pick.name,
                    "host": pick.host,
                    "port": Int(pick.port ?? 0)
                ]
            )
            self.connectAndHandshake(host: pick.host, port: pick.port ?? 0)
        }
    }

    // MARK: - Timeout + teardown

    private func startFlowTimeout() {
        flowTimeoutTask?.cancel()
        flowTimeoutTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Self.flowTimeout * 1_000_000_000))
            guard let self, self.isRunning else { return }
            if self.state == .searching || self.state == .connecting
                || self.state == .handshaking || self.state == .awaitingConfirm {
                LLog.info("pair", "flow timeout reached")
                self.state = .failed
            }
        }
    }

    func teardown() {
        browseTask?.cancel()
        browseTask = nil
        browser = nil
        flowTimeoutTask?.cancel()
        flowTimeoutTask = nil
        pollTask?.cancel()
        pollTask = nil
        if let client = pairClient {
            pairClient = nil
            Task { await client.stop() }
        }
        if let session = niSession {
            session.invalidate()
            niSession = nil
        }
        niDelegateBox = nil
        pendingMacDiscoveryToken = nil
        bleScanner?.stop()
        bleScanner = nil
        bleTrackers.removeAll()
        bleStrongestPeer = nil
        ultrasonicReader?.stop()
        ultrasonicReader = nil
    }
}
#endif
