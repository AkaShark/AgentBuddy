import SwiftUI

/// "Thinking" status under the live turn. A soft shimmer runs only when
/// Reduce Motion is off; otherwise the text stays static.
struct TypingIndicator: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shimmerOffset: CGFloat = -1

    var body: some View {
        if reduceMotion {
            label
                .foregroundStyle(AgentBuddyTheme.textSecondary)
        } else {
            label
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            AgentBuddyTheme.textSecondary,
                            AgentBuddyTheme.textPrimary,
                            AgentBuddyTheme.textSecondary,
                        ],
                        startPoint: UnitPoint(x: shimmerOffset - 0.3, y: 0.5),
                        endPoint: UnitPoint(x: shimmerOffset + 0.3, y: 0.5)
                    )
                )
                .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false), value: shimmerOffset)
                .onAppear {
                    shimmerOffset = 2
                }
        }
    }

    private var label: some View {
        Text("Thinking")
            .buddyText(.label)
    }
}
