import XCTest
import UserNotifications
import WatchConnectivity
@testable import AgentBuddy

@MainActor
final class WatchCompanionBridgeTests: XCTestCase {

    /// In-memory `WatchTransport` that records `updateApplicationContext`
    /// calls and lets a test pin the WC connection state.
    final class StubWatchTransport: WatchTransport {
        var activationState: WCSessionActivationState
        var isPaired: Bool
        var isWatchAppInstalled: Bool
        var isReachable: Bool
        var sentContexts: [[String: Any]] = []
        var nextSendError: Error?

        init(
            activationState: WCSessionActivationState = .activated,
            isPaired: Bool = true,
            isWatchAppInstalled: Bool = true,
            isReachable: Bool = true
        ) {
            self.activationState = activationState
            self.isPaired = isPaired
            self.isWatchAppInstalled = isWatchAppInstalled
            self.isReachable = isReachable
        }

        func updateApplicationContext(_ context: [String: Any]) throws {
            if let nextSendError {
                self.nextSendError = nil
                throw nextSendError
            }
            sentContexts.append(context)
        }
    }

    // The bridge currently reads `AppModel.shared.snapshot` directly. To
    // keep tests isolated we restore whatever the singleton held at the
    // start of each test in tearDown.
    private var savedSnapshot: AppSnapshotRecord?
    // SavedThreadsStore is file-backed and shared across the whole test
    // process. Snapshot its state in setUp and restore it in tearDown so
    // tests that mutate pinned/hidden don't leak state to each other.
    private var savedPinnedKeys: [PinnedThreadKey] = []
    private var savedHiddenKeys: [PinnedThreadKey] = []

    override func setUp() {
        super.setUp()
        savedSnapshot = AppModel.shared.snapshot
        savedPinnedKeys = SavedThreadsStore.pinnedKeys()
        savedHiddenKeys = SavedThreadsStore.hiddenKeys()
        // Wipe so each test starts from a clean home-visibility state.
        for key in savedPinnedKeys { SavedThreadsStore.remove(key) }
        for key in savedHiddenKeys { SavedThreadsStore.unhide(key) }
        if let pending = AppModel.shared.composerPrefillRequest {
            AppModel.shared.clearComposerPrefill(id: pending.id)
        }
    }

    override func tearDown() {
        AppModel.shared.applySnapshot(savedSnapshot)
        // Wipe whatever the test left behind…
        for key in SavedThreadsStore.pinnedKeys() { SavedThreadsStore.remove(key) }
        for key in SavedThreadsStore.hiddenKeys() { SavedThreadsStore.unhide(key) }
        // …and restore the original state in original order. `add` and
        // `hide` both prepend, so iterate in reverse to preserve order.
        for key in savedPinnedKeys.reversed() { SavedThreadsStore.add(key) }
        for key in savedHiddenKeys.reversed() { SavedThreadsStore.hide(key) }
        if let pending = AppModel.shared.composerPrefillRequest {
            AppModel.shared.clearComposerPrefill(id: pending.id)
        }
        super.tearDown()
    }

    // MARK: - 1. Complication mode = .running with real runtime

    func testComplicationSnapshotEmitsRunningModeWithRealTurnStartAndTaskId() throws {
        let now = Date()
        let startedMs = Int64((now.timeIntervalSince1970 - 90) * 1000)
        let summary = makeSummary(
            serverId: "macbook",
            threadId: "t1",
            updatedAt: Int64(now.timeIntervalSince1970),
            hasActiveTurn: true,
            title: "fix auth",
            lastTurnStartMs: startedMs
        )
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [summary]
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())

        let data = try XCTUnwrap(bridge.currentComplicationSnapshot())
        let dict = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(dict["mode"] as? String, "running")
        XCTAssertEqual(dict["taskId"] as? String, "macbook:t1")
        XCTAssertEqual(dict["lastTurnStartMsEpoch"] as? Int64, startedMs)
        XCTAssertEqual(dict["serverCount"] as? Int, 1)
    }

    // MARK: - 2. Complication mode = .idle

    func testComplicationSnapshotEmitsIdleModeWhenNoActiveTurnAndPairedTransport() throws {
        let summary = makeSummary(
            serverId: "macbook",
            threadId: "t1",
            updatedAt: 100,
            hasActiveTurn: false
        )
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook"), makeServer(id: "studio")],
            sessionSummaries: [summary]
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport(
            activationState: .activated,
            isPaired: true,
            isWatchAppInstalled: true
        ))

        let data = try XCTUnwrap(bridge.currentComplicationSnapshot())
        let dict = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(dict["mode"] as? String, "idle")
        XCTAssertEqual(dict["serverCount"] as? Int, 2)
        XCTAssertNil(dict["taskId"])
        XCTAssertNil(dict["lastTurnStartMsEpoch"])
    }

    func testComplicationSnapshotIdleMessageFallsBackWhenNoTasks() throws {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: []
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let data = try XCTUnwrap(bridge.currentComplicationSnapshot())
        let dict = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(dict["mode"] as? String, "idle")
        XCTAssertEqual(dict["title"] as? String, "1 servers ready")
    }

    // MARK: - 3. Complication mode = .offline

    func testComplicationSnapshotEmitsOfflineWhenTransportNotPaired() throws {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: []
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport(
            activationState: .activated,
            isPaired: false,
            isWatchAppInstalled: true
        ))

        let data = try XCTUnwrap(bridge.currentComplicationSnapshot())
        let dict = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(dict["mode"] as? String, "offline")
        XCTAssertNil(dict["taskId"])
        XCTAssertEqual(dict["title"] as? String, "phone unreachable")
    }

    func testComplicationSnapshotEmitsOfflineWhenTransportNotActivated() throws {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: []
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport(
            activationState: .notActivated,
            isPaired: true,
            isWatchAppInstalled: true
        ))

        let data = try XCTUnwrap(bridge.currentComplicationSnapshot())
        let dict = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(dict["mode"] as? String, "offline")
    }

    func testComplicationSnapshotEmitsOfflineWhenWatchAppNotInstalled() throws {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: []
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport(
            activationState: .activated,
            isPaired: true,
            isWatchAppInstalled: false
        ))

        let data = try XCTUnwrap(bridge.currentComplicationSnapshot())
        let dict = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(dict["mode"] as? String, "offline")
    }
}
