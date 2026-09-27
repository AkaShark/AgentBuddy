import XCTest
@testable import AgentBuddy

extension WatchProjectionTests {

    // MARK: - 2. Subtitle precedence + compact() truncation

    func testSubtitlePrefersPendingApprovalOverEverythingElse() {
        let summary = makeSummary(
            serverId: "srv",
            threadId: "t1",
            updatedAt: 1,
            hasActiveTurn: false,
            lastToolLabel: "edit_file src/foo.swift",
            lastResponsePreview: "ignored response",
            lastUserMessage: "ignored user",
            preview: "ignored preview"
        )
        let approval = makePendingApproval(
            id: "ap",
            threadId: "t1",
            kind: .command,
            command: "git push"
        )

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [],
            pendingApprovals: [approval]
        )

        XCTAssertEqual(result.first?.subtitle, "awaiting approval: git push")
        XCTAssertEqual(result.first?.pendingApprovalId, "ap")
    }

    func testSubtitlePrefersAssistantResponseOverToolLabelThenUserThenPreviewThenNil() {
        // Assistant response wins over tool label — the watch prioritizes
        // what the AI said over which tool is mid-run. The tool label is
        // exposed separately as `lastTool` for a secondary chip.
        let withBoth = makeSummary(
            serverId: "srv",
            threadId: "both",
            updatedAt: 0,
            hasActiveTurn: false,
            lastToolLabel: "ran tests",
            lastResponsePreview: "all tests pass",
            lastUserMessage: "ignored",
            preview: "ignored"
        )
        // response present, no tool -> response wins, no tool chip
        let withResponseOnly = makeSummary(
            serverId: "srv",
            threadId: "resp",
            updatedAt: 1,
            hasActiveTurn: false,
            lastToolLabel: nil,
            lastResponsePreview: "assistant said hi",
            lastUserMessage: "ignored",
            preview: "ignored"
        )
        // tool only -> tool is the subtitle, no separate chip needed
        let withToolOnly = makeSummary(
            serverId: "srv",
            threadId: "tool",
            updatedAt: 2,
            hasActiveTurn: false,
            lastToolLabel: "ran tests",
            lastResponsePreview: nil,
            lastUserMessage: "ignored",
            preview: "ignored"
        )
        // no tool, no response -> user message wins
        let withUser = makeSummary(
            serverId: "srv",
            threadId: "user",
            updatedAt: 3,
            hasActiveTurn: false,
            lastToolLabel: nil,
            lastResponsePreview: nil,
            lastUserMessage: "user said hi",
            preview: "ignored"
        )
        // all empty -> preview field
        let withPreview = makeSummary(
            serverId: "srv",
            threadId: "prev",
            updatedAt: 4,
            hasActiveTurn: false,
            lastToolLabel: nil,
            lastResponsePreview: nil,
            lastUserMessage: nil,
            preview: "preview line"
        )
        // nothing at all -> nil
        let withNothing = makeSummary(
            serverId: "srv",
            threadId: "none",
            updatedAt: 5,
            hasActiveTurn: false,
            lastToolLabel: nil,
            lastResponsePreview: nil,
            lastUserMessage: nil,
            preview: ""
        )

        let result = WatchProjection.tasks(
            summaries: [withBoth, withResponseOnly, withToolOnly, withUser, withPreview, withNothing],
            threads: [],
            pendingApprovals: []
        )

        let byThread = Dictionary(uniqueKeysWithValues: result.map { ($0.threadId, $0) })
        // both: assistant text in subtitle, tool label in lastTool chip
        XCTAssertEqual(byThread["both"]?.subtitle, "all tests pass")
        XCTAssertEqual(byThread["both"]?.lastTool, "ran tests")
        // response-only: subtitle is the response, no chip
        XCTAssertEqual(byThread["resp"]?.subtitle, "assistant said hi")
        XCTAssertNil(byThread["resp"]?.lastTool)
        // tool-only: subtitle is the tool, no separate chip
        XCTAssertEqual(byThread["tool"]?.subtitle, "ran tests")
        XCTAssertNil(byThread["tool"]?.lastTool)
        XCTAssertEqual(byThread["user"]?.subtitle, "user said hi")
        XCTAssertEqual(byThread["prev"]?.subtitle, "preview line")
        XCTAssertNil(byThread["none"]?.subtitle)
    }

    func testSubtitleEmptyStringsAreTreatedAsAbsent() {
        // Empty strings should be skipped and the next non-empty source used.
        let summary = makeSummary(
            serverId: "srv",
            threadId: "t",
            updatedAt: 1,
            hasActiveTurn: false,
            lastToolLabel: "",
            lastResponsePreview: "",
            lastUserMessage: "",
            preview: "fallback"
        )

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [],
            pendingApprovals: []
        )
        XCTAssertEqual(result.first?.subtitle, "fallback")
    }

    func testSubtitleAppliesCompactTruncationAtTheRightMaxWidths() {
        // tool label (when it IS the subtitle) uses max=48
        let toolText = String(repeating: "a", count: 60)
        let toolSummary = makeSummary(
            serverId: "srv",
            threadId: "tool",
            updatedAt: 1,
            hasActiveTurn: false,
            lastToolLabel: toolText
        )
        // assistant response uses max=100 (it's the prime real estate now)
        let respText = String(repeating: "b", count: 150)
        let respSummary = makeSummary(
            serverId: "srv",
            threadId: "resp",
            updatedAt: 2,
            hasActiveTurn: false,
            lastResponsePreview: respText
        )
        // tool label exposed as `lastTool` chip uses max=36
        let bothSummary = makeSummary(
            serverId: "srv",
            threadId: "both",
            updatedAt: 3,
            hasActiveTurn: false,
            lastToolLabel: String(repeating: "c", count: 50),
            lastResponsePreview: "short reply"
        )

        let result = WatchProjection.tasks(
            summaries: [toolSummary, respSummary, bothSummary],
            threads: [],
            pendingApprovals: []
        )
        let byThread = Dictionary(uniqueKeysWithValues: result.map { ($0.threadId, $0) })

        // compact pattern: take prefix(max - 1) and append "…"
        XCTAssertEqual(byThread["tool"]?.subtitle, String(repeating: "a", count: 47) + "…")
        XCTAssertEqual(byThread["tool"]?.subtitle?.count, 48)
        XCTAssertEqual(byThread["resp"]?.subtitle, String(repeating: "b", count: 99) + "…")
        XCTAssertEqual(byThread["resp"]?.subtitle?.count, 100)
        XCTAssertEqual(byThread["both"]?.lastTool, String(repeating: "c", count: 35) + "…")
        XCTAssertEqual(byThread["both"]?.lastTool?.count, 36)
    }

    func testApprovalSubtitleTruncatesCommandAt32() {
        let longCmd = String(repeating: "x", count: 50)
        let summary = makeSummary(
            serverId: "srv",
            threadId: "t",
            updatedAt: 1,
            hasActiveTurn: false
        )
        let approval = makePendingApproval(
            id: "ap",
            threadId: "t",
            kind: .command,
            command: longCmd
        )

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [],
            pendingApprovals: [approval]
        )
        // approvalLabel(.command) compacts at max=32, then prefixed with "awaiting approval: "
        let truncated = String(repeating: "x", count: 31) + "…"
        XCTAssertEqual(result.first?.subtitle, "awaiting approval: \(truncated)")
    }

    func testTitleFallsBackThroughLastUserMessageThenPreviewThenUntitled() {
        let withTitle = makeSummary(
            serverId: "srv", threadId: "a",
            updatedAt: 1, hasActiveTurn: false,
            title: "Real title",
            lastUserMessage: "ignored",
            preview: "ignored"
        )
        let withUser = makeSummary(
            serverId: "srv", threadId: "b",
            updatedAt: 2, hasActiveTurn: false,
            title: "",
            lastUserMessage: "from user",
            preview: "ignored"
        )
        let withPreview = makeSummary(
            serverId: "srv", threadId: "c",
            updatedAt: 3, hasActiveTurn: false,
            title: "",
            lastUserMessage: nil,
            preview: "preview text"
        )
        let withNothing = makeSummary(
            serverId: "srv", threadId: "d",
            updatedAt: 4, hasActiveTurn: false,
            title: "",
            lastUserMessage: nil,
            preview: ""
        )

        let result = WatchProjection.tasks(
            summaries: [withTitle, withUser, withPreview, withNothing],
            threads: [],
            pendingApprovals: []
        )
        let byThread = Dictionary(uniqueKeysWithValues: result.map { ($0.threadId, $0) })
        XCTAssertEqual(byThread["a"]?.title, "Real title")
        XCTAssertEqual(byThread["b"]?.title, "from user")
        XCTAssertEqual(byThread["c"]?.title, "preview text")
        XCTAssertEqual(byThread["d"]?.title, "untitled task")
    }
}
