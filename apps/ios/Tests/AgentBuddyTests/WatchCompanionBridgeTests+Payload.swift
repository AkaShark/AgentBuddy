import XCTest
@testable import AgentBuddy

extension WatchCompanionBridgeTests {

    // MARK: - currentPayload

    func testCurrentPayloadExposesPendingApprovalAndTasks() {
        let approval = PendingApproval(
            id: "approval-1",
            serverId: "macbook",
            kind: .command,
            threadId: "t1",
            turnId: nil,
            itemId: nil,
            command: "git push",
            path: nil,
            grantRoot: nil,
            cwd: nil,
            reason: nil
        )
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [makeSummary(
                serverId: "macbook",
                threadId: "t1",
                updatedAt: 100,
                hasActiveTurn: false
            )],
            pendingApprovals: [approval]
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let payload = bridge.currentPayload()

        XCTAssertEqual(payload.tasks.count, 1)
        XCTAssertEqual(payload.tasks.first?.status, .needsApproval)
        XCTAssertEqual(payload.pendingApproval?.id, "approval-1")
        XCTAssertEqual(payload.pendingApproval?.kind, .command)
        XCTAssertEqual(payload.pendingApproval?.command, "git push")

        // Theme is attached on every push so the watch can mirror the
        // user's selected iPhone palette.
        XCTAssertNotNil(payload.theme)
        XCTAssertTrue(payload.theme?.accent.hasPrefix("#") ?? false)
    }

    // MARK: - Home visibility parity

    func testCurrentPayloadExcludesHiddenThreads() {
        let visibleKey = ThreadKey(serverId: "macbook", threadId: "visible")
        let hiddenKey = ThreadKey(serverId: "macbook", threadId: "hidden")
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [
                makeSummary(serverId: "macbook", threadId: "visible", updatedAt: 200, hasActiveTurn: false),
                makeSummary(serverId: "macbook", threadId: "hidden", updatedAt: 100, hasActiveTurn: false),
            ]
        ))

        SavedThreadsStore.hide(PinnedThreadKey(threadKey: hiddenKey))
        defer { SavedThreadsStore.unhide(PinnedThreadKey(threadKey: hiddenKey)) }

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let payload = bridge.currentPayload()

        XCTAssertEqual(payload.tasks.map(\.threadId), [visibleKey.threadId])
    }

    func testCurrentPayloadIncludesHiddenTasks() {
        let visibleKey = ThreadKey(serverId: "macbook", threadId: "visible")
        let hiddenKey = ThreadKey(serverId: "macbook", threadId: "hidden")
        let otherHiddenKey = ThreadKey(serverId: "studio", threadId: "hidden2")
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook"), makeServer(id: "studio")],
            sessionSummaries: [
                makeSummary(serverId: "macbook", threadId: "visible", updatedAt: 300, hasActiveTurn: false, title: "stays"),
                makeSummary(serverId: "macbook", threadId: "hidden",  updatedAt: 200, hasActiveTurn: false, title: "tucked away"),
                makeSummary(serverId: "studio",  threadId: "hidden2", updatedAt: 100, hasActiveTurn: false, title: "also tucked"),
            ]
        ))

        SavedThreadsStore.hide(PinnedThreadKey(threadKey: hiddenKey))
        SavedThreadsStore.hide(PinnedThreadKey(threadKey: otherHiddenKey))
        defer {
            SavedThreadsStore.unhide(PinnedThreadKey(threadKey: hiddenKey))
            SavedThreadsStore.unhide(PinnedThreadKey(threadKey: otherHiddenKey))
        }

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let payload = bridge.currentPayload()

        // Visible slice excludes the hidden threads (existing behavior).
        XCTAssertEqual(payload.tasks.map(\.threadId), [visibleKey.threadId])

        // Hidden slice contains both hidden threads in some order.
        let hiddenIds = Set((payload.hiddenTasks ?? []).map(\.threadId))
        XCTAssertEqual(hiddenIds, [hiddenKey.threadId, otherHiddenKey.threadId])
    }

    func testCurrentPayloadOmitsHiddenTasksFieldWhenEmpty() {
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [
                makeSummary(serverId: "macbook", threadId: "visible", updatedAt: 100, hasActiveTurn: false),
            ]
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let payload = bridge.currentPayload()

        XCTAssertNil(payload.hiddenTasks, "no hidden threads → no hiddenTasks slice")
    }

    func testCurrentPayloadOrdersPinnedThreadsByPinOrder() {
        let pin1 = ThreadKey(serverId: "macbook", threadId: "alpha")
        let pin2 = ThreadKey(serverId: "macbook", threadId: "bravo")
        let other = ThreadKey(serverId: "macbook", threadId: "charlie")
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: [
                // Reverse-recency order so we know pin order is doing the sorting.
                makeSummary(serverId: "macbook", threadId: "alpha",   updatedAt: 100, hasActiveTurn: false),
                makeSummary(serverId: "macbook", threadId: "bravo",   updatedAt: 200, hasActiveTurn: false),
                makeSummary(serverId: "macbook", threadId: "charlie", updatedAt: 300, hasActiveTurn: false),
            ]
        ))

        // `add` prepends — pinning bravo then alpha yields pin order: alpha, bravo.
        SavedThreadsStore.add(PinnedThreadKey(threadKey: pin2))
        SavedThreadsStore.add(PinnedThreadKey(threadKey: pin1))
        defer {
            SavedThreadsStore.remove(PinnedThreadKey(threadKey: pin1))
            SavedThreadsStore.remove(PinnedThreadKey(threadKey: pin2))
        }

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let payload = bridge.currentPayload()

        // Only the two pinned threads show up (charlie is excluded because the
        // iPhone home rule is "pins only when any are pinned"). Order matches
        // pin order, not recency.
        XCTAssertEqual(payload.tasks.map(\.threadId), [pin1.threadId, pin2.threadId])
        XCTAssertFalse(payload.tasks.contains { $0.threadId == other.threadId })
    }

    func testInboundHomeHideAddsThreadToHiddenStore() async {
        let key = ThreadKey(serverId: "macbook", threadId: "tohide")
        let pinned = PinnedThreadKey(threadKey: key)
        // Ensure clean start.
        SavedThreadsStore.unhide(pinned)

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound([
            "kind": "home.hide",
            "serverId": key.serverId,
            "threadId": key.threadId,
        ])
        defer { SavedThreadsStore.unhide(pinned) }

        XCTAssertEqual(reply?["ok"] as? Bool, true)
        XCTAssertTrue(SavedThreadsStore.hiddenKeys().contains(pinned))
    }

    func testInboundHomeHideRejectsInvalidPayload() async {
        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let reply = await bridge.handleInbound([
            "kind": "home.hide",
            "threadId": "no-server-id",
        ])
        XCTAssertEqual(reply?["ok"] as? Bool, false)
    }

    func testCurrentPayloadIncludesResolvedThemeForDarkAppearance() {
        ThemeManager.shared.setAppearanceMode(.dark)
        AppModel.shared.applySnapshot(makeRecord(
            servers: [makeServer(id: "macbook")],
            sessionSummaries: []
        ))

        let bridge = WatchCompanionBridge(transport: StubWatchTransport())
        let theme = bridge.currentPayload().theme
        XCTAssertNotNil(theme)
        XCTAssertEqual(theme?.isDark, true)
        XCTAssertEqual(theme?.appearanceMode, .dark)
        XCTAssertEqual(theme?.accent, ThemeManager.shared.darkTheme.accent)
        XCTAssertEqual(theme?.backgroundTop, ThemeManager.shared.darkTheme.background)
    }
}
