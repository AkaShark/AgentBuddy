import SwiftUI

/// Rate-limit window badge ("5h 72%"): window label plus the remaining share.
struct RateLimitBadgeView: View, Equatable {
    let label: String
    let percent: Int

    private var tint: Color {
        if percent <= 10 { return AgentBuddyTheme.danger }
        if percent <= 30 { return AgentBuddyTheme.warning }
        return AgentBuddyTheme.textSecondary
    }

    var body: some View {
        HStack(spacing: BuddySpacing.xxs) {
            Text(verbatim: label)
                .buddyText(.caption)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            ContextBadgeView(percent: percent, tint: tint)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(label) limit: \(percent)% left"))
    }
}
