import XCTest
import WatchConnectivity
@testable import AgentBuddy

extension WatchCompanionBridgeTests {

    // MARK: - 4. Inbound prompt routing

    func testInboundPromptWithKnownThreadQueuesComposerPrefillOnThatThread() async {
        let key = ThreadKey(serverId: "macbook", threadId: "t1")
        let summary = makeSummary(
            serverId: "macbook",
            threadId: "t1",
            updatedAt: 100,
            hasActiveTurn: false
        )
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [summary]
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound([
            "kind": "prompt.send",
            "text": "hi from watch",
            "serverId": "macbook",
            "threadId": "t1"
        ])

        XCTAssertEqual(reply?["ok"] as? Bool, true)
        XCTAssertEqual(reply?["threadId"] as? String, "t1")

        let prefill = AppModel.shared.composerPrefillRequest
        XCTAssertEqual(prefill?.threadKey, key)
        XCTAssertEqual(prefill?.text, "hi from watch")
    }

    func testInboundPromptFallsBackToActiveThreadWhenServerAndThreadAreMissing() async {
        let key = ThreadKey(serverId: "macbook", threadId: "active")
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [makeSummary(
                serverId: "macbook",
                threadId: "active",
                updatedAt: 100,
                hasActiveTurn: false
            )],
            activeThread: key
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound([
            "kind": "prompt.send",
            "text": "fallback"
        ])

        XCTAssertEqual(reply?["ok"] as? Bool, true)
        XCTAssertEqual(reply?["threadId"] as? String, "active")

        let prefill = AppModel.shared.composerPrefillRequest
        XCTAssertEqual(prefill?.threadKey, key)
        XCTAssertEqual(prefill?.text, "fallback")
    }

    func testInboundPromptWithEmptyTextReturnsErrorWithoutPrefill() async {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [],
            activeThread: ThreadKey(serverId: "macbook", threadId: "t1")
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound([
            "kind": "prompt.send",
            "text": "   "
        ])

        XCTAssertEqual(reply?["ok"] as? Bool, false)
        XCTAssertEqual(reply?["error"] as? String, "empty prompt")
        XCTAssertNil(AppModel.shared.composerPrefillRequest)
    }

    func testInboundPromptWithNoActiveThreadAndNoServerReturnsError() async {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [],
            sessionSummaries: [],
            activeThread: nil
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound([
            "kind": "prompt.send",
            "text": "hi"
        ])

        XCTAssertEqual(reply?["ok"] as? Bool, false)
        XCTAssertEqual(reply?["error"] as? String, "no active task")
    }

    // MARK: - 5. Inbound approval

    func testInboundApprovalWithMissingFieldsReturnsInvalidPayloadError() async {
        let bridge = WatchCompanionBridge(transport: StubWatchTransport())

        let missingApprove = await bridge.handleInbound([
            "kind": "approval.decision",
            "requestId": "x"
        ])
        XCTAssertEqual(missingApprove?["ok"] as? Bool, false)
        XCTAssertEqual(missingApprove?["error"] as? String, "invalid approval payload")

        let missingId = await bridge.handleInbound([
            "kind": "approval.decision",
            "approve": true
        ])
        XCTAssertEqual(missingId?["ok"] as? Bool, false)
        XCTAssertEqual(missingId?["error"] as? String, "invalid approval payload")
    }

    func testInboundApprovalForwardsToStoreAndRepliesAccordingToOutcome() async {
        // We can't run a real respondToApproval in the test environment
        // (no server to talk to), but we *can* assert the bridge dispatches
        // the call: the reply will be `{ok: false, error: "..."}` because
        // the store has no matching request id. The test verifies the
        // bridge parsed the payload and routed it into the store path
        // (not into the "invalid payload" branch).
        let bridge = WatchCompanionBridge(transport: StubWatchTransport())

        let reply = await bridge.handleInbound([
            "kind": "approval.decision",
            "requestId": "unknown-request",
            "approve": true
        ])

        // Either ok:true (if store accepted) or ok:false with a *non-payload*
        // error (i.e., not "invalid approval payload"). Any other shape means
        // the bridge took the wrong branch.
        XCTAssertNotNil(reply)
        XCTAssertNotEqual(reply?["error"] as? String, "invalid approval payload")
    }

    // MARK: - 6. snapshot.request triggers a fresh push

    func testSnapshotRequestForcesPushThroughTransport() async {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [makeSummary(
                serverId: "macbook",
                threadId: "t1",
                updatedAt: 100,
                hasActiveTurn: false
            )]
        ))

        let stub = StubWatchTransport(
            activationState: .activated,
            isPaired: true,
            isWatchAppInstalled: true
        )
        let bridge = WatchCompanionBridge(transport: stub)

        let reply = await bridge.handleInbound(["kind": "snapshot.request"])
        XCTAssertEqual(reply?["ok"] as? Bool, true)

        // The push goes through a 150ms throttle. Wait for it to fire.
        try? await Task.sleep(nanoseconds: 250_000_000)

        XCTAssertFalse(stub.sentContexts.isEmpty, "expected at least one context push after snapshot.request")
        let context = stub.sentContexts.last
        XCTAssertNotNil(context?["agentbuddy.snapshot"] as? Data)
    }

    func testSnapshotRequestPushesWhenWatchInstallFlagIsStale() async {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: []
        ))

        let stub = StubWatchTransport(
            activationState: .activated,
            isPaired: true,
            isWatchAppInstalled: false
        )
        let bridge = WatchCompanionBridge(transport: stub)

        let reply = await bridge.handleInbound(["kind": "snapshot.request"])
        XCTAssertEqual(reply?["ok"] as? Bool, true)

        try? await Task.sleep(nanoseconds: 250_000_000)

        XCTAssertFalse(
            stub.sentContexts.isEmpty,
            "expected snapshot push even when WCSession has stale isWatchAppInstalled state"
        )
        XCTAssertNotNil(stub.sentContexts.last?["agentbuddy.snapshot"] as? Data)
    }

    // MARK: - 7. Unknown kind returns nil

    func testUnknownKindReturnsNilSoDelegateRepliesGenericAck() async {
        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound(["kind": "unsupported.message"])
        XCTAssertNil(reply)
    }

    func testMessageWithoutKindReturnsNil() async {
        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound(["text": "no kind here"])
        XCTAssertNil(reply)
    }
}
