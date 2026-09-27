import SwiftUI

struct ConversationTurnDiffRow: View {
    let data: ConversationTurnDiffData
    @State private var presented: PresentedDiff?

    var body: some View {
        Button {
            presented = PresentedDiff(
                id: "turn-diff",
                title: String(localized: "Turn Diff"),
                diff: data.diff,
                stats: DiffStats(additions: data.additions, deletions: data.deletions),
                sections: presentedDiffSections(from: data.diff)
            )
        } label: {
            DiffIndicatorLabel(additions: data.additions, deletions: data.deletions)
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
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
        HStack(spacing: BuddySpacing.xs) {
            Image(systemName: "arrow.left.arrow.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.textSecondary)

            if stats.hasChanges {
                HStack(spacing: 6) {
                    Text(verbatim: "+\(stats.additions)")
                        .buddyText(.caption, weight: .semibold)
                        .monospacedDigit()
                        .foregroundStyle(AgentBuddyTheme.success)
                    Text(verbatim: "−\(stats.deletions)")
                        .buddyText(.caption, weight: .semibold)
                        .monospacedDigit()
                        .foregroundStyle(AgentBuddyTheme.danger)
                }
            } else {
                Text("Diff")
                    .buddyText(.caption, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
        }
        .padding(.horizontal, BuddySpacing.sm)
        .frame(minHeight: BuddySize.compactPill)
        .background(AgentBuddyTheme.surface, in: Capsule())
        .overlay {
            Capsule().strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
        }
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
