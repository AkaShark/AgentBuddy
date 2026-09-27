import SwiftUI

struct ConversationPinnedContextStrip: View {
    let items: [ConversationItem]
    @State private var todoExpanded = false
    @State private var selectedDiff: PresentedDiff?
    @State private var cachedCombinedPinnedDiff: PresentedDiff?

    init(items: [ConversationItem]) {
        self.items = items
        _cachedCombinedPinnedDiff = State(
            initialValue: Self.buildCombinedPinnedDiff(from: items)
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if pinnedPlan != nil || cachedCombinedPinnedDiff != nil {
                if let plan = pinnedPlan, let diff = cachedCombinedPinnedDiff {
                    HStack(alignment: .top, spacing: 10) {
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
        .padding(.horizontal, 12)
        .padding(.top, 8)
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
            let summary: String = {
                if completed == 0 {
                    return "To do list created with \(total) tasks"
                }
                return "\(completed) out of \(total) tasks completed"
            }()

            VStack(alignment: .leading, spacing: 0) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        todoExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: completed == total && total > 0 ? "checkmark.circle.fill" : "checklist")
                            .agentBuddyFont(size: 11, weight: .semibold)
                            .foregroundColor(completed == total && total > 0 ? AgentBuddyTheme.success : AgentBuddyTheme.accent)
                        Text(summary)
                            .agentBuddyFont(.caption, weight: .semibold)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                            .lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: "chevron.down")
                            .agentBuddyFont(size: 11, weight: .medium)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                            .rotationEffect(.degrees(todoExpanded ? 180 : 0))
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)

                if todoExpanded {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(data.steps.enumerated()), id: \.offset) { _, step in
                            HStack(alignment: .top, spacing: 8) {
                                compactTodoStatusView(for: step.status)
                                    .padding(.top, 2)
                                AgentBuddyMarkdownView(
                                    markdown: step.step,
                                    style: .content,
                                    bodySize: 12,
                                    codeSize: 11
                                )
                                    .strikethrough(step.status == .completed, color: AgentBuddyTheme.textMuted)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 10)
                    .transition(.sectionReveal)
                }
            }
        }
    }

    @ViewBuilder
    private func compactTodoStatusView(for status: HydratedPlanStepStatus) -> some View {
        switch status {
        case .pending:
            Image(systemName: "circle")
                .agentBuddyFont(size: 10, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.textMuted)
        case .inProgress:
            ProgressView()
                .controlSize(.mini)
                .tint(AgentBuddyTheme.warning)
                .frame(width: 10, height: 10)
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .agentBuddyFont(size: 10, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.success)
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
        .fixedSize(horizontal: true, vertical: false)
    }

}
