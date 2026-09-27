import SwiftUI

struct ConversationComposerActiveTaskRowView: View {
    let summary: ConversationActiveTaskSummary

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "checklist")
                .agentBuddyFont(size: 11, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.warning)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(summary.title)
                        .agentBuddyFont(.caption, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.textPrimary)

                    Text(summary.progressLabel)
                        .agentBuddyMonoFont(size: 10, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.warning)
                }

                Text(summary.detail)
                    .agentBuddyFont(.caption2)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AgentBuddyTheme.surface.opacity(0.72))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}
