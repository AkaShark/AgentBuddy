import SwiftUI

/// Host push design §9: a host without `push.v1` cannot report completions;
/// say so instead of silently keeping the app alive.
struct UnsupportedHostPushHintView: View {
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "bell.slash")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AgentBuddyTheme.warning)
            Text("This host doesn't support completion notifications yet. Update the AgentBuddy desktop app to enable them.")
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .frame(width: 22, height: 22)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Close"))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .modifier(GlassRectModifier(cornerRadius: 14))
    }
}
