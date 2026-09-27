import SwiftUI

/// Inline confirmation shown when a Plan-mode turn finishes: implement the plan
/// in Default mode, or stay in Plan mode.
struct PlanImplementationPromptView: View {
    let onImplement: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            HStack(spacing: BuddySpacing.xs) {
                Image(systemName: "list.bullet.clipboard.fill")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.link)
                    .accessibilityHidden(true)
                Text("Implement Plan")
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                Spacer(minLength: 0)
            }

            Text("Switch to Default mode and implement the plan?")
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: BuddySpacing.xs) {
                BuddyButton("Stay in Plan", kind: .secondary, action: onDismiss)
                BuddyButton("Implement", kind: .primary, action: onImplement)
            }
        }
        .buddyCard(.surface, radius: BuddyRadius.confirmCard, padding: BuddySpacing.md)
    }
}
