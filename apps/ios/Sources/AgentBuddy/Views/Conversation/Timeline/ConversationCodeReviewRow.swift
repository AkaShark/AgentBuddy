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
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
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

    private var prioritySurface: Color {
        switch finding.priority {
        case 0?, 1?:
            return AgentBuddyTheme.dangerSurface
        case 2?:
            return AgentBuddyTheme.warningSurface
        default:
            return AgentBuddyTheme.surfaceSoft
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
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            HStack(alignment: .center, spacing: BuddySpacing.sm) {
                if let priorityLabel {
                    Text(verbatim: priorityLabel)
                        .buddyText(.caption, weight: .semibold)
                        .foregroundStyle(priorityTint)
                        .padding(.horizontal, BuddySpacing.xs)
                        .padding(.vertical, 3)
                        .background(prioritySurface, in: Capsule())
                }

                Text(finding.title)
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                Button(action: onDismiss) {
                    Text("Dismiss")
                        .timelineLinkAction()
                }
                .buttonStyle(.plain)
            }

            AgentBuddyMarkdownView(markdown: finding.body, style: .content, selectionEnabled: true)

            if let locationText, !locationText.isEmpty {
                Text(locationText)
                    .buddyText(.code)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
    }
}
