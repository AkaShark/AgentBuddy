import SwiftUI

// MARK: - Semantic colour roles

/// Colour roles from the Mint design system (`spec/UI-GUIDELINES.md` §2).
///
/// Views should pick a role by meaning (brand surface, primary action, warning
/// surface…) rather than by hue. Each role resolves from the active theme, so a
/// user-selected non-Mint theme keeps working through the fallbacks computed in
/// `ResolvedTheme`.
extension AgentBuddyTheme {
    private static func role(_ keyPath: KeyPath<ResolvedTheme, String>) -> Color {
        let store = ThemeStore.shared
        let theme = store.colorScheme == .dark ? store.dark : store.light
        return Color(hex: theme[keyPath: keyPath])
    }

    /// Page background (`bg`).
    static var background: Color { role(\.background) }
    /// Secondary grouping surface and user message fill (`surfaceSoft`).
    static var surfaceSoft: Color { role(\.surfaceLight) }

    /// Emphasis surface for the current task and the main call to action (`brand`).
    static var brand: Color { role(\.brand) }
    /// Text and icons placed on `brand`.
    static var onBrand: Color { role(\.onBrand) }

    /// Fill of the primary button (`action`).
    static var action: Color { role(\.accentStrong) }
    /// Content on `action`.
    static var onAction: Color { role(\.textOnAccent) }
    /// Text actions and links (`link`).
    static var link: Color { role(\.accent) }

    /// Outline of required controls and unselected inputs (`borderControl`).
    /// `border` / `separator` stay decorative.
    static var borderControl: Color { role(\.borderControl) }
    /// Keyboard and assistive focus ring.
    static var focus: Color { role(\.focus) }

    static var successSurface: Color { role(\.successSurface) }
    static var warningSurface: Color { role(\.warningSurface) }
    static var dangerSurface: Color { role(\.dangerSurface) }

    /// Fill of controls that cannot be used right now.
    static var disabled: Color { role(\.disabled) }
    /// Content on `disabled`.
    static var onDisabled: Color { role(\.onDisabled) }

    /// Shadow for floating layers only: y 8, blur 24, black 10% in light mode.
    /// Dark mode relies on outlines instead of deeper shadows.
    static var floatingShadow: Color {
        ThemeStore.shared.colorScheme == .dark ? .clear : Color.black.opacity(0.10)
    }
}
