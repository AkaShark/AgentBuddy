import SwiftUI

struct ConversationUserInputResponseRow: View {
    let data: ConversationUserInputResponseData

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(data.questions.enumerated()), id: \.element.id) { _, question in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Image(systemName: "checkmark.circle.fill")
                        .agentBuddyFont(size: 10, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(question.header ?? question.question)
                            .agentBuddyFont(.caption, weight: .semibold)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                        Text(question.answer)
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                            .textSelection(.enabled)
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }
}

struct ConversationDividerRow: View {
    let kind: ConversationDividerKind
    let isLiveTurn: Bool

    var body: some View {
        HStack(spacing: 10) {
            Capsule()
                .fill(AgentBuddyTheme.border)
                .frame(minWidth: 16, maxHeight: 1)
            dividerContent
                .layoutPriority(1)
            Capsule()
                .fill(AgentBuddyTheme.border)
                .frame(minWidth: 16, maxHeight: 1)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
    }

    @ViewBuilder
    private var dividerContent: some View {
        switch kind {
        case .contextCompaction:
            HStack(spacing: 6) {
                if effectiveContextCompactionComplete {
                    Image(systemName: "checkmark.circle.fill")
                        .agentBuddyFont(size: 10, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.success)
                } else {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(AgentBuddyTheme.warning)
                }

                Text(title)
                    .agentBuddyFont(.caption2, weight: .semibold)
                    .foregroundColor(
                        effectiveContextCompactionComplete ? AgentBuddyTheme.textMuted : AgentBuddyTheme.warning
                    )
                    .lineLimit(1)
            }
        default:
            Text(title)
                .agentBuddyFont(.caption2, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.textMuted)
                .lineLimit(1)
        }
    }

    private var title: String {
        switch kind {
        case .contextCompaction:
            return effectiveContextCompactionComplete ? "Context compacted" : "Compacting context"
        case .modelRerouted(let fromModel, let toModel, let reason):
            let base = fromModel.map { "\($0) -> \(toModel)" } ?? "Routed to \(toModel)"
            if let reason, !reason.isEmpty {
                return "\(base) · \(reason)"
            }
            return base
        case .reviewEntered(let review):
            return review.isEmpty ? "Entered review" : "Entered review: \(review)"
        case .reviewExited(let review):
            return review.isEmpty ? "Exited review" : "Exited review: \(review)"
        case .workedFor(let duration):
            return duration
        case .generic(let title, let detail):
            if let detail, !detail.isEmpty {
                return "\(title): \(detail)"
            }
            return title
        }
    }

    private var effectiveContextCompactionComplete: Bool {
        guard case .contextCompaction(let isComplete) = kind else { return true }
        return isComplete && !isLiveTurn
    }
}

struct ConversationSystemCardRow: View {
    let title: String
    let content: String
    let accent: Color
    let iconName: String

    var bodyView: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: iconName)
                    .agentBuddyFont(size: 11, weight: .semibold)
                    .foregroundColor(accent)
                Text(title.uppercased())
                    .agentBuddyFont(.caption2, weight: .bold)
                    .foregroundColor(accent)
            }
            if !content.isEmpty {
                AgentBuddyMarkdownView(
                    markdown: content,
                    style: .system
                )
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    var body: some View { bodyView }
}
