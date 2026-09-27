import SwiftUI

/// Collaboration-mode chip (Default / Plan). Plan mode is marked by an icon and
/// the action fill, so the state never depends on colour alone.
struct ConversationComposerModeChip: View {
    let mode: AppModeKind
    let onTap: () -> Void

    private var label: LocalizedStringKey {
        switch mode {
        case .plan:
            return "Plan"
        case .`default`:
            return "Default"
        }
    }

    private var isPlan: Bool { mode == .plan }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: BuddySpacing.xxs) {
                if isPlan {
                    Image(systemName: "list.bullet.clipboard")
                        .font(.system(size: 13, weight: .semibold))
                        .accessibilityHidden(true)
                }
                Text(label)
                    .buddyText(.label)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .accessibilityHidden(true)
            }
            .foregroundStyle(isPlan ? AgentBuddyTheme.onAction : AgentBuddyTheme.textPrimary)
            .padding(.horizontal, BuddySpacing.sm)
            .frame(minHeight: BuddySize.compactPill)
            .background(isPlan ? AgentBuddyTheme.action : AgentBuddyTheme.surfaceSoft, in: Capsule())
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Collaboration mode"))
        .accessibilityValue(Text(label))
    }
}
