import SwiftUI

struct RateLimitBadgeView: View, Equatable {
    let label: String
    let percent: Int

    private var tint: Color {
        if percent <= 10 { return AgentBuddyTheme.danger }
        if percent <= 30 { return AgentBuddyTheme.warning }
        return AgentBuddyTheme.textMuted
    }

    var body: some View {
        HStack(spacing: 3) {
            Text(label)
                .font(AgentBuddyFont.monospaced(size: 9.5, weight: .semibold))
                .foregroundColor(AgentBuddyTheme.textSecondary)
            ContextBadgeView(percent: percent, tint: tint)
        }
    }
}
