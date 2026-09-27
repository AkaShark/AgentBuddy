import XCTest
@testable import AgentBuddy

extension WatchProjectionTests {

    // MARK: - 9. deriveDiffs

    func testDeriveDiffsCollapsesSamePathKeepingMostRecentEdit() {
        // Same file edited twice — the later edit should win, and the
        // resulting task should expose exactly one diff entry.
        let earlier = makeFileChangeItem(
            id: "f1",
            path: "src/foo.swift",
            kind: "modify",
            status: .completed,
            diff: "@@ -1,1 +1,1 @@\n-old\n+older",
            additions: 1,
            deletions: 1
        )
        let later = makeFileChangeItem(
            id: "f2",
            path: "src/foo.swift",
            kind: "modify",
            status: .completed,
            diff: "@@ -1,1 +1,1 @@\n-older\n+latest",
            additions: 2,
            deletions: 1
        )
        let thread = makeThread(serverId: "srv", threadId: "t", items: [earlier, later])
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        let diffs = result.first?.diffs ?? []
        XCTAssertEqual(diffs.count, 1)
        XCTAssertEqual(diffs.first?.path, "src/foo.swift")
        XCTAssertEqual(diffs.first?.additions, 2)
        XCTAssertEqual(diffs.first?.deletions, 1)
        XCTAssertEqual(diffs.first?.diff, "@@ -1,1 +1,1 @@\n-older\n+latest")
        XCTAssertEqual(diffs.first?.truncated, false)
    }

    func testDeriveDiffsCapsAtMaxFilesPerTaskNewestFirst() {
        // Build N+2 distinct file edits. The projection should keep
        // `maxDiffFilesPerTask` of them, newest-first (i.e. iterate items
        // in reverse), and drop the oldest pair.
        let cap = WatchProjection.maxDiffFilesPerTask
        let items: [HydratedConversationItem] = (0..<(cap + 2)).map { i in
            makeFileChangeItem(
                id: "f\(i)",
                path: "src/file_\(i).swift",
                kind: "modify",
                status: .completed,
                diff: "@@ -1,1 +1,1 @@\n-a\n+b",
                additions: 1,
                deletions: 1
            )
        }
        let thread = makeThread(serverId: "srv", threadId: "t", items: items)
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        let diffs = result.first?.diffs ?? []
        XCTAssertEqual(diffs.count, cap)
        // Newest-first: items.last is `file_{cap+1}.swift`.
        let expected = (0..<cap).map { "src/file_\(cap + 1 - $0).swift" }
        XCTAssertEqual(diffs.map(\.path), expected)
    }

    func testDeriveDiffsTruncatesLargeDiffAndMarksTruncated() {
        // 30 lines of "+aaaa…" each, longer than `maxDiffCharsPerFile`.
        // The projection should keep the head, drop the partial trailing
        // line, append a truncation marker, and set `truncated = true`.
        let cap = WatchProjection.maxDiffCharsPerFile
        let bigLine = "+" + String(repeating: "a", count: 80)
        let diffText = (0..<60).map { _ in bigLine }.joined(separator: "\n")
        XCTAssertGreaterThan(diffText.count, cap)

        let item = makeFileChangeItem(
            id: "f1",
            path: "src/huge.swift",
            kind: "modify",
            status: .completed,
            diff: diffText,
            additions: 60,
            deletions: 0
        )
        let thread = makeThread(serverId: "srv", threadId: "t", items: [item])
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        let projected = result.first?.diffs?.first
        XCTAssertNotNil(projected)
        XCTAssertEqual(projected?.truncated, true)
        XCTAssertTrue(projected?.diff.hasSuffix("\n… (truncated)") == true)
        // Header line plus the trimmed body must be shorter than original.
        XCTAssertLessThan(projected?.diff.count ?? .max, diffText.count)
    }

    func testDeriveDiffsSkipsEmptyDiffStrings() {
        // A file change with no diff text carries no useful info on the
        // watch — skip it instead of taking up a slot.
        let empty = makeFileChangeItem(
            id: "f1",
            path: "src/empty.swift",
            kind: "modify",
            status: .completed,
            diff: "",
            additions: 0,
            deletions: 0
        )
        let real = makeFileChangeItem(
            id: "f2",
            path: "src/real.swift",
            kind: "modify",
            status: .completed,
            diff: "@@ -1,1 +1,1 @@\n-x\n+y",
            additions: 1,
            deletions: 1
        )
        let thread = makeThread(serverId: "srv", threadId: "t", items: [empty, real])
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)

        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        XCTAssertEqual(result.first?.diffs?.map(\.path), ["src/real.swift"])
    }

    func testDeriveDiffsIsNilWhenThreadHasNoFileChanges() {
        // No file-change items at all -> the projection should leave the
        // `diffs` field nil so older watch builds (and the JSON encoder)
        // can omit it entirely.
        let thread = makeThread(
            serverId: "srv",
            threadId: "t",
            items: [makeUserItem(id: "u", text: "hi")]
        )
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)
        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        XCTAssertNil(result.first?.diffs)
    }

    func testDeriveDiffsExpandsMultiFileChangeItem() {
        // A single fileChange item can carry multiple file entries (e.g. a
        // patch that touches several files in one apply). All of them
        // should land in `diffs`, ordered as they appear in the entry list.
        let item = makeFileChangeItemMulti(
            id: "f1",
            status: .completed,
            entries: [
                HydratedFileChangeEntryData(
                    path: "src/a.swift",
                    kind: "modify",
                    diff: "@@ -1,1 +1,1 @@\n-1\n+2",
                    additions: 1,
                    deletions: 1
                ),
                HydratedFileChangeEntryData(
                    path: "src/b.swift",
                    kind: "add",
                    diff: "@@ -0,0 +1,1 @@\n+new",
                    additions: 1,
                    deletions: 0
                ),
            ]
        )
        let thread = makeThread(serverId: "srv", threadId: "t", items: [item])
        let summary = makeSummary(serverId: "srv", threadId: "t", updatedAt: 1, hasActiveTurn: false)
        let result = WatchProjection.tasks(
            summaries: [summary],
            threads: [thread],
            pendingApprovals: []
        )
        XCTAssertEqual(result.first?.diffs?.map(\.path), ["src/a.swift", "src/b.swift"])
        XCTAssertEqual(result.first?.diffs?.map(\.kind), ["modify", "add"])
    }
}
