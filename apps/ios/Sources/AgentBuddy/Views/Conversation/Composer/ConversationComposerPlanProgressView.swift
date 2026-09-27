import SwiftUI

struct ConversationComposerPlanProgressView: View {
    let progress: AppPlanProgressSnapshot
    @State private var isExpanded = true

    private var completedCount: Int {
        progress.plan.filter { $0.status == .completed }.count
    }

    private var currentStepText: String {
        guard let step = currentStep?.step.trimmingCharacters(in: .whitespacesAndNewlines),
              !step.isEmpty else {
            return progress.plan.isEmpty ? "No plan task" : "Plan complete"
        }
        return step
    }

    private var currentStep: AppPlanStep? {
        progress.plan.first(where: { $0.status == .inProgress })
            ?? progress.plan.first(where: { $0.status == .pending })
            ?? progress.plan.last(where: { $0.status == .completed })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: isExpanded ? 8 : 0) {
            Button {
                withAnimation(.snappy(duration: 0.18)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: 8) {
                    headerContent
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isExpanded ? "Collapse plan progress" : "Expand plan progress")

            if isExpanded {
                expandedContent
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(AgentBuddyTheme.codeBackground.opacity(0.92))
        )
    }

    private var headerContent: some View {
        Group {
            Image(systemName: "list.bullet.clipboard")
                .agentBuddyFont(size: 12, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.accent)
            Text(isExpanded ? "Plan Progress" : "Plan")
                .agentBuddyFont(.caption, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
            Text("\(completedCount)/\(progress.plan.count)")
                .agentBuddyMonoFont(size: 11, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textSecondary)

            if !isExpanded {
                Text(currentStepText)
                    .agentBuddyFont(.caption)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(1)
            } else {
                Spacer(minLength: 0)
            }

            Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                .agentBuddyFont(size: 10, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textMuted)
        }
    }

    @ViewBuilder
    private var expandedContent: some View {
        if let explanation = progress.explanation?.trimmingCharacters(in: .whitespacesAndNewlines),
           !explanation.isEmpty {
            Text(explanation)
                .agentBuddyFont(.caption)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
        }

        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(progress.plan.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: iconName(for: step.status))
                        .agentBuddyFont(size: 11, weight: .semibold)
                        .foregroundStyle(iconColor(for: step.status))
                        .padding(.top, 2)
                    Text("\(index + 1).")
                        .agentBuddyMonoFont(size: 11, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                        .padding(.top, 1)
                    Text(step.step)
                        .agentBuddyFont(.caption)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
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
            return "circle.fill"
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
            return AgentBuddyTheme.textMuted
        }
    }
}
