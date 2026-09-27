import SwiftUI

struct PresentedDiff: Identifiable {
    let id: String
    let title: String
    let diff: String?
    let stats: DiffStats
    let sections: [PresentedDiffSection]
}

struct PresentedDiffSection: Identifiable {
    let id: String
    let title: String
    let diff: String

    init(title: String, diff: String) {
        self.title = title
        self.diff = diff
        self.id = "\(title)|\(diff.hashValue)"
    }
}

struct DiffStats: Equatable {
    let additions: Int
    let deletions: Int

    var hasChanges: Bool {
        additions > 0 || deletions > 0
    }

    init(additions: Int, deletions: Int) {
        self.additions = additions
        self.deletions = deletions
    }

    /// Cheap stats-only parse — no per-line allocation.
    init(diff: String) {
        var adds = 0
        var dels = 0
        for line in diff.split(separator: "\n", omittingEmptySubsequences: false) {
            if line.hasPrefix("+"), !line.hasPrefix("+++") { adds += 1 }
            else if line.hasPrefix("-"), !line.hasPrefix("---") { dels += 1 }
        }
        self.additions = adds
        self.deletions = dels
    }
}

private struct DiffLine: Identifiable {
    enum Kind {
        case addition, deletion, hunk, context

        var foregroundColor: Color {
            switch self {
            case .addition: AgentBuddyTheme.success
            case .deletion: AgentBuddyTheme.danger
            case .hunk: AgentBuddyTheme.link
            case .context: AgentBuddyTheme.textPrimary
            }
        }

        var backgroundColor: Color {
            switch self {
            case .addition: AgentBuddyTheme.successSurface
            case .deletion: AgentBuddyTheme.dangerSurface
            case .hunk: AgentBuddyTheme.surfaceSoft
            case .context: AgentBuddyTheme.codeBackground
            }
        }
    }

    let id: Int
    let text: String
    let kind: Kind
}

func presentedDiffSections(from diff: String) -> [PresentedDiffSection] {
    let normalized = diff.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !normalized.isEmpty else { return [] }

    let lines = normalized.components(separatedBy: .newlines)
    let splitIndices = lines.enumerated().compactMap { index, line -> Int? in
        line.hasPrefix("diff --git ") ? index : nil
    }

    if !splitIndices.isEmpty {
        return splitIndices.enumerated().compactMap { offset, start in
            let end = offset + 1 < splitIndices.count ? splitIndices[offset + 1] : lines.count
            let chunk = Array(lines[start..<end]).joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !chunk.isEmpty else { return nil }
            return PresentedDiffSection(title: diffSectionTitle(from: chunk), diff: chunk)
        }
    }

    return [PresentedDiffSection(title: diffSectionTitle(from: normalized), diff: normalized)]
}

func mergePresentedDiffSections(_ sections: [PresentedDiffSection]) -> [PresentedDiffSection] {
    var orderedTitles: [String] = []
    var mergedByTitle: [String: String] = [:]
    var passthrough: [PresentedDiffSection] = []

    for section in sections {
        let normalizedTitle = section.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedTitle.isEmpty else {
            passthrough.append(section)
            continue
        }

        if let existing = mergedByTitle[normalizedTitle] {
            mergedByTitle[normalizedTitle] = existing + "\n\n" + section.diff
        } else {
            orderedTitles.append(normalizedTitle)
            mergedByTitle[normalizedTitle] = section.diff
        }
    }

    let merged = orderedTitles.compactMap { title -> PresentedDiffSection? in
        guard let diff = mergedByTitle[title] else { return nil }
        return PresentedDiffSection(title: title, diff: diff)
    }

    return merged + passthrough
}

private func diffSectionTitle(from diff: String) -> String {
    for line in diff.components(separatedBy: .newlines) {
        if line.hasPrefix("diff --git ") {
            let parts = line.split(separator: " ")
            if let candidate = parts.last {
                return stripDiffPathPrefix(String(candidate))
            }
        }
        if line.hasPrefix("+++ ") {
            let candidate = String(line.dropFirst(4))
            if candidate != "/dev/null" {
                return stripDiffPathPrefix(candidate)
            }
        }
        if line.hasPrefix("--- ") {
            let candidate = String(line.dropFirst(4))
            if candidate != "/dev/null" {
                return stripDiffPathPrefix(candidate)
            }
        }
    }
    return ""
}

private func stripDiffPathPrefix(_ path: String) -> String {
    if path.hasPrefix("a/") || path.hasPrefix("b/") {
        return String(path.dropFirst(2))
    }
    return path
}
