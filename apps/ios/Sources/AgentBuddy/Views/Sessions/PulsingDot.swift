import SwiftUI

/// Small "live" dot. The pulse is decoration, so Reduce Motion keeps it still.
struct PulsingDot: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(AgentBuddyTheme.success)
            .frame(width: 8, height: 8)
            .scaleEffect(pulse && !reduceMotion ? 1.3 : 1.0)
            .opacity(pulse && !reduceMotion ? 0.6 : 1.0)
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: pulse)
            .onAppear { pulse = true }
            .accessibilityHidden(true)
    }
}
