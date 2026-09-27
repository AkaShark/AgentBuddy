import XCTest
@testable import AgentBuddy

@MainActor
final class WatchProjectionTests: XCTestCase {

    // MARK: - 1. Status ranking + sort tiebreak

    func testTasksOrdersNeedsApprovalThenRunningThenIdleAndTieBreaksByRecency() {
        let needsApproval = makeSummary(
            serverId: "srv",
            threadId: "needs",
            updatedAt: 100,
            hasActiveTurn: false
        )
        let running = makeSummary(
            serverId: "srv",
            threadId: "running",
            updatedAt: 200,
            hasActiveTurn: true
        )
        let idleNew = makeSummary(
            serverId: "srv",
            threadId: "idle-new",
            updatedAt: 500,
            hasActiveTurn: false
        )
        let idleOld = makeSummary(
            serverId: "srv",
            threadId: "idle-old",
            updatedAt: 300,
            hasActiveTurn: false
        )
        let bothApprovalAndRunning = makeSummary(
            serverId: "srv",
            threadId: "both",
            updatedAt: 50,
            hasActiveTurn: true
        )

        let approvals = [
            makePendingApproval(id: "a-needs", threadId: "needs", kind: .command),
            makePendingApproval(id: "a-both", threadId: "both", kind: .fileChange)
        ]

        let result = WatchProjection.tasks(
            summaries: [idleOld, running, idleNew, needsApproval, bothApprovalAndRunning],
            threads: [],
            pendingApprovals: approvals
        )

        // needsApproval entries first; among them, the more recent updatedAt
        // wins (needs=100 > both=50). Then running, then idle (idle-new=500 > idle-old=300).
        XCTAssertEqual(
            result.map(\.threadId),
            ["needs", "both", "running", "idle-new", "idle-old"]
        )
        XCTAssertEqual(result[0].status, .needsApproval)
        XCTAssertEqual(result[1].status, .needsApproval)
        XCTAssertEqual(result[2].status, .running)
        XCTAssertEqual(result[3].status, .idle)
        XCTAssertEqual(result[4].status, .idle)
    }

    func testTasksIgnoresMcpElicitationApprovalsForStatusGrouping() {
        // mcpElicitation approvals are filtered out of status grouping —
        // the thread should fall back to its non-approval status.
        let summary = makeSummary(
            serverId: "srv",
            threadId: "thread",
            updatedAt: 100,
            hasActiveTurn: true
        )
        let approval = makePendingApproval(
            id: "ignored",
            threadId: "thread",
            kind: .mcpElicitation
        )

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [],
            pendingApprovals: [approval]
        )

        XCTAssertEqual(result.first?.status, .running)
        XCTAssertNil(result.first?.pendingApprovalId)
    }

    // MARK: - 3. relativeTime

    func testRelativeTimeFormatsAcrossExpectedBuckets() {
        // We can't drive Date.now in the implementation, so we pick deltas
        // that are far enough from boundaries to be stable across millisecond
        // jitter during the test run.
        let now = Date()
        func epoch(_ secondsAgo: TimeInterval) -> Int64 {
            Int64(now.timeIntervalSince1970 - secondsAgo)
        }

        let s30 = makeSummary(serverId: "srv", threadId: "s30", updatedAt: epoch(30), hasActiveTurn: false)
        let s90 = makeSummary(serverId: "srv", threadId: "s90", updatedAt: epoch(90), hasActiveTurn: false)
        let s65min = makeSummary(serverId: "srv", threadId: "s65", updatedAt: epoch(65 * 60), hasActiveTurn: false)
        let s25h = makeSummary(serverId: "srv", threadId: "s25h", updatedAt: epoch(25 * 3600), hasActiveTurn: false)
        let s8d = makeSummary(serverId: "srv", threadId: "s8d", updatedAt: epoch(8 * 86400), hasActiveTurn: false)
        let sNoTime = makeSummary(serverId: "srv", threadId: "sNo", updatedAt: nil, hasActiveTurn: false)

        let result = WatchProjection.tasks(
            summaries: [s30, s90, s65min, s25h, s8d, sNoTime],
            threads: [],
            pendingApprovals: []
        )
        let byThread = Dictionary(uniqueKeysWithValues: result.map { ($0.threadId, $0) })

        XCTAssertEqual(byThread["s30"]?.relativeTime, "now")
        XCTAssertEqual(byThread["s90"]?.relativeTime, "1m")
        XCTAssertEqual(byThread["s65"]?.relativeTime, "1h")
        XCTAssertEqual(byThread["s25h"]?.relativeTime, "1d")
        // 8 days ago -> formatter "MMM d"
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        let expected = formatter.string(from: Date(timeIntervalSince1970: TimeInterval(epoch(8 * 86400))))
        XCTAssertEqual(byThread["s8d"]?.relativeTime, expected)
        XCTAssertEqual(byThread["sNo"]?.relativeTime, "")
    }

    // MARK: - Identifier shape

    func testTaskIdIsServerIdColonThreadId() {
        let summary = makeSummary(
            serverId: "studio.lan",
            threadId: "t99",
            updatedAt: 1,
            hasActiveTurn: false
        )
        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [],
            pendingApprovals: []
        )
        XCTAssertEqual(result.first?.id, "studio.lan:t99")
        XCTAssertEqual(result.first?.serverId, "studio.lan")
        XCTAssertEqual(result.first?.threadId, "t99")
    }
}
