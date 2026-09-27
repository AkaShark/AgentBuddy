import SwiftUI

struct ConversationCodeReviewRow: View {
    let data: ConversationCodeReviewData
    @State private var dismissedFindingIndices: Set<Int> = []

    private var visibleFindings: [(index: Int, finding: ConversationCodeReviewFinding)] {
        data.findings.enumerated().compactMap { index, finding in
            dismissedFindingIndices.contains(index) ? nil : (index, finding)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(visibleFindings, id: \.index) { entry in
                ConversationCodeReviewFindingCard(
                    finding: entry.finding,
                    onDismiss: { dismissedFindingIndices.insert(entry.index) }
                )
            }
        }
    }
}

private struct ConversationCodeReviewFindingCard: View {
    let finding: ConversationCodeReviewFinding
    let onDismiss: () -> Void

    private var priorityLabel: String? {
        finding.priority.map { "P\($0)" }
    }

    private var priorityTint: Color {
        switch finding.priority {
        case 0?, 1?:
            return AgentBuddyTheme.danger
        case 2?:
            return AgentBuddyTheme.warning
        case 3?:
            return AgentBuddyTheme.textSecondary
        default:
            return AgentBuddyTheme.textSecondary
        }
    }

    private var locationText: String? {
        guard let location = finding.codeLocation else { return nil }
        guard let lineRange = location.lineRange else { return location.absoluteFilePath }
        if lineRange.start == lineRange.end {
            return "\(location.absoluteFilePath):\(lineRange.start)"
        }
        return "\(location.absoluteFilePath):\(lineRange.start)-\(lineRange.end)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 10) {
                if let priorityLabel {
                    Text(priorityLabel)
                        .agentBuddyFont(.caption2, weight: .bold)
                        .foregroundColor(priorityTint)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(priorityTint.opacity(0.12), in: Capsule())
                }

                Text(finding.title)
                    .agentBuddyFont(.headline, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Button("Dismiss", action: onDismiss)
                    .buttonStyle(.plain)
                    .agentBuddyFont(.callout, weight: .medium)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            }

            AgentBuddyMarkdownView(markdown: finding.body, style: .content, selectionEnabled: true)

            if let locationText, !locationText.isEmpty {
                Text(locationText)
                    .agentBuddyFont(.footnote)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(20)
        .background(AgentBuddyTheme.surface.opacity(0.72), in: RoundedRectangle(cornerRadius: 22))
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .stroke(AgentBuddyTheme.border.opacity(0.7), lineWidth: 1)
        )
    }
}
