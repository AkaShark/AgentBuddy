import SwiftUI

struct TypingIndicator: View {
    @State private var shimmerOffset: CGFloat = -1

    var body: some View {
        Text("Thinking")
            .agentBuddyFont(.body, weight: .medium)
            .foregroundStyle(
                LinearGradient(
                    colors: [
                        AgentBuddyTheme.textSecondary.opacity(0.4),
                        AgentBuddyTheme.accent,
                        AgentBuddyTheme.textSecondary.opacity(0.4),
                    ],
                    startPoint: UnitPoint(x: shimmerOffset - 0.3, y: 0.5),
                    endPoint: UnitPoint(x: shimmerOffset + 0.3, y: 0.5)
                )
            )
            .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false), value: shimmerOffset)
            .padding(.leading, 12)
            .onAppear {
                shimmerOffset = 2
            }
    }
}
