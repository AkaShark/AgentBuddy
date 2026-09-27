import SwiftUI

struct LaunchView: View {
    var body: some View {
        ZStack {
            AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
            VStack(spacing: 24) {
                BrandLogo(size: 132)
                Text("AI coding agent on iOS")
                    .agentBuddyFont(.body)
                    .foregroundColor(AgentBuddyTheme.textMuted)
            }
        }
    }
}
