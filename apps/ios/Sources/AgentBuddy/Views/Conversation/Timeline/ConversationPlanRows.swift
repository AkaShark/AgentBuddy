import SwiftUI

struct ConversationTodoListRow: View {
    let data: ConversationTodoListData
    private let bodySize: CGFloat = BuddyTextStyle.label.size
    private let codeSize: CGFloat = BuddyTextStyle.code.size
    @State private var expanded = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: toggleExpanded) {
                HStack(spacing: BuddySpacing.sm) {
                    Image(systemName: headerIconName)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(headerTint)
                        .frame(width: BuddySize.icon, height: BuddySize.icon)
                        .accessibilityHidden(true)
                    Text("To Do")
                        .buddyText(.label, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                    Text(verbatim: summaryText)
                        .buddyText(.caption, weight: .medium)
                        .foregroundStyle(progressTint)
                        .lineLimit(1)
                    Spacer(minLength: BuddySpacing.xs)
                    TimelineDisclosureChevron(expanded: expanded)
                }
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.horizontal, BuddySpacing.md)
            .padding(.vertical, BuddySpacing.xxs)

            if expanded {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                        ForEach(Array(data.steps.enumerated()), id: \.offset) { index, step in
                            HStack(alignment: .top, spacing: BuddySpacing.xs) {
                                todoStatusView(for: step.status)
                                    .padding(.top, 3)
                                Text("\(index + 1).")
                                    .buddyText(.label)
                                    .monospacedDigit()
                                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                                AgentBuddyMarkdownView(
                                    markdown: step.step,
                                    style: .content,
                                    bodySize: bodySize,
                                    codeSize: codeSize
                                )
                                    .strikethrough(step.status == .completed, color: AgentBuddyTheme.textSecondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .padding(.horizontal, BuddySpacing.md)
                    .padding(.bottom, BuddySpacing.md)
                }
                .frame(maxHeight: 160)
                .mask {
                    VStack(spacing: 0) {
                        Rectangle()
                        LinearGradient(colors: [Color.primary, Color.primary.opacity(0)], startPoint: .top, endPoint: .bottom)
                            .frame(height: 18)
                    }
                }
                .transition(.sectionReveal)
            }
        }
        .timelineDetailCard()
    }

    private var completedCount: Int {
        data.completedCount
    }

    private var hasInProgressStep: Bool {
        data.steps.contains { $0.status == .inProgress }
    }

    private var headerIconName: String {
        if data.isComplete { return "checkmark.circle.fill" }
        if hasInProgressStep { return "checklist.checked" }
        return "checklist"
    }

    private var headerTint: Color {
        if data.isComplete { return AgentBuddyTheme.success }
        if hasInProgressStep { return AgentBuddyTheme.warning }
        return AgentBuddyTheme.textSecondary
    }

    private var summaryText: String {
        data.steps.count == 1
            ? String(localized: "\(completedCount) out of 1 task completed")
            : String(localized: "\(completedCount) out of \(data.steps.count) tasks completed")
    }

    private var progressTint: Color {
        data.isComplete ? AgentBuddyTheme.success : (hasInProgressStep ? AgentBuddyTheme.warning : AgentBuddyTheme.textSecondary)
    }

    private func toggleExpanded() {
        withAnimation(.easeInOut(duration: 0.2)) {
            expanded.toggle()
        }
    }

    @ViewBuilder
    private func todoStatusView(for status: HydratedPlanStepStatus) -> some View {
        switch status {
        case .pending:
            Image(systemName: "circle")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityLabel(Text("Pending"))
        case .inProgress:
            if reduceMotion {
                Image(systemName: "circle.dotted")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.warning)
                    .accessibilityLabel(Text("In progress"))
            } else {
                ProgressView()
                    .controlSize(.mini)
                    .tint(AgentBuddyTheme.warning)
                    .frame(width: 14, height: 14)
                    .accessibilityLabel(Text("In progress"))
            }
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.success)
                .accessibilityLabel(Text("Completed"))
        }
    }
}

struct ConversationProposedPlanRow: View {
    let data: ConversationProposedPlanData

    private var trimmedContent: String? {
        let trimmed = data.content.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var body: some View {
        if let trimmedContent {
            VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                HStack(spacing: BuddySpacing.xs) {
                    Image(systemName: "list.bullet.rectangle.portrait")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)
                    Text("Plan")
                        .buddyText(.label, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                }

                AgentBuddyMarkdownView(
                    markdown: trimmedContent,
                    style: .system
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: BuddySpacing.md)
        }
    }
}
