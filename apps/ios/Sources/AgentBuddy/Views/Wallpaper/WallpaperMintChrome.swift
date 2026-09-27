import SwiftUI

// MARK: - Shared Mint chrome for the wallpaper picker and adjust screens

/// Floating text button over the wallpaper preview (Close / Cancel): surface
/// capsule with a hairline border and the floating shadow, 44pt hit area.
struct WallpaperFloatingPillButton: View {
    let title: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .buddyText(.label, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.link)
                .padding(.horizontal, BuddySpacing.md)
                .frame(minHeight: BuddySize.compactPill + 6)
                .background(AgentBuddyTheme.surface, in: Capsule())
                .overlay { Capsule().strokeBorder(AgentBuddyTheme.border, lineWidth: 1) }
                .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Sample user message over the wallpaper: Mint user bubble (19/19/5/19).
struct WallpaperSampleUserBubble: View {
    let text: LocalizedStringKey

    var body: some View {
        Text(text)
            .buddyText(.body)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .padding(.horizontal, BuddySpacing.md)
            .padding(.vertical, BuddySpacing.sm)
            .background(AgentBuddyTheme.surfaceSoft, in: BuddyUserBubbleShape())
    }
}

extension View {
    /// Sample assistant reply over the wallpaper: a surface card so the text
    /// stays readable on any image.
    func wallpaperSampleAssistantCard() -> some View {
        padding(.horizontal, BuddySpacing.md)
            .padding(.vertical, BuddySpacing.sm)
            .buddyCard(.surface, radius: BuddyRadius.resultCard, padding: 0)
    }

    /// Bottom control panel: page background with 30pt top corners and the
    /// floating shadow, like a Mint bottom sheet.
    func wallpaperBottomPanel() -> some View {
        background(
            UnevenRoundedRectangle(
                topLeadingRadius: BuddyRadius.sheet,
                topTrailingRadius: BuddyRadius.sheet,
                style: .continuous
            )
            .fill(AgentBuddyTheme.background)
            .shadow(color: AgentBuddyTheme.floatingShadow, radius: 24, y: -4)
            .ignoresSafeArea(edges: .bottom)
        )
    }
}

/// Drag handle at the top of the bottom panel.
struct WallpaperPanelGrabber: View {
    var body: some View {
        Capsule()
            .fill(AgentBuddyTheme.borderControl)
            .frame(width: 36, height: 5)
            .accessibilityHidden(true)
    }
}
