import XCTest
@testable import AgentBuddy

final class HomeTaskPresentationTests: XCTestCase {
    private func session(
        thread: String,
        active: Bool,
        ended: Bool = false,
        server: String = "s1",
        cwd: String = "/Users/me/Projects/AgentBuddy",
        updatedAt: Date = Date()
    ) -> HomeDashboardRecentSession {
        HomeDashboardRecentSession(
            key: ThreadKey(serverId: server, threadId: thread),
            serverId: server,
            serverDisplayName: "MacBook Pro",
            agentRuntimeKind: .codex,
            isLocal: false,
            sessionTitle: "Task \(thread)",
            preview: "",
            cwd: cwd,
            model: "gpt",
            agentLabel: nil,
            updatedAt: updatedAt,
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

    // MARK: - Projects tab

    private let projectCwd = "/Users/me/Projects/AgentBuddy"

    /// Same id form Rust's `project_id_for` produces ("server::cwd").
    private func project(server: String = "s1", cwd: String) -> AppProject {
        AppProject(id: "\(server)::\(cwd)", serverId: server, cwd: cwd, lastUsedAtMs: nil)
    }

    private func summary(
        _ thread: String,
        server: String = "s1",
        cwd: String,
        parent: String? = nil,
        updatedAt: Int64 = 0
    ) -> AppSessionSummary {
        AppSessionSummary(
            key: ThreadKey(serverId: server, threadId: thread), agentRuntimeKind: "codex",
            serverDisplayName: "MacBook Pro", serverHost: "mac.local", title: "Task \(thread)", preview: "",
            cwd: cwd, model: "gpt", modelProvider: "openai", parentThreadId: parent, forkedFromId: nil,
            agentNickname: nil, agentRole: nil, agentDisplayLabel: nil, agentStatus: .unknown, updatedAt: updatedAt,
            hasActiveTurn: false, isResumed: false, isSubagent: parent != nil, isFork: false, lastResponsePreview: nil,
            lastResponseTurnId: nil, lastUserMessage: nil, lastToolLabel: nil, recentToolLog: [],
            lastTurnStartMs: nil, lastTurnEndMs: nil, stats: nil, tokenUsage: nil, goal: nil
        )
    }

    func testProjectFixtureIdsMatchRust() {
        XCTAssertEqual(projectIdFor(serverId: "s1", cwd: projectCwd + "/"), project(cwd: projectCwd).id)
    }

    func testProjectRecentSessionsAreTheNewestThreeOfThatProject() {
        let now = Date()
        let sessions = [
            session(thread: "old", active: false, updatedAt: now.addingTimeInterval(-400)),
            session(thread: "newest", active: false, updatedAt: now),
            session(thread: "other-folder", active: false, cwd: "/Users/me/Projects/Other", updatedAt: now.addingTimeInterval(10)),
            session(thread: "other-host", active: false, server: "s2", updatedAt: now.addingTimeInterval(10)),
            // Rust canonicalises the folder, so a trailing slash is the same project.
            session(thread: "second", active: true, cwd: projectCwd + "/", updatedAt: now.addingTimeInterval(-100)),
            session(thread: "third", active: false, updatedAt: now.addingTimeInterval(-200)),
        ]
        let target = project(cwd: projectCwd)

        let recent = ProjectSummary.recentSessions(for: target, in: sessions)
        XCTAssertEqual(recent.map(\.key.threadId), ["newest", "second", "third"])

        // Same matching as the task count, so "See all" (shown while the
        // count exceeds the rows) agrees with the list.
        let summary = ProjectSummary.build(projects: [target], sessions: sessions, servers: [])
        XCTAssertEqual(summary.first?.taskCount, 4)
        XCTAssertEqual(summary.first?.runningCount, 1)
        XCTAssertGreaterThan(summary.first?.taskCount ?? 0, recent.count)
    }

    func testProjectRecentSessionsSkipTasksHiddenFromHome() {
        let now = Date()
        let sessions = [
            session(thread: "hidden", active: false, updatedAt: now),
            session(thread: "shown", active: false, updatedAt: now.addingTimeInterval(-60)),
        ]
        let target = project(cwd: projectCwd)
        let hidden = PinnedThreadKey(serverId: "s1", threadId: "hidden")
        let recent = ProjectSummary.recentSessions(for: target, in: sessions, hiddenKeys: [hidden])
        XCTAssertEqual(recent.map(\.key.threadId), ["shown"])

        // Everything hidden: no rows, but the count still has tasks, so the
        // card shows only the "See all" link rather than "No tasks yet".
        let allHidden = ProjectSummary.recentSessions(
            for: target,
            in: sessions,
            hiddenKeys: [hidden, PinnedThreadKey(serverId: "s1", threadId: "shown")]
        )
        XCTAssertTrue(allHidden.isEmpty)
        XCTAssertEqual(ProjectSummary.build(projects: [target], sessions: sessions, servers: []).first?.taskCount, 2)
    }

    func testProjectRecentSessionsIsEmptyWithoutTasks() {
        let sessions = [session(thread: "a", active: false, cwd: "/Users/me/Projects/Other")]
        XCTAssertTrue(ProjectSummary.recentSessions(for: project(cwd: projectCwd), in: sessions).isEmpty)
    }

    func testCurrentProjectIsTheSelectionElseTheMostRecent() {
        let summaries = ProjectSummary.build(
            projects: [project(cwd: "/a/Newest"), project(cwd: "/a/Older"), project(server: "offline", cwd: "/b/Kept")],
            sessions: [],
            servers: []
        )

        XCTAssertEqual(ProjectSummary.current(in: summaries, selectedId: nil)?.name, "Newest")
        XCTAssertEqual(ProjectSummary.current(in: summaries, selectedId: "offline::/b/Kept")?.name, "Kept")
        XCTAssertEqual(ProjectSummary.current(in: summaries, selectedId: "gone::/c")?.name, "Newest")
        XCTAssertNil(ProjectSummary.current(in: [], selectedId: "offline::/b/Kept"))
    }

    func testProjectScopeKeepsOnlyThatProjectsTasks() {
        let scope = SessionsProjectScope(project(cwd: projectCwd))
        let kept = scope.filter([
            summary("a", cwd: projectCwd),
            summary("b", cwd: projectCwd + "/"),
            summary("c", cwd: projectCwd + "/shared"),
            summary("d", server: "s2", cwd: projectCwd),
        ])
        XCTAssertEqual(kept.map(\.key.threadId), ["a", "b"])
    }

    @MainActor
    func testProjectScopeKeepsSubAgentsAsTheFullListShowsThem() {
        let sessions = [
            summary("parent", cwd: projectCwd, updatedAt: 40),
            summary("child", cwd: projectCwd, parent: "parent", updatedAt: 30),
            summary("outside", cwd: "/Users/me/Projects/Other", updatedAt: 20),
            summary("child-of-outside", cwd: projectCwd, parent: "outside", updatedAt: 10),
        ]
        let derived = SessionsDerivation.build(
            sessions: sessions,
            projectScope: SessionsProjectScope(project(cwd: projectCwd)),
            selectedServerFilterId: nil,
            showOnlyForks: false,
            selectedRuntimeKind: nil,
            workspaceSortMode: .mostRecent,
            searchQuery: "",
            frozenMostRecentOrder: nil
        )

        XCTAssertEqual(Set(derived.allThreads.map(\.key.threadId)), ["parent", "child", "child-of-outside"])
        // Lineage still spans every task, so a sub-agent keeps its parent link.
        XCTAssertEqual(derived.parentByKey[ThreadKey(serverId: "s1", threadId: "child-of-outside")]?.key.threadId, "outside")
        let roots = derived.workspaceSections.flatMap(\.groups).flatMap(\.treeRoots)
        XCTAssertEqual(roots.first { $0.thread.key.threadId == "parent" }?.children.map(\.thread.key.threadId), ["child"])
    }

    // MARK: - Project selection rules

    @MainActor
    func testRefreshKeepsAProjectWhoseHostIsOffline() {
        let offline = project(server: "offline", cwd: "/b/Kept")
        let kept = HomeDashboardModel.reconciledProject(
            offline,
            savedId: nil,
            scopedServerId: nil, // the refresh cleared the offline host's scope
            projects: [project(cwd: "/a/Newest"), offline],
            knownServerIds: ["s1", "offline"]
        )
        XCTAssertEqual(kept, offline)
    }

    @MainActor
    func testRefreshKeepsAFreshFolderOnlyWhileItsHostIsKnown() {
        let fresh = project(cwd: "/a/Fresh")
        XCTAssertEqual(
            HomeDashboardModel.reconciledProject(fresh, savedId: nil, scopedServerId: nil, projects: [], knownServerIds: ["s1"]),
            fresh
        )
        XCTAssertNil(
            HomeDashboardModel.reconciledProject(fresh, savedId: nil, scopedServerId: nil, projects: [], knownServerIds: [])
        )
    }

    @MainActor
    func testSavedProjectIsRestoredOnlyWhenItAgreesWithTheHostScope() {
        let saved = project(cwd: "/a/Saved")
        func restored(scope: String?) -> AppProject? {
            HomeDashboardModel.reconciledProject(
                nil, savedId: saved.id, scopedServerId: scope, projects: [saved], knownServerIds: ["s1", "s2"]
            )
        }
        XCTAssertEqual(restored(scope: nil), saved)
        XCTAssertEqual(restored(scope: "s1"), saved)
        XCTAssertNil(restored(scope: "s2"))
    }

    @MainActor
    func testOnlyAnExplicitHostChangeMovesTheProject() {
        let current = project(cwd: "/a/Current")
        let other = project(server: "s2", cwd: "/b/Other")
        let projects = [current, other]
        XCTAssertEqual(HomeDashboardModel.alignedProject(current, toServer: nil, projects: projects), current)
        XCTAssertEqual(HomeDashboardModel.alignedProject(current, toServer: "s1", projects: projects), current)
        XCTAssertEqual(HomeDashboardModel.alignedProject(current, toServer: "s2", projects: projects), other)
        XCTAssertNil(HomeDashboardModel.alignedProject(current, toServer: "s3", projects: projects))
    }
}
