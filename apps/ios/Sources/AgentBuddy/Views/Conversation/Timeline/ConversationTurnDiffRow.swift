import SwiftUI

struct ConversationTurnDiffRow: View {
    let data: ConversationTurnDiffData
    @State private var presented: PresentedDiff?

    var body: some View {
        Button {
            presented = PresentedDiff(
                id: "turn-diff",
                title: "Turn Diff",
                diff: data.diff,
                stats: DiffStats(additions: data.additions, deletions: data.deletions),
                sections: presentedDiffSections(from: data.diff)
            )
        } label: {
            DiffIndicatorLabel(additions: data.additions, deletions: data.deletions)
        }
        .buttonStyle(.plain)
        .sheet(item: $presented) { sheet in
            ConversationDiffDetailSheet(
                title: sheet.title,
                diff: sheet.diff ?? "",
                sections: sheet.sections
            )
        }
    }
}

struct DiffIndicatorLabel: View {
    private let stats: DiffStats

    init(diff: String) {
        self.stats = DiffStats(diff: diff)
    }

    init(additions: Int, deletions: Int) {
        self.stats = DiffStats(additions: additions, deletions: deletions)
    }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "arrow.left.arrow.right")
                .agentBuddyFont(size: 11, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.accent)

            if stats.hasChanges {
                HStack(spacing: 6) {
                    Text("+\(stats.additions)")
                        .agentBuddyFont(.caption2, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.success)
                    Text("-\(stats.deletions)")
                        .agentBuddyFont(.caption2, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.danger)
                }
            } else {
                Text("Diff")
                    .agentBuddyFont(.caption2, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(AgentBuddyTheme.surface.opacity(0.72), in: Capsule())
        .fixedSize(horizontal: true, vertical: false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
    }

    private var accessibilityLabel: String {
        if stats.hasChanges {
            return "Show diff details. \(stats.additions) additions, \(stats.deletions) deletions."
        }
        return "Show diff details."
    }
}
