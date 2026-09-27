import SwiftUI
import UIKit

extension SessionCanvasLine {
    // MARK: - Fork lineage affordances

    /// Compact rune that trails the title at every zoom level. Single chip,
    /// single number — `2/3` reads as "branch 2 of 3 in this lineage".
    @ViewBuilder
    func forkRune(lineage: ThreadLineage) -> some View {
        HStack(spacing: 3) {
            Image(systemName: "arrow.triangle.branch")
                .agentBuddyFont(size: 8, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.85))
            Text("\(lineage.branchIndex)/\(lineage.branchTotal)")
                .agentBuddyMonoFont(size: 9, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.accent)
        }
        .padding(.horizontal, 5)
        .padding(.vertical, 1)
        .overlay(
            Capsule()
                .stroke(AgentBuddyTheme.border.opacity(0.6), lineWidth: 1)
        )
        .clipShape(Capsule())
        .accessibilityLabel("Branch \(lineage.branchIndex) of \(lineage.branchTotal)")
    }

    /// Inline meta-line replacement for the old `fork` warning text. Carries
    /// the same numeric info as the rune but reads as a chip in line with
    /// the server/model spans at zoom 2+.
    @ViewBuilder
    func branchChip(lineage: ThreadLineage) -> some View {
        HStack(spacing: 3) {
            Image(systemName: "arrow.triangle.branch")
                .agentBuddyFont(size: 7, weight: .semibold)
            Text("branch \(lineage.branchIndex)/\(lineage.branchTotal)")
        }
        .foregroundStyle(AgentBuddyTheme.accent.opacity(0.85))
    }

    /// Zoom-4 lineage breadcrumb. Renders ancestors root → ... → parent so
    /// the user knows where in the fork tree they are. Self is rendered as
    /// the title beneath, so we don't repeat it here.
    @ViewBuilder
    var lineageBreadcrumb: some View {
        if let lineage = session.lineage, !lineage.ancestors.isEmpty {
            HStack(spacing: 0) {
                ForEach(Array(lineage.ancestors.enumerated()), id: \.offset) { idx, ancestor in
                    if idx > 0 {
                        Text(" › ")
                            .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.55))
                    }
                    Text(ancestor.title)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.85))
                }
                Text(" ›")
                    .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.55))
                Spacer(minLength: 0)
            }
            .agentBuddyMonoFont(size: 9, weight: .regular)
            .padding(.bottom, 2)
        }
    }

    /// Zoom-4 sibling pills. Each pill is a branch in the lineage; the one
    /// matching `session.key` is highlighted. The pills double as branch
    /// pickers when wired up by the host.
    @ViewBuilder
    var siblingPillsRow: some View {
        if let lineage = session.lineage, lineage.hasMultipleBranches {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(lineage.members, id: \.key) { member in
                        siblingPill(member: member, isCurrent: member.key == session.key)
                    }
                }
            }
            .padding(.top, 6)
        }
    }

    @ViewBuilder
    private func siblingPill(member: ThreadLineageMember, isCurrent: Bool) -> some View {
        HStack(spacing: 5) {
            Circle()
                .fill(isCurrent ? AgentBuddyTheme.accent : AgentBuddyTheme.textMuted.opacity(0.5))
                .frame(width: 5, height: 5)
            Text(member.title)
                .lineLimit(1)
                .truncationMode(.tail)
        }
        .agentBuddyFont(size: 10, weight: isCurrent ? .semibold : .regular)
        .foregroundStyle(isCurrent ? AgentBuddyTheme.accent : AgentBuddyTheme.textSecondary.opacity(0.85))
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(
            Capsule()
                .fill(isCurrent ? AgentBuddyTheme.accent.opacity(0.12) : AgentBuddyTheme.surface.opacity(0.6))
        )
        .overlay(
            Capsule()
                .stroke(isCurrent ? AgentBuddyTheme.accent.opacity(0.6) : AgentBuddyTheme.border.opacity(0.6), lineWidth: 1)
        )
    }
}
