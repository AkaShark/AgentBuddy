import XCTest
@testable import AgentBuddy

@MainActor
final class ApprovalCoordinatorTests: XCTestCase {
    private func approval(_ id: String, thread: String = "t1", kind: ApprovalKind = .command) -> PendingApproval {
        PendingApproval(
            id: id,
            serverId: "s1",
            kind: kind,
            threadId: thread,
            turnId: nil,
            itemId: nil,
            command: "make test",
            path: nil,
            grantRoot: nil,
            cwd: "/tmp",
            reason: nil
        )
    }

    func testSecondTapWhileSubmittingIsIgnored() async {
        let coordinator = ApprovalCoordinator()
        let request = approval("a1")
        var calls = 0
        let gate = AsyncGate()

        let first = Task {
            await coordinator.submit(request, decision: .accept) { _, _ in
                calls += 1
                await gate.wait()
            }
        }
        await Task.yield()
        XCTAssertTrue(coordinator.isSubmitting(request))
        await coordinator.submit(request, decision: .decline) { _, _ in calls += 1 }
        gate.open()
        await first.value

        XCTAssertEqual(calls, 1)
        XCTAssertFalse(coordinator.isSubmitting(request))
        XCTAssertEqual(coordinator.outcome(for: ThreadKey(serverId: "s1", threadId: "t1"))?.kind, .allowedOnce)
    }

    func testFailureIsRecordedAndClearedOnRetry() async {
        struct Boom: LocalizedError { var errorDescription: String? { "offline" } }
        let coordinator = ApprovalCoordinator()
        let request = approval("a1")

        await coordinator.submit(request, decision: .accept) { _, _ in throw Boom() }
        XCTAssertEqual(coordinator.failures["a1"], "offline")
        XCTAssertNil(coordinator.outcome(for: ThreadKey(serverId: "s1", threadId: "t1")))

        await coordinator.submit(request, decision: .accept) { _, _ in }
        XCTAssertNil(coordinator.failures["a1"])
        XCTAssertEqual(coordinator.outcome(for: ThreadKey(serverId: "s1", threadId: "t1"))?.kind, .allowedOnce)
    }

    func testRequestLeavingQueueWithoutLocalDecisionIsResolvedElsewhere() {
        let coordinator = ApprovalCoordinator()
        coordinator.reconcile(pending: [approval("a1"), approval("a2", thread: "t2")])
        coordinator.reconcile(pending: [approval("a2", thread: "t2")])

        XCTAssertEqual(coordinator.outcome(for: ThreadKey(serverId: "s1", threadId: "t1"))?.kind, .resolvedElsewhere)
        XCTAssertNil(coordinator.outcome(for: ThreadKey(serverId: "s1", threadId: "t2")))
    }

    func testLocallyDecidedRequestKeepsItsOutcomeWhenItLeavesTheQueue() async {
        let coordinator = ApprovalCoordinator()
        let request = approval("a1")
        coordinator.reconcile(pending: [request])
        await coordinator.submit(request, decision: .acceptForSession) { _, _ in }
        coordinator.reconcile(pending: [])

        XCTAssertEqual(coordinator.outcome(for: ThreadKey(serverId: "s1", threadId: "t1"))?.kind, .allowedForSession)
    }

    func testMcpElicitationIsNotAnswerableOnPhone() {
        XCTAssertFalse(approval("a1", kind: .mcpElicitation).isAnswerableOnPhone)
        XCTAssertTrue(approval("a2", kind: .fileChange).isAnswerableOnPhone)
    }
}

/// Minimal one-shot gate for suspending a responder until the test opens it.
@MainActor
private final class AsyncGate {
    private var continuation: CheckedContinuation<Void, Never>?
    private var isOpen = false

    func wait() async {
        if isOpen { return }
        await withCheckedContinuation { continuation = $0 }
    }

    func open() {
        isOpen = true
        continuation?.resume()
        continuation = nil
    }
}
