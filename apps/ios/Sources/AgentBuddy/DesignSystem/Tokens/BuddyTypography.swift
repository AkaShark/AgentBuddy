import SwiftUI

// MARK: - Text styles

/// Type scale from the Mint design system (`spec/UI-GUIDELINES.md` §3).
/// Sizes are points at the default Dynamic Type size; they grow with the
/// system text size and with the app-wide `textScale` setting.
enum BuddyTextStyle {
    /// 32 / 42, semibold — short home title, at most two lines.
    case display
    /// 24 / 34, semibold — page and sheet titles.
    case title
    /// 17 / 26, semibold — section and card titles.
    case heading
    /// 16 / 26, regular — conversation and explanatory text.
    case body
    /// 14 / 20, medium — buttons, pickers, secondary actions.
    case label
    /// 12 / 18, regular — timestamps and secondary status.
    case caption
    /// 14 / 22, regular, monospaced — commands, paths, code.
    case code

    var size: CGFloat {
        switch self {
        case .display: return 32
        case .title: return 24
        case .heading: return 17
        case .body: return 16
        case .label: return 14
        case .caption: return 12
        case .code: return 14
        }
    }

    var lineHeight: CGFloat {
        switch self {
        case .display: return 42
        case .title: return 34
        case .heading: return 26
        case .body: return 26
        case .label: return 20
        case .caption: return 18
        case .code: return 22
        }
    }

    var weight: Font.Weight {
        switch self {
        case .display, .title, .heading: return .semibold
        case .label: return .medium
        case .body, .caption, .code: return .regular
        }
    }

    /// The system text style each size scales with.
    var relativeTo: Font.TextStyle {
        switch self {
        case .display: return .largeTitle
        case .title: return .title2
        case .heading: return .headline
        case .body: return .body
        case .label: return .subheadline
        case .caption: return .caption
        case .code: return .callout
        }
    }

    var isMonospaced: Bool { self == .code }
}

// MARK: - Modifier

private struct BuddyTextModifier: ViewModifier {
    let style: BuddyTextStyle
    let weight: Font.Weight?
    @Environment(\.textScale) private var textScale
    @ScaledMetric private var scaledSize: CGFloat

    init(style: BuddyTextStyle, weight: Font.Weight?) {
        self.style = style
        self.weight = weight
        _scaledSize = ScaledMetric(wrappedValue: style.size, relativeTo: style.relativeTo)
    }

    func body(content: Content) -> some View {
        let size = scaledSize * textScale
        let factor = size / style.size
        // SwiftUI adds `lineSpacing` on top of the font's natural line height
        // (about 1.2× the point size for SF / PingFang).
        let spacing = max(0, style.lineHeight * factor - size * 1.2)
        content
            .font(BuddyTypography.font(style, size: size, weight: weight ?? style.weight))
            .lineSpacing(spacing)
    }
}

enum BuddyTypography {
    /// Interface text follows the user's font-family preference; code is always
    /// monospaced (Berkeley Mono when bundled, otherwise the system monospace).
    static func font(_ style: BuddyTextStyle, size: CGFloat, weight: Font.Weight) -> Font {
        if style.isMonospaced || AgentBuddyFont.storedFamily.isMono {
            return AgentBuddyFont.monospaced(size: size, weight: weight)
        }
        return .system(size: size, weight: weight)
    }
}

extension View {
    /// Applies a Mint text style: font, weight, and line height, scaled with
    /// Dynamic Type and the app-wide text size.
    func buddyText(_ style: BuddyTextStyle, weight: Font.Weight? = nil) -> some View {
        modifier(BuddyTextModifier(style: style, weight: weight))
    }
}
