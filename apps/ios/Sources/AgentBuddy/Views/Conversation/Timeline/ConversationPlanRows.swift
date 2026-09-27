import SwiftUI

struct ConversationTodoListRow: View {
    let data: ConversationTodoListData
    private let bodySize: CGFloat = 13
    private let codeSize: CGFloat = 12
    @State private var expanded = true

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: toggleExpanded) {
                HStack(spacing: 8) {
                    Image(systemName: headerIconName)
                        .agentBuddyFont(size: 12, weight: .semibold)
                        .foregroundColor(headerTint)
                    Text("To Do")
                        .agentBuddyFont(.caption, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                    Text(summaryText)
                        .agentBuddyFont(.caption2, weight: .semibold)
                        .foregroundColor(progressTint)
                    Spacer(minLength: 8)
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .agentBuddyFont(size: 11, weight: .medium)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                }
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)

            if expanded {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(Array(data.steps.enumerated()), id: \.offset) { index, step in
                            HStack(alignment: .top, spacing: 8) {
                                todoStatusView(for: step.status)
                                    .padding(.top, 2)
                                Text("\(index + 1).")
                                    .agentBuddyFont(.caption, weight: .semibold)
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                                    .padding(.top, 1)
                                AgentBuddyMarkdownView(
                                    markdown: step.step,
                                    style: .content,
                                    bodySize: bodySize,
                                    codeSize: codeSize
                                )
                                    .strikethrough(step.status == .completed, color: AgentBuddyTheme.textMuted)
                                    .opacity(step.status == .completed ? 0.78 : 1.0)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .padding(10)
                }
                .frame(maxHeight: 160)
                .background(AgentBuddyTheme.surface.opacity(0.45))
                .mask {
                    VStack(spacing: 0) {
                        Rectangle().fill(.black)
                        LinearGradient(colors: [.black, .clear], startPoint: .top, endPoint: .bottom)
                            .frame(height: 18)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .padding(.horizontal, 12)
                .padding(.bottom, 10)
                .transition(.sectionReveal)
            }
        }
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
        return AgentBuddyTheme.accent
    }

    private var summaryText: String {
        "\(completedCount) out of \(data.steps.count) task\(data.steps.count == 1 ? "" : "s") completed"
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
                .agentBuddyFont(size: 11, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.textMuted)
        case .inProgress:
            ProgressView()
                .controlSize(.mini)
                .tint(AgentBuddyTheme.warning)
                .frame(width: 11, height: 11)
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .agentBuddyFont(size: 11, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.success)
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
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "list.bullet.rectangle.portrait.fill")
                        .agentBuddyFont(size: 12, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.accent)
                    Text("Plan")
                        .agentBuddyFont(.caption, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                }

                AgentBuddyMarkdownView(
                    markdown: trimmedContent,
                    style: .system
                )
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
    }
}
