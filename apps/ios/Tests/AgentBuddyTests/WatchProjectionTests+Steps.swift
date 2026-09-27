import XCTest
@testable import AgentBuddy

extension WatchProjectionTests {

    // MARK: - 4. deriveSteps + status mapping

    func testDeriveStepsKeepsLastFiveOfTwelveAndMapsEachStatus() {
        // 12 items total. Mix kinds so deriveSteps yields steps for all 12,
        // but only the last 5 are returned.
        var items: [HydratedConversationItem] = []
        for i in 0..<12 {
            let cmd = makeCommandItem(
                id: "cmd-\(i)",
                command: "step\(i)",
                status: .completed
            )
            items.append(cmd)
        }
        // Replace the final 5 with a status-mapping mix so we can verify the
        // mapping rules per state. Order in items: indices 7..11 are last-5.
        items[7]  = makeCommandItem(id: "i7", command: "completed", status: .completed)
        items[8]  = makeCommandItem(id: "i8", command: "failed",    status: .failed)
        items[9]  = makeCommandItem(id: "i9", command: "declined",  status: .declined)
        items[10] = makeCommandItem(id: "i10", command: "active",   status: .inProgress)
        items[11] = makeCommandItem(id: "i11", command: "pending",  status: .pending)

        let thread = makeThread(serverId: "srv", threadId: "t", items: items)
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 100, hasActiveTurn: false)

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        let steps = result.first?.steps ?? []
        XCTAssertEqual(steps.count, 5)
        XCTAssertEqual(steps.map(\.arg), ["completed", "failed", "declined", "active", "pending"])
        XCTAssertEqual(steps.map(\.state), [.done, .done, .done, .active, .pending])
    }

    func testDeriveStepsHandlesUnknownStatusAsPending() {
        let thread = makeThread(
            serverId: "srv",
            threadId: "t",
            items: [makeCommandItem(id: "u", command: "unk", status: .unknown)]
        )
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)
        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        XCTAssertEqual(result.first?.steps.first?.state, .pending)
    }

    func testDeriveStepsCoversAllSupportedItemKinds() {
        let webSearchInProgress = makeWebSearchItem(id: "w1", query: "swift", isInProgress: true)
        let webSearchDone = makeWebSearchItem(id: "w2", query: "rust", isInProgress: false)
        let mcp = makeMcpItem(id: "m1", tool: "summarize", contentSummary: "did the thing", status: .inProgress)
        let dyn = makeDynamicItem(id: "d1", tool: "lookup", contentSummary: "found it", status: .completed)
        let fileChange = makeFileChangeItem(
            id: "f1",
            path: "src/foo.swift",
            kind: "edit",
            status: .completed
        )

        let thread = makeThread(
            serverId: "srv",
            threadId: "t",
            items: [webSearchInProgress, webSearchDone, mcp, dyn, fileChange]
        )
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)
        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        let steps = result.first?.steps ?? []
        XCTAssertEqual(steps.count, 5)
        XCTAssertEqual(steps[0].tool, "web_search")
        XCTAssertEqual(steps[0].state, .active)
        XCTAssertEqual(steps[1].tool, "web_search")
        XCTAssertEqual(steps[1].state, .done)
        XCTAssertEqual(steps[2].tool, "summarize")
        XCTAssertEqual(steps[2].state, .active)
        XCTAssertEqual(steps[3].tool, "lookup")
        XCTAssertEqual(steps[3].state, .done)
        XCTAssertEqual(steps[4].tool, "edit_file")
        XCTAssertEqual(steps[4].state, .done)
    }

    func testDeriveStepsReturnsEmptyWhenThreadIsAbsent() {
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)
        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [],
            pendingApprovals: []
        )
        XCTAssertEqual(result.first?.steps, [])
    }

    // MARK: - 5. mapFileChangeKind

    func testFileChangeKindMaps() {
        let cases: [(String, String)] = [
            ("add",     "create_file"),
            ("create",  "create_file"),
            ("CREATED", "create_file"),
            ("delete",  "delete_file"),
            ("remove",  "delete_file"),
            ("edit",    "edit_file"),
            ("modify",  "edit_file"),
            ("",        "edit_file")
        ]

        for (kind, expectedTool) in cases {
            let thread = makeThread(
                serverId: "srv", threadId: "t",
                items: [makeFileChangeItem(id: "f", path: "/p", kind: kind, status: .completed)]
            )
            let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)
            let result = WatchProjection.tasks(
                summaries: [summary],
                threads: [thread],
                pendingApprovals: []
            )
            XCTAssertEqual(result.first?.steps.first?.tool, expectedTool, "kind=\(kind)")
        }
    }
}
