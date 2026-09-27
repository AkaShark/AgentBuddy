import XCTest
@testable import AgentBuddy

final class HomeTaskPresentationTests: XCTestCase {
    private func session(
        thread: String,
        active: Bool,
        ended: Bool = false,
        cwd: String = "/Users/me/Projects/AgentBuddy"
    ) -> HomeDashboardRecentSession {
        HomeDashboardRecentSession(
            key: ThreadKey(serverId: "s1", threadId: thread),
            serverId: "s1",
            serverDisplayName: "MacBook Pro",
            agentRuntimeKind: .codex,
            isLocal: false,
            sessionTitle: "Task \(thread)",
            preview: "",
            cwd: cwd,
            model: "gpt",
            agentLabel: nil,
            updatedAt: Date(),
            hasTurnActive: active,
            isResumed: true,
            isSubagent: false,
            isFork: false,
            forkedFromId: nil,
            lineage: nil,
            lastResponsePreview: nil,
            lastResponseTurnId: nil,
            lastUserMessage: nil,
            lastToolLabel: nil,
            stats: nil,
            tokenUsage: nil,
            goal: nil,
            recentToolLog: [],
            lastTurnStart: nil,
            lastTurnEnd: ended ? Date() : nil
        )
    }

    func testStatePriority() {
        let running = session(thread: "a", active: true)
        let idle = session(thread: "b", active: false)
        let done = session(thread: "c", active: false, ended: true)

        XCTAssertEqual(HomeTaskPresentation.state(for: running, approvalKeys: [], inputKeys: [], cancellingKeys: []), .running)
        XCTAssertEqual(HomeTaskPresentation.state(for: running, approvalKeys: ["s1/a"], inputKeys: [], cancellingKeys: []), .awaitingApproval)
        XCTAssertEqual(HomeTaskPresentation.state(for: running, approvalKeys: [], inputKeys: ["s1/a"], cancellingKeys: []), .awaitingInput)
        XCTAssertEqual(HomeTaskPresentation.state(for: running, approvalKeys: ["s1/a"], inputKeys: [], cancellingKeys: ["s1/a"]), .stopping)
        XCTAssertEqual(HomeTaskPresentation.state(for: idle, approvalKeys: [], inputKeys: [], cancellingKeys: ["s1/b"]), .idle)
        XCTAssertEqual(HomeTaskPresentation.state(for: done, approvalKeys: [], inputKeys: [], cancellingKeys: []), .completed)
    }

    func testItemsCountApprovalsPerThreadAndIgnoreElicitations() {
        func approval(_ id: String, thread: String, kind: ApprovalKind) -> PendingApproval {
            PendingApproval(id: id, serverId: "s1", kind: kind, threadId: thread, turnId: nil, itemId: nil,
                            command: nil, path: nil, grantRoot: nil, cwd: nil, reason: nil)
        }
        let items = HomeTaskPresentation.items(
            sessions: [session(thread: "a", active: true), session(thread: "b", active: true)],
            pendingApprovals: [
                approval("1", thread: "a", kind: .command),
                approval("2", thread: "a", kind: .fileChange),
                approval("3", thread: "b", kind: .mcpElicitation),
            ],
            pendingInputs: [],
            pinnedKeys: [SavedThreadsStore.PinnedKey(threadKey: ThreadKey(serverId: "s1", threadId: "b"))],
            cancellingKeys: []
        )
        XCTAssertEqual(items[0].pendingApprovalCount, 2)
        XCTAssertEqual(items[0].state, .awaitingApproval)
        XCTAssertEqual(items[1].pendingApprovalCount, 0)
        XCTAssertEqual(items[1].state, .running)
        XCTAssertFalse(items[0].isPinned)
        XCTAssertTrue(items[1].isPinned)
    }

    func testProjectNameUsesLastPathComponent() {
        XCTAssertEqual(HomeTaskPresentation.projectName(forCwd: "/Users/me/Projects/AgentBuddy"), "AgentBuddy")
        XCTAssertEqual(HomeTaskPresentation.projectName(forCwd: "/Users/me/Projects/AgentBuddy/"), "AgentBuddy")
        XCTAssertNil(HomeTaskPresentation.projectName(forCwd: "/"))
        XCTAssertNil(HomeTaskPresentation.projectName(forCwd: "  "))
    }
}
