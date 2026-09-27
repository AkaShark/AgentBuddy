import SwiftUI

/// Collapsible plan progress card above the composer. Step state is shown by
/// icon shape (check / filled / empty) plus colour.
struct ConversationComposerPlanProgressView: View {
    let progress: AppPlanProgressSnapshot
    @State private var isExpanded = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var completedCount: Int {
        progress.plan.filter { $0.status == .completed }.count
    }

    private var currentStepText: String {
        guard let step = currentStep?.step.trimmingCharacters(in: .whitespacesAndNewlines),
              !step.isEmpty else {
            return progress.plan.isEmpty
                ? String(localized: "No plan task")
                : String(localized: "Plan complete")
        }
        return step
    }

    private var currentStep: AppPlanStep? {
        progress.plan.first(where: { $0.status == .inProgress })
            ?? progress.plan.first(where: { $0.status == .pending })
            ?? progress.plan.last(where: { $0.status == .completed })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: isExpanded ? BuddySpacing.xs : 0) {
            Button {
                withAnimation(reduceMotion ? nil : .snappy(duration: 0.18)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: BuddySpacing.xs) {
                    headerContent
                }
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isExpanded ? "Collapse plan progress" : "Expand plan progress")

            if isExpanded {
                expandedContent
                    .padding(.bottom, BuddySpacing.sm)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, BuddySpacing.md)
        .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
    }

    private var headerContent: some View {
        Group {
            Image(systemName: "list.bullet.clipboard")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.link)
                .accessibilityHidden(true)
            Text(isExpanded ? "Plan Progress" : "Plan")
                .buddyText(.label, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
            Text(verbatim: "\(completedCount)/\(progress.plan.count)")
                .buddyText(.caption, weight: .semibold)
                .monospacedDigit()
                .foregroundStyle(AgentBuddyTheme.textSecondary)

            if !isExpanded {
                Text(verbatim: currentStepText)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)
            } else {
                Spacer(minLength: 0)
            }

            Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityHidden(true)
        }
    }

    @ViewBuilder
    private var expandedContent: some View {
        if let explanation = progress.explanation?.trimmingCharacters(in: .whitespacesAndNewlines),
           !explanation.isEmpty {
            Text(verbatim: explanation)
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
        }

        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            ForEach(Array(progress.plan.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                    Image(systemName: iconName(for: step.status))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(iconColor(for: step.status))
                        .accessibilityLabel(Text(statusLabel(for: step.status)))
                    Text(verbatim: "\(index + 1).")
                        .buddyText(.caption, weight: .semibold)
                        .monospacedDigit()
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                    Text(verbatim: step.step)
                        .buddyText(.label, weight: step.status == .inProgress ? .semibold : .regular)
                        .foregroundStyle(step.status == .completed ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private func iconName(for status: AppPlanStepStatus) -> String {
        switch status {
        case .completed:
            return "checkmark.circle.fill"
        case .inProgress:
            return "circle.inset.filled"
        case .pending:
            return "circle"
        }
    }

    private func iconColor(for status: AppPlanStepStatus) -> Color {
        switch status {
        case .completed:
            return AgentBuddyTheme.success
        case .inProgress:
            return AgentBuddyTheme.warning
        case .pending:
            return AgentBuddyTheme.textSecondary
        }
    }

    private func statusLabel(for status: AppPlanStepStatus) -> LocalizedStringKey {
        switch status {
        case .completed: return "Done"
        case .inProgress: return "In progress"
        case .pending: return "Not started"
        }
    }
}
