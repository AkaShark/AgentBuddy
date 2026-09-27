import SwiftUI

struct ConversationUserInputResponseRow: View {
    let data: ConversationUserInputResponseData

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            ForEach(Array(data.questions.enumerated()), id: \.element.id) { _, question in
                HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.success)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(question.header ?? question.question)
                            .buddyText(.caption, weight: .medium)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                        Text(question.answer)
                            .buddyText(.label, weight: .regular)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .textSelection(.enabled)
                    }
                }
            }
        }
        .padding(.vertical, BuddySpacing.xxs)
    }
}

struct ConversationDividerRow: View {
    let kind: ConversationDividerKind
    let isLiveTurn: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: BuddySpacing.sm) {
            Capsule()
                .fill(AgentBuddyTheme.border)
                .frame(minWidth: 16, maxHeight: 1)
            dividerContent
                .layoutPriority(1)
            Capsule()
                .fill(AgentBuddyTheme.border)
                .frame(minWidth: 16, maxHeight: 1)
        }
        .padding(.vertical, BuddySpacing.xs)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
    }

    @ViewBuilder
    private var dividerContent: some View {
        switch kind {
        case .contextCompaction:
            HStack(spacing: BuddySpacing.xs) {
                if effectiveContextCompactionComplete {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.success)
                } else if reduceMotion {
                    Image(systemName: "circle.dotted")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.warning)
                } else {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(AgentBuddyTheme.warning)
                }

                Text(verbatim: title)
                    .buddyText(.caption, weight: .medium)
                    .foregroundStyle(
                        effectiveContextCompactionComplete ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.warning
                    )
                    .lineLimit(1)
            }
        default:
            Text(verbatim: title)
                .buddyText(.caption, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .lineLimit(1)
        }
    }

    private var title: String {
        switch kind {
        case .contextCompaction:
            return effectiveContextCompactionComplete
                ? String(localized: "Context compacted")
                : String(localized: "Compacting context")
        case .modelRerouted(let fromModel, let toModel, let reason):
            let base = fromModel.map { "\($0) -> \(toModel)" } ?? String(localized: "Routed to \(toModel)")
            if let reason, !reason.isEmpty {
                return "\(base) · \(reason)"
            }
            return base
        case .reviewEntered(let review):
            return review.isEmpty ? String(localized: "Entered review") : String(localized: "Entered review: \(review)")
        case .reviewExited(let review):
            return review.isEmpty ? String(localized: "Exited review") : String(localized: "Exited review: \(review)")
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
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                Image(systemName: iconName)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(accent)
                    .accessibilityHidden(true)
                Text(verbatim: title)
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
            }
            if !content.isEmpty {
                AgentBuddyMarkdownView(
                    markdown: content,
                    style: .system
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.soft, radius: BuddyRadius.detailCard, padding: BuddySpacing.md)
    }

    var body: some View { bodyView }
}
