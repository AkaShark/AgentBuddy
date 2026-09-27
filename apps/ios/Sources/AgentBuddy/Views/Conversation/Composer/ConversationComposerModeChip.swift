import SwiftUI

struct ConversationComposerModeChip: View {
    let mode: AppModeKind
    let onTap: () -> Void

    private var label: String {
        switch mode {
        case .plan:
            return "Plan"
        case .`default`:
            return "Default"
        }
    }

    private var foreground: Color {
        mode == .plan ? Color.black : AgentBuddyTheme.textPrimary
    }

    private var background: Color {
        mode == .plan ? AgentBuddyTheme.accent : AgentBuddyTheme.surfaceLight
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                Text(label)
                    .agentBuddyFont(.caption, weight: .semibold)
                Image(systemName: "chevron.up.chevron.down")
                    .agentBuddyFont(size: 10, weight: .semibold)
            }
            .foregroundStyle(foreground)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Capsule().fill(background))
        }
        .buttonStyle(.plain)
    }
}
