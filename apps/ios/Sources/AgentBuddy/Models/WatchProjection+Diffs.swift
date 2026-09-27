import Foundation

extension WatchProjection {
    // MARK: - Diff projection

    /// Max number of per-file diffs we ship per task. Bounds the watch
    /// payload so a turn that touches dozens of files doesn't blow the
    /// WatchConnectivity application-context size cap.
    static let maxDiffFilesPerTask = 6
    /// Per-file diff text budget, in characters. Anything longer is
    /// tail-truncated with a "…" sentinel and `truncated = true` so the
    /// watch can render a hint instead of silently lying.
    static let maxDiffCharsPerFile = 1200

    /// Walk the hydrated conversation items and produce one `WatchFileDiff`
    /// per distinct file path — collapsing repeated edits to the most recent
    /// diff. Ordered most-recent-first, capped to `maxDiffFilesPerTask`,
    /// each diff truncated to `maxDiffCharsPerFile`.
    static func deriveDiffs(from items: [HydratedConversationItem]) -> [WatchFileDiff] {
        // Scan newest → oldest so the first time we see a path wins (most
        // recent edit for that file). Skip empty diffs — they carry no
        // information and would just waste a slot.
        var seenPaths = Set<String>()
        var diffs: [WatchFileDiff] = []
        for item in items.reversed() {
            guard case .fileChange(let data) = item.content else { continue }
            for change in data.changes {
                let path = change.path.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !path.isEmpty, !seenPaths.contains(path) else { continue }
                let rawDiff = change.diff
                guard !rawDiff.isEmpty else { continue }
                seenPaths.insert(path)
                let (text, truncated) = truncateDiff(rawDiff, max: maxDiffCharsPerFile)
                diffs.append(
                    WatchFileDiff(
                        path: path,
                        kind: change.kind,
                        additions: Int(change.additions),
                        deletions: Int(change.deletions),
                        diff: text,
                        truncated: truncated
                    )
                )
                if diffs.count >= maxDiffFilesPerTask { return diffs }
            }
        }
        return diffs
    }

    private static func truncateDiff(_ s: String, max: Int) -> (String, Bool) {
        if s.count <= max { return (s, false) }
        let head = String(s.prefix(max))
        // Drop the partial trailing line so the truncation marker sits on
        // its own row instead of fusing onto a half-rendered diff line.
        let lastNewline = head.lastIndex(of: "\n")
        let body = lastNewline.map { String(head[..<$0]) } ?? head
        return (body + "\n… (truncated)", true)
    }
}
