import SwiftUI

/// Circular icon button. The visual circle can be small (32–44pt) but the hit
/// area is always at least 44×44pt, and neighbours never overlap because the
/// hit frame is part of layout.
struct BuddyIconButton: View {
    enum Tone {
        /// Icon only, no fill.
        case plain
        /// surfaceSoft fill (composer "+", secondary header actions).
        case soft
        /// surface fill with a hairline border (floating header actions).
        case surface
        /// action fill (send, voice).
        case action
    }

    let systemImage: String
    let accessibilityLabel: Text
    var tone: Tone = .plain
    var diameter: CGFloat = 36
    var iconSize: CGFloat = 18
    var isEnabled = true
    let action: () -> Void

    init(
        systemImage: String,
        accessibilityLabel: LocalizedStringKey,
        tone: Tone = .plain,
        diameter: CGFloat = 36,
        iconSize: CGFloat = 18,
        isEnabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.systemImage = systemImage
        self.accessibilityLabel = Text(accessibilityLabel)
        self.tone = tone
        self.diameter = diameter
        self.iconSize = iconSize
        self.isEnabled = isEnabled
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: iconSize, weight: .medium))
                .foregroundStyle(foreground)
                .frame(width: diameter, height: diameter)
                .background(fill, in: Circle())
                .overlay {
                    if tone == .surface {
                        Circle().strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
                    }
                }
                .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(accessibilityLabel)
    }

    private var foreground: Color {
        guard isEnabled else { return AgentBuddyTheme.onDisabled }
        switch tone {
        case .action: return AgentBuddyTheme.onAction
        case .plain, .soft, .surface: return AgentBuddyTheme.textPrimary
        }
    }

    private var fill: Color {
        switch tone {
        case .plain: return .clear
        case .soft: return isEnabled ? AgentBuddyTheme.surfaceSoft : AgentBuddyTheme.disabled
        case .surface: return AgentBuddyTheme.surface
        case .action: return isEnabled ? AgentBuddyTheme.action : AgentBuddyTheme.disabled
        }
    }
}
