import SwiftUI

/// Compact "active task" summary shown above the composer while a task list
/// is running. Status is carried by the icon and the progress text, not colour.
struct ConversationComposerActiveTaskRowView: View {
    let summary: ConversationActiveTaskSummary

    var body: some View {
        HStack(spacing: BuddySpacing.sm) {
            Image(systemName: "checklist")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(AgentBuddyTheme.warning)
                .frame(width: 24)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: BuddySpacing.xs) {
                    Text(summary.title)
                        .buddyText(.label, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .lineLimit(1)

                    Text(summary.progressLabel)
                        .buddyText(.caption, weight: .semibold)
                        .monospacedDigit()
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }

                Text(summary.detail)
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.vertical, BuddySpacing.xs)
        .frame(maxWidth: .infinity, minHeight: BuddySize.minHitTarget, alignment: .leading)
        .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
        .accessibilityElement(children: .combine)
    }
}
