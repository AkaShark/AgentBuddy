import SwiftUI

/// Remaining-capacity meter: a short bar plus the percentage as text, so the
/// value never depends on colour alone.
struct ContextBadgeView: View, Equatable {
    let percent: Int
    let tint: Color

    private var clamped: Int { min(max(percent, 0), 100) }

    var body: some View {
        HStack(spacing: BuddySpacing.xxs) {
            Capsule()
                .fill(AgentBuddyTheme.surfaceSoft)
                .frame(width: 22, height: 6)
                .overlay(alignment: .leading) {
                    Capsule()
                        .fill(tint)
                        .frame(width: 22 * CGFloat(clamped) / 100.0, height: 6)
                }
                .accessibilityHidden(true)

            Text(verbatim: "\(clamped)%")
                .buddyText(.caption, weight: .semibold)
                .monospacedDigit()
                .foregroundStyle(tint)
        }
        .fixedSize()
    }
}
