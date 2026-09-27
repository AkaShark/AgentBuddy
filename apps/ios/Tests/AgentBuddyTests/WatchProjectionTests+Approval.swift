import XCTest
@testable import AgentBuddy

extension WatchProjectionTests {

    // MARK: - 7. approval(_:)

    func testApprovalProducesRightCommandTargetDiffPerKind() {
        let cmd = makePendingApproval(
            id: "1",
            threadId: nil,
            kind: .command,
            command: "ls -la",
            cwd: "/tmp",
            reason: "list dir"
        )
        let cmdResult = WatchProjection.approval(cmd)
        XCTAssertEqual(cmdResult.kind, .command)
        XCTAssertEqual(cmdResult.command, "ls -la")
        XCTAssertEqual(cmdResult.target, "/tmp")
        XCTAssertEqual(cmdResult.diffSummary, "list dir")

        let fc = makePendingApproval(
            id: "2",
            threadId: nil,
            kind: .fileChange,
            path: "/etc/foo.conf",
            grantRoot: "/etc"
        )
        let fcResult = WatchProjection.approval(fc)
        XCTAssertEqual(fcResult.kind, .fileChange)
        XCTAssertEqual(fcResult.command, "edit_file")
        XCTAssertEqual(fcResult.target, "/etc/foo.conf")
        XCTAssertEqual(fcResult.diffSummary, "/etc")

        let perm = makePendingApproval(
            id: "3",
            threadId: nil,
            kind: .permissions,
            reason: "grant network"
        )
        let permResult = WatchProjection.approval(perm)
        XCTAssertEqual(permResult.kind, .permissions)
        XCTAssertEqual(permResult.command, "permissions")
        XCTAssertEqual(permResult.target, "grant network")
        XCTAssertEqual(permResult.diffSummary, "")

        let mcp = makePendingApproval(
            id: "4",
            threadId: nil,
            kind: .mcpElicitation,
            reason: "give me input"
        )
        let mcpResult = WatchProjection.approval(mcp)
        XCTAssertEqual(mcpResult.kind, .mcpElicitation)
        XCTAssertEqual(mcpResult.command, "mcp")
        XCTAssertEqual(mcpResult.target, "give me input")
        XCTAssertEqual(mcpResult.diffSummary, "")
    }

    func testApprovalUsesFallbacksWhenOptionalFieldsMissing() {
        // command kind without command -> falls back to "command"; without
        // reason -> diffSummary is "".
        let bareCmd = makePendingApproval(id: "1", threadId: nil, kind: .command)
        let cmdResult = WatchProjection.approval(bareCmd)
        XCTAssertEqual(cmdResult.command, "command")
        XCTAssertEqual(cmdResult.target, "")
        XCTAssertEqual(cmdResult.diffSummary, "")

        // fileChange without path -> "file"; without grantRoot -> "".
        let bareFc = makePendingApproval(id: "2", threadId: nil, kind: .fileChange)
        let fcResult = WatchProjection.approval(bareFc)
        XCTAssertEqual(fcResult.command, "edit_file")
        XCTAssertEqual(fcResult.target, "file")
        XCTAssertEqual(fcResult.diffSummary, "")

        // permissions without reason -> "grant access"
        let barePerm = makePendingApproval(id: "3", threadId: nil, kind: .permissions)
        let permResult = WatchProjection.approval(barePerm)
        XCTAssertEqual(permResult.target, "grant access")

        // mcpElicitation without reason -> "input requested"
        let bareMcp = makePendingApproval(id: "4", threadId: nil, kind: .mcpElicitation)
        let mcpResult = WatchProjection.approval(bareMcp)
        XCTAssertEqual(mcpResult.target, "input requested")
    }
}
