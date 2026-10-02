import SwiftUI

// MARK: - Spacing

/// Spacing scale from the Mint design system: 4 8 12 16 20 24 32 48 64.
enum BuddySpacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 20
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let xxxl: CGFloat = 48
    static let huge: CGFloat = 64

    /// Phone page gutter: 24, dropping to 16 on 320–359pt wide screens.
    static func pageGutter(forWidth width: CGFloat) -> CGFloat {
        width > 0 && width < 360 ? md : xl
    }
}

// MARK: - Corner radius

/// Component radii. Each component has its own rule; there is deliberately no
/// global "corner radius" knob (handoff P0-1).
enum BuddyRadius {
    /// Small controls: segmented items, small tiles.
    static let control: CGFloat = 12
    /// Standard buttons (production value; the old 14 from page drafts is retired).
    static let button: CGFloat = 16
    /// Collapsible detail summary card.
    static let detailCard: CGFloat = 16
    /// Result card shown after an action completes.
    static let resultCard: CGFloat = 18
    /// Inline confirmation / approval card.
    static let confirmCard: CGFloat = 20
    /// Task and host cards. Fixed: do not scale with card height.
    static let card: CGFloat = 22
    /// Composer container. Stays fixed while the editor grows.
    static let composer: CGFloat = 24
    /// Top corners of bottom sheets (bottom corners are square).
    static let sheet: CGFloat = 30
    /// Icon tiles inside rows (status tile, project initial).
    static let tile: CGFloat = 16
    /// Brand tiles keep the same silhouette from header to splash.
    static let brandTileRatio: CGFloat = 32.0 / 112.0
}

/// User message bubble corners: top-leading / top-trailing / bottom-trailing /
/// bottom-leading = 19 / 19 / 5 / 19. The small corner points at the sender.
enum BuddyBubbleRadius {
    static let large: CGFloat = 19
    static let tail: CGFloat = 5
}

// MARK: - Sizes

enum BuddySize {
    static let brandMark: CGFloat = 32
    static let splashMark: CGFloat = 112
    /// Fits the longest bundled provider name in the splash's code type style.
    static let splashAgentName: CGFloat = 92
    /// Default control height (buttons, rows, inputs).
    static let control: CGFloat = 48
    /// Minimum iOS hit target; icons stay 20–24 and the hit area grows around them.
    static let minHitTarget: CGFloat = 44
    static let icon: CGFloat = 20
    static let iconLarge: CGFloat = 24
    /// Visual height of compact pills (model chip, status chip). The layout slot
    /// around them still guarantees `minHitTarget`.
    static let compactPill: CGFloat = 34
    /// Leading tile in list rows.
    static let rowTile: CGFloat = 44
    /// Minimum composer editor height.
    static let composerMinHeight: CGFloat = 48
}

// MARK: - Motion

/// Durations from the Mint design system. Use `BuddyMotion.animation(_:reduceMotion:)`
/// so "Reduce Motion" removes movement.
enum BuddyMotion {
    enum Kind {
        case press, state, page, sheet

        var duration: Double {
            switch self {
            case .press: return 0.12
            case .state: return 0.16
            case .page: return 0.24
            case .sheet: return 0.32
            }
        }
    }

    static func animation(_ kind: Kind, reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : .easeOut(duration: kind.duration)
    }
}
