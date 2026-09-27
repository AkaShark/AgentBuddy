import SwiftUI

struct PulsingDot: View {
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(AgentBuddyTheme.accent)
            .frame(width: 8, height: 8)
            .scaleEffect(pulse ? 1.3 : 1.0)
            .opacity(pulse ? 0.6 : 1.0)
            .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: pulse)
            .onAppear { pulse = true }
    }
}
