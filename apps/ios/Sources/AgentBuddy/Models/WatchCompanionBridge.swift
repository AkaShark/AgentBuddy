import Foundation
import UserNotifications
import WatchConnectivity
#if canImport(WidgetKit)
import WidgetKit
#endif

/// Thin transport seam over `WCSession` so unit tests can drive
/// `WatchCompanionBridge` without a real WatchConnectivity stack. Production
/// uses the default `WCSession.default` conformance below.
@MainActor
protocol WatchTransport {
    var activationState: WCSessionActivationState { get }
    var isPaired: Bool { get }
    var isWatchAppInstalled: Bool { get }
    var isReachable: Bool { get }
    func updateApplicationContext(_ context: [String: Any]) throws
}

extension WCSession: WatchTransport {}

/// iOS side of the Watch companion pipeline.
///
/// - Observes `AppModel.shared.snapshot` and whenever it changes, projects
///   the relevant slice into a `WatchSnapshotPayload` and pushes it to the
///   paired watch via `WCSession.updateApplicationContext`.
/// - Writes a lightweight complication snapshot to the shared App Group so
///   the watchOS complications can read it even when the app isn't active.
/// - Receives inbound messages from the watch (approval decisions,
///   dictated prompts, voice control) and dispatches them back into
///   `AppStore` / composer / `VoiceRuntimeController`.
///
/// Kept thin: no state reducer logic here. Just projection + plumbing.
@MainActor
final class WatchCompanionBridge: NSObject {
    static let shared = WatchCompanionBridge()

    static let appGroupSuite = "group.com.akashark.agentbuddy"
    static let snapshotKey = "watch.snapshot.v1"
    static let snapshotTimestampKey = "watch.snapshot.v1.timestamp"
    static let complicationSnapshotKey = "complication.snapshot.v1"
    // Per-server complication slices keyed by serverId — read by the
    // widget configuration intent when the user has pinned a complication
    // to a single server (Task #7).
    static let perServerComplicationKey = "complication.per-server.v1"
    // Connected-server picker list populated by `ServerEntityQuery` so the
    // watch face edit sheet can show real servers.
    static let serverListKey = "servers.v1"
    static let complicationKinds = [
        "AgentBuddyCircularComplication",
        "AgentBuddyCornerComplication",
        "AgentBuddyRectangularComplication",
    ]

    private let delegate = WatchCompanionSessionDelegate()
    var lastPushedPayload: WatchSnapshotPayload?
    var lastPushedComplication: Data?
    var pushThrottle: Task<Void, Never>?
    private var themeObserver: NSObjectProtocol?
    private var preferencesObserver: NSObjectProtocol?
    /// Request ids the bridge has already scheduled an approval push for.
    /// Used to avoid duplicate banners when the snapshot tracker re-fires for
    /// unrelated mutations.
    var notifiedApprovalIds: Set<String> = []

    /// Injected WatchConnectivity surface. Tests pass a fake; production
    /// uses `WCSession.default` via the conformance above.
    var transport: WatchTransport

    private override convenience init() {
        self.init(transport: WCSession.default)
    }

    init(transport: WatchTransport) {
        self.transport = transport
        super.init()
    }

    func start() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = delegate
        session.activate()
        observe()
        observeThemeChanges()
        observeHomePreferencesChanges()
    }

    /// Pin/hide changes don't mutate `AppModel.snapshot` so the snapshot
    /// observation tracker won't notice them. Fire a re-push whenever the
    /// SavedThreadsStore notifies preferences changed (CloudKV sync, local
    /// pin/hide actions, watch-originated hide).
    private func observeHomePreferencesChanges() {
        guard preferencesObserver == nil else { return }
        preferencesObserver = NotificationCenter.default.addObserver(
            forName: .agentBuddyThreadPreferencesDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.lastPushedPayload = nil
                self.lastPushedComplication = nil
                self.pushIfChanged()
            }
        }
    }

    /// Theme changes don't touch `AppModel.snapshot`, so the observation
    /// tracker above won't fire. Listen for `.themeDidChange` and force a
    /// re-push by clearing the diff state, then go through the same throttle
    /// path the snapshot pump uses.
    private func observeThemeChanges() {
        guard themeObserver == nil else { return }
        themeObserver = NotificationCenter.default.addObserver(
            forName: .themeDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.lastPushedPayload = nil
                self.pushIfChanged()
            }
        }
    }

    deinit {
        if let themeObserver {
            NotificationCenter.default.removeObserver(themeObserver)
        }
        if let preferencesObserver {
            NotificationCenter.default.removeObserver(preferencesObserver)
        }
    }

    // MARK: - Observation

    /// Observe the canonical Rust-backed `AppModel.shared.snapshot` via
    /// `withObservationTracking`. Each `onChange` re-arms a fresh tracker on
    /// the main actor, which is the same pattern `HomeDashboardModel` uses.
    private func observe() {
        withObservationTracking {
            // Touch every field that participates in the watch payload or
            // complication entry so a mutation to any of them schedules a
            // push. `pushIfChanged()` is the single sink that diffs against
            // the last successful push.
            _ = AppModel.shared.snapshot
        } onChange: { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                self.pushIfChanged()
                self.observe()
            }
        }
        // Run an initial push so first-launch state lands on the watch
        // even before the snapshot mutates.
        pushIfChanged()
    }

    func pushIfChanged() {
        let payload = currentPayload()
        let complication = currentComplicationSnapshot()

        if payload != lastPushedPayload {
            push(payload: payload)
        }

        if complication != lastPushedComplication {
            lastPushedComplication = complication
            writeComplication(complication)
        }

        // Per-server complication slices + server picker list. These power
        // the watch face configuration intent (Task #7). They're cheap to
        // compute and small, so just re-publish on every change rather
        // than diffing — keeps the bridge state surface from growing.
        writePerServerComplicationSnapshots()
        writeServerListPayload()

        scheduleApprovalNotificationsIfNeeded()
    }
}
