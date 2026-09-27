import SwiftUI

struct PlanImplementationPromptView: View {
    let onImplement: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "list.bullet.clipboard.fill")
                    .foregroundColor(AgentBuddyTheme.accent)
                Text("Implement Plan")
                    .agentBuddyFont(.caption, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                Spacer()
            }

            Text("Switch to Default mode and implement the plan?")
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.textSecondary)

            HStack(spacing: 8) {
                Button {
                    onImplement()
                } label: {
                    Text("Implement")
                        .agentBuddyFont(.caption2, weight: .semibold)
                        .foregroundColor(Color.black)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(AgentBuddyTheme.accent)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)

                Button {
                    onDismiss()
                } label: {
                    Text("Stay in Plan")
                        .agentBuddyFont(.caption2, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(AgentBuddyTheme.surface.opacity(0.8))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .modifier(GlassRectModifier(cornerRadius: 14))
    }
}
