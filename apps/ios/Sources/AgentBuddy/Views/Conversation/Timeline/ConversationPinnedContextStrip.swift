import SwiftUI

struct ConversationPinnedContextStrip: View {
    let items: [ConversationItem]
    @State private var todoExpanded = false
    @State private var selectedDiff: PresentedDiff?
    @State private var cachedCombinedPinnedDiff: PresentedDiff?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(items: [ConversationItem]) {
        self.items = items
        _cachedCombinedPinnedDiff = State(
            initialValue: Self.buildCombinedPinnedDiff(from: items)
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            if pinnedPlan != nil || cachedCombinedPinnedDiff != nil {
                if let plan = pinnedPlan, let diff = cachedCombinedPinnedDiff {
                    HStack(alignment: .top, spacing: BuddySpacing.xs) {
                        compactTodoAccordion(for: plan)
                            .layoutPriority(1)
                        diffIndicatorButton(for: diff)
                    }
                } else {
                    if let plan = pinnedPlan {
                        compactTodoAccordion(for: plan)
                    }

                    if let diff = cachedCombinedPinnedDiff {
                        diffIndicatorButton(for: diff)
                    }
                }
            }
        }
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.top, BuddySpacing.xs)
        .sheet(item: $selectedDiff) { presentedDiff in
            ConversationDiffDetailSheet(
                title: presentedDiff.title,
                diff: presentedDiff.diff ?? "",
                sections: presentedDiff.sections
            )
        }
        .onChange(of: pinnedDiffTaskKey, initial: false) { _, _ in
            cachedCombinedPinnedDiff = Self.buildCombinedPinnedDiff(from: items)
        }
    }

    private var pinnedPlan: ConversationItem? {
        items.last(where: {
            if case .todoList(let data) = $0.content {
                return !data.steps.isEmpty
            }
            return false
        })
    }

    private var pinnedDiffTaskKey: [Int] {
        items.map(\.renderDigest)
    }

    private static func buildCombinedPinnedDiff(from items: [ConversationItem]) -> PresentedDiff? {
        let rawSections = items.flatMap { item -> [PresentedDiffSection] in
            switch item.content {
            case .fileChange(let data):
                return data.changes.compactMap { change in
                    let diff = change.diff.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !diff.isEmpty else { return nil }
                    return PresentedDiffSection(
                        title: workspaceTitle(for: change.path),
                        diff: diff
                    )
                }
            case .turnDiff(let data):
                let diff = data.diff.trimmingCharacters(in: .whitespacesAndNewlines)
                return diff.isEmpty ? [] : presentedDiffSections(from: diff)
            default:
                return []
            }
        }

        let sections = mergePresentedDiffSections(rawSections)
        guard !sections.isEmpty else { return nil }
        let stats = sections.reduce(into: DiffStats(additions: 0, deletions: 0)) { partial, section in
            let sectionStats = DiffStats(diff: section.diff)
            partial = DiffStats(
                additions: partial.additions + sectionStats.additions,
                deletions: partial.deletions + sectionStats.deletions
            )
        }
        return PresentedDiff(
            id: "session-diff",
            title: "Session Diff",
            diff: nil,
            stats: stats,
            sections: sections
        )
    }

    @ViewBuilder
    private func compactTodoAccordion(for item: ConversationItem) -> some View {
        if case .todoList(let data) = item.content {
            let completed = data.completedCount
            let total = data.steps.count
            let summary: LocalizedStringKey = completed == 0
                ? "To do list created with \(total) tasks"
                : "\(completed) out of \(total) tasks completed"

            VStack(alignment: .leading, spacing: 0) {
                Button {
                    withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.2)) {
                        todoExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: BuddySpacing.xs) {
                        Image(systemName: completed == total && total > 0 ? "checkmark.circle.fill" : "checklist")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(completed == total && total > 0 ? AgentBuddyTheme.success : AgentBuddyTheme.link)
                            .accessibilityHidden(true)
                        Text(summary)
                            .buddyText(.label, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .rotationEffect(.degrees(todoExpanded ? 180 : 0))
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, BuddySpacing.sm)
                    .frame(minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint(Text(todoExpanded ? "Collapses the task list" : "Shows every step"))

                if todoExpanded {
                    VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                        ForEach(Array(data.steps.enumerated()), id: \.offset) { _, step in
                            HStack(alignment: .top, spacing: BuddySpacing.xs) {
                                compactTodoStatusView(for: step.status)
                                    .padding(.top, 2)
                                AgentBuddyMarkdownView(
                                    markdown: step.step,
                                    style: .content,
                                    bodySize: 14,
                                    codeSize: 13
                                )
                                    .strikethrough(step.status == .completed, color: AgentBuddyTheme.textSecondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .padding(.horizontal, BuddySpacing.sm)
                    .padding(.bottom, BuddySpacing.sm)
                    .transition(.sectionReveal)
                }
            }
            .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
        }
    }

    @ViewBuilder
    private func compactTodoStatusView(for status: HydratedPlanStepStatus) -> some View {
        switch status {
        case .pending:
            Image(systemName: "circle")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityLabel(Text("Not started"))
        case .inProgress:
            ProgressView()
                .controlSize(.mini)
                .tint(AgentBuddyTheme.warning)
                .frame(width: 12, height: 12)
                .accessibilityLabel(Text("In progress"))
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.success)
                .accessibilityLabel(Text("Done"))
        }
    }

    private func diffIndicatorButton(for presented: PresentedDiff) -> some View {
        Button {
            selectedDiff = presented
        } label: {
            DiffIndicatorLabel(
                additions: presented.stats.additions,
                deletions: presented.stats.deletions
            )
        }
        .buttonStyle(.plain)
        .frame(minHeight: BuddySize.minHitTarget)
        .contentShape(Rectangle())
        .fixedSize(horizontal: true, vertical: false)
        .accessibilityLabel(Text("Session changes: \(presented.stats.additions) added, \(presented.stats.deletions) removed"))
    }

}
